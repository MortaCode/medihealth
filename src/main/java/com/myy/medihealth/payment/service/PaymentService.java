package com.myy.medihealth.payment.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.mapper.CartItemMapper;
import com.myy.medihealth.cart.service.CartRedisService;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.order.entity.Order;
import com.myy.medihealth.order.entity.OrderItem;
import com.myy.medihealth.order.mapper.OrderItemMapper;
import com.myy.medihealth.order.mapper.OrderMapper;
import com.myy.medihealth.payment.vo.OrderSubmitV2Vo;
import com.myy.medihealth.payment.vo.OrderSubmitVo;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.mapper.ProductMapper;
import com.myy.medihealth.product.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 支付下单服务
 * 优化点：
 * 1. 批量加载商品（避免 N+1 查询）
 * 2. 分布式锁防止重复下单
 * 3. 清理 Redis 购物车缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String ORDER_LOCK_PREFIX = "lock:order:";
    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:order:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(5);

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductCacheService productCacheService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartRedisService cartRedisService;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService bizExecutor;

    /**
     * 创建订单 —— 批量校验 + 库存扣减 + 清购物车
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(String userId, OrderSubmitVo vo) {
        RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + userId);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new BizException("操作过于频繁，请稍后重试");
            }

            // 1. 查询已选中的购物车项DB
            List<CartItem> selectedItems = cartItemMapper.selectList(
                    new LambdaQueryWrapper<CartItem>()
                            .eq(CartItem::getUserId, userId)
                            .eq(CartItem::getSelected, 1)
            );
            if (selectedItems.isEmpty()) {
                throw new BizException("请选择要购买的商品");
            }

            // 2. 批量获取商品信息（一次查询代替 N 次，优先走缓存）
            Set<String> productIds = selectedItems.stream()
                    .map(CartItem::getProductId)
                    .collect(Collectors.toSet());
            Map<String, Product> productMap = productCacheService.getProducts(productIds);

            // 3. 遍历校验并构建订单项
            BigDecimal totalAmount = BigDecimal.ZERO;
            int prescriptionFlag = 0;
            List<OrderItem> orderItems = new ArrayList<>();

            for (CartItem cartItem : selectedItems) {
                Product product = productMap.get(cartItem.getProductId());
                if (product == null) {
                    throw new BizException("商品不存在");
                }
                if (product.getStatus() != 1) {
                    throw new BizException("商品【" + product.getName() + "】已下架，请重新选择");
                }
                if (product.getStock() < cartItem.getQuantity()) {
                    throw new BizException("商品【" + product.getName() + "】库存不足");
                }
                if (product.getPrescriptionRequired() != null && product.getPrescriptionRequired() == 1) {
                    prescriptionFlag = 1;
                }

                BigDecimal itemAmount = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
                totalAmount = totalAmount.add(itemAmount);

                OrderItem orderItem = buildOrderItem(cartItem, product, itemAmount);
                orderItems.add(orderItem);
            }

            // 4. 创建订单
            String orderId = IdUtil.fastSimpleUUID();
            Order order = buildOrder(userId, orderId, totalAmount, prescriptionFlag);
            orderMapper.insert(order);

            // 5. 批量插入订单项
            for (OrderItem item : orderItems) {
                item.setOrderId(orderId);
                orderItemMapper.insert(item);
            }

            // 6. 批量扣减库存
            for (CartItem cartItem : selectedItems) {
                int affected = productMapper.deductStock(cartItem.getProductId(), cartItem.getQuantity());
                if (affected == 0) {
                    throw new BizException("商品库存扣减失败，可能库存不足");
                }
                // 库存变更后清除产品缓存
                productCacheService.evictCache(cartItem.getProductId());
            }

            // 7. 清理购物车（DB + Redis）
            List<String> removedProductIds = selectedItems.stream()
                    .map(CartItem::getProductId)
                    .toList();
            cartItemMapper.deleteSelectedByUserId(userId);
            cartRedisService.removeItems(userId, removedProductIds);

            log.info("订单创建成功 orderId={}, userId={}, amount={}, items={}",
                    orderId, userId, totalAmount, orderItems.size());
            return order;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("系统繁忙，请稍后重试");
        } catch (BizException e) {
            throw e;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 创建订单 V2 —— 幂等防重 + 批量DB操作 + 异步清购物车
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrderV2(String userId, OrderSubmitV2Vo vo) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + userId + ":" + vo.requestId();

        // 1. 幂等性检查：SET NX 原子占位
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "PENDING", IDEMPOTENT_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            // 已存在 → 检查是否已有结果
            Object cached = redisTemplate.opsForValue().get(idempotentKey);
            if (cached != null && !"PENDING".equals(cached.toString())) {
                Order existOrder = orderMapper.selectOne(
                        new LambdaQueryWrapper<Order>().eq(Order::getOrderId, cached.toString())
                );
                if (existOrder != null) {
                    return existOrder;
                }
            }
            throw new BizException("订单处理中，请勿重复提交");
        }

        try {
            // 2. 查询已选中的购物车项
            List<CartItem> selectedItems = cartItemMapper.selectList(
                    new LambdaQueryWrapper<CartItem>()
                            .eq(CartItem::getUserId, userId)
                            .eq(CartItem::getSelected, 1)  //选中
            );
            if (selectedItems.isEmpty()) {
                throw new BizException("请选择要购买的商品");
            }

            // 3. 批量获取商品信息
            Set<String> productIds = selectedItems.stream()
                    .map(CartItem::getProductId)
                    .collect(Collectors.toSet());
            Map<String, Product> productMap = productCacheService.getProducts(productIds);

            // 4. 遍历校验并构建订单项
            BigDecimal totalAmount = BigDecimal.ZERO;
            int prescriptionFlag = 0;
            List<OrderItem> orderItems = new ArrayList<>();

            for (CartItem cartItem : selectedItems) {
                Product product = productMap.get(cartItem.getProductId());
                if (product == null) {
                    throw new BizException("商品不存在");
                }
                if (product.getStatus() != 1) {
                    throw new BizException("商品【" + product.getName() + "】已下架，请重新选择");
                }
                if (product.getStock() < cartItem.getQuantity()) {
                    throw new BizException("商品【" + product.getName() + "】库存不足");
                }
                if (product.getPrescriptionRequired() != null && product.getPrescriptionRequired() == 1) {
                    prescriptionFlag = 1;
                }

                BigDecimal itemAmount = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
                totalAmount = totalAmount.add(itemAmount);

                OrderItem orderItem = buildOrderItem(cartItem, product, itemAmount);
                orderItems.add(orderItem);
            }

            // 5. 创建订单
            String orderId = IdUtil.fastSimpleUUID();
            Order order = buildOrder(userId, orderId, totalAmount, prescriptionFlag);
            orderMapper.insert(order);

            // 6. 批量插入订单项（一次 SQL 替代 N 次）
            for (OrderItem item : orderItems) {
                item.setOrderId(orderId);
            }
            orderItemMapper.batchInsert(orderItems);

            // 7. 扣减库存
            for (CartItem cartItem : selectedItems) {
                int affected = productMapper.deductStock(cartItem.getProductId(), cartItem.getQuantity());
                if (affected == 0) {
                    throw new BizException("商品库存扣减失败，可能库存不足");
                }
            }

            // 8. 批量清除商品缓存（一次 Redis 调用替代 N 次）
            productCacheService.evictCache(productIds);

            // 9. 清理购物车 DB
            cartItemMapper.deleteSelectedByUserId(userId);

            // 10. 异步清理购物车 Redis 缓存（非关键路径，不阻塞响应）
            List<String> removedProductIds = selectedItems.stream()
                    .map(CartItem::getProductId)
                    .toList();
            CompletableFuture.runAsync(
                    () -> cartRedisService.removeItems(userId, removedProductIds),
                    bizExecutor
            );

            log.info("订单创建成功(V2) orderId={}, userId={}, requestId={}, amount={}, items={}",
                    orderId, userId, vo.requestId(), totalAmount, orderItems.size());

            // 11. 将幂等键更新为 orderId（后续重复请求可直接返回订单）
            redisTemplate.opsForValue().set(idempotentKey, orderId, IDEMPOTENT_TTL);

            return order;

        } catch (BizException e) {
            // 业务异常 → 删除幂等键，允许客户端换 requestId 重试
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    /**
     * 支付回调 —— 乐观锁版本号防止并发
     */
    @Transactional(rollbackFor = Exception.class)
    public Order paySuccess(String orderId, String channel, String channelOrderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderId, orderId)
        );
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BizException("订单状态不允许支付");
        }

        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        int updated = orderMapper.updateById(order);
        if (updated == 0) {
            throw new BizException("订单状态已变更，支付失败");
        }
        return order;
    }

    public Order getOrder(String orderId, String userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, orderId)
                        .eq(Order::getUserId, userId)
        );
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return order;
    }

    public List<OrderItem> getOrderItems(String orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
    }

    // ---------- helpers ----------

    private Order buildOrder(String userId, String orderId, BigDecimal amount, int prescriptionFlag) {
        Order order = new Order();
        order.setId(IdUtil.fastSimpleUUID());
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus(0);
        order.setVersion(0);
        order.setPrescriptionFlag(prescriptionFlag);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setDelFlag(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }

    private OrderItem buildOrderItem(CartItem cartItem, Product product, BigDecimal amount) {
        OrderItem item = new OrderItem();
        item.setId(IdUtil.fastSimpleUUID());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImage(product.getImage());
        item.setPrice(product.getPrice());
        item.setQuantity(cartItem.getQuantity());
        item.setAmount(amount);
        item.setPrescriptionRequired(product.getPrescriptionRequired());
        item.setCreateTime(LocalDateTime.now());
        return item;
    }
}
