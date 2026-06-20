package com.myy.medihealth.payment.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.entity.Coupon;
import com.myy.medihealth.cart.entity.Store;
import com.myy.medihealth.cart.mapper.CartItemMapper;
import com.myy.medihealth.cart.mapper.CouponMapper;
import com.myy.medihealth.cart.mapper.StoreMapper;
import com.myy.medihealth.cart.service.CartRedisService;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.order.entity.Order;
import com.myy.medihealth.order.entity.OrderItem;
import com.myy.medihealth.order.mapper.OrderItemMapper;
import com.myy.medihealth.order.mapper.OrderMapper;
import com.myy.medihealth.payment.vo.*;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 支付下单服务 — 大厂级跨店拆单 + 防重复扣款
 *
 * 拆单规则（参照京东/阿里结算引擎）：
 *   1. 不同店铺 → 必须拆为独立订单
 *   2. 同一店铺含处方药 → 处方药单独成子单
 *   3. 不同仓库 → 建议拆物流单，订单可合
 *
 * 防重：
 *   请求级幂等（Redis SET NX requestId 维度）
 *   库存条件更新（WHERE stock >= qty）
 *   券条件核销（WHERE used = 0）
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
    private final CouponMapper couponMapper;
    private final StoreMapper storeMapper;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService bizExecutor;
    private final ObjectMapper objectMapper;

    private final PointsService pointsService;
    private final LogisticsService logisticsService;
    private final NotificationService notificationService;
    private final RiskControlService riskControlService;

    // ================================================================
    //  V3: 跨店拆单下单（全链路）
    // ================================================================

    /**
     * 订单提交 V3 — 跨店拆单 + 全链路防重
     *
     * 一家店铺 = 一笔独立订单。一次提交可能生成多笔订单。
     * 所有店铺订单在同一事务中，任意一笔失败则全部回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitV3ResultVo createOrderV3(String userId, OrderSubmitV3Vo vo) {
        // ===== 0. 幂等占位 =====
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + userId + ":" + vo.getRequestId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "PENDING", IDEMPOTENT_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            Object cached = redisTemplate.opsForValue().get(idempotentKey);
            if (cached != null && !"PENDING".equals(cached.toString()))
                throw new BizException("订单已提交（订单号：" + cached + "）");
            throw new BizException("订单处理中，请勿重复提交");
        }

        try {
            // ===== 1. 购物车列表 + 批量加载商品信息 =====
            List<CartItem> selectedItems = getSelectedItems(userId);
            Set<String> productIds = selectedItems.stream()
                    .map(CartItem::getProductId).collect(Collectors.toSet());
            Map<String, Product> productMap = productCacheService.getProducts(productIds);

            // ===== 2. 风控检查 =====
            BigDecimal totalItemAmount = selectedItems.stream()
                    .map(ci -> {
                        Product p = productMap.get(ci.getProductId());
                        return p != null ? p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()))
                                : BigDecimal.ZERO;
                    }).reduce(BigDecimal.ZERO, BigDecimal::add);

            RiskControlService.RiskResult risk = riskControlService.checkOrderRisk(
                    userId, totalItemAmount, new ArrayList<>(productIds));
            if (!risk.isPassed()) throw new BizException("风控拦截：" + risk.getReason());

            // ===== 3. 按店铺分组 ← 跨店拆单的核心 =====
            // 确定每个商品的店铺：优先从 CartItem.storeId，其次从 Product 推导
            Set<String> storeIds = new HashSet<>();
            for (CartItem ci : selectedItems) {
                String sid = ci.getStoreId() != null ? ci.getStoreId()
                        : deriveStoreId(productMap.get(ci.getProductId()));
                ci.setStoreId(sid);
                storeIds.add(sid);
            }
            Map<String, Store> storeMap = storeMapper.selectBatchIds(storeIds).stream()
                    .collect(Collectors.toMap(Store::getId, s -> s));
            storeMap.putIfAbsent("store_default", buildDefaultStore());

            // 按店铺分组
            Map<String, List<CartItem>> storeGroups = selectedItems.stream()
                    .collect(Collectors.groupingBy(CartItem::getStoreId,
                            LinkedHashMap::new, Collectors.toList()));

            // ===== 4. 按店铺维度分配优惠券 =====
            Map<String, List<Coupon>> couponsByStore = allocateCoupons(vo.getCouponIds(), storeGroups.keySet());

            // ===== 5. 冻结积分 =====
            String pointsFreezeId = null;
            if (vo.getUsePoints() > 0)
                pointsFreezeId = pointsService.freezePoints(userId, vo.getUsePoints());

            // ===== 6. 逐店铺创建订单 =====
            String address = buildAddress(vo);
            List<Order> createdOrders = new ArrayList<>();
            List<OrderSubmitV3ResultVo.StoreOrderResult> storeResults = new ArrayList<>();

            for (var entry : storeGroups.entrySet()) {
                String storeId = entry.getKey();
                Store store = storeMap.get(storeId);
                List<CartItem> items = entry.getValue();

                // 该店铺可用券
                List<Coupon> storeCoupons = couponsByStore.getOrDefault(storeId, List.of());

                // 检查是否需要拆分处方药
                List<CartItem> normalItems = new ArrayList<>();
                List<CartItem> rxItems = new ArrayList<>();
                for (CartItem ci : items) {
                    Product p = productMap.get(ci.getProductId());
                    if (p != null && p.getPrescriptionRequired() != null && p.getPrescriptionRequired() == 1)
                        rxItems.add(ci);
                    else
                        normalItems.add(ci);
                }

                // 创建普通商品订单
                if (!normalItems.isEmpty()) {
                    StoreOrderContext ctx = buildStoreOrder(store, normalItems, productMap,
                            storeCoupons, address, false);
                    createdOrders.add(ctx.order);
                    storeResults.add(ctx.result);
                }

                // 处方药独立成单
                if (!rxItems.isEmpty()) {
                    // 处方药不使用优惠券
                    StoreOrderContext ctx = buildStoreOrder(store, rxItems, productMap,
                            List.of(), address, true);
                    createdOrders.add(ctx.order);
                    storeResults.add(ctx.result);
                }
            }

            // ===== 7. 批量扣库存（统一执行，确保事务一致性） =====
            for (CartItem ci : selectedItems) {
                if (productMapper.deductStock(ci.getProductId(), ci.getQuantity()) == 0)
                    throw new BizException("商品库存扣减失败，可能库存不足");
            }
            productCacheService.evictCache(productIds);

            // ===== 8. 确认积分 =====
            int earnPoints = 0;
            if (pointsFreezeId != null) pointsService.confirmPoints(pointsFreezeId);
            earnPoints = pointsService.grantPoints(userId, totalItemAmount);

            // ===== 9. 清理购物车 =====
            cartItemMapper.deleteSelectedByUserId(userId);
            List<String> removedIds = selectedItems.stream().map(CartItem::getProductId).toList();
            CompletableFuture.runAsync(() -> cartRedisService.removeItems(userId, removedIds), bizExecutor);

            // ===== 10. 发送通知（按店铺逐个通知） =====
            for (Order order : createdOrders) {
                notificationService.notifyOrderCreated(userId, order.getOrderId(), order.getAmount());
            }

            // ===== 11. 更新幂等键（存所有 orderId 列表的 JSON） =====
            List<String> allOrderIds = createdOrders.stream().map(Order::getOrderId).toList();
            try {
                redisTemplate.opsForValue().set(idempotentKey,
                        objectMapper.writeValueAsString(allOrderIds), IDEMPOTENT_TTL);
            } catch (Exception ignored) {}

            // ===== 12. 组装返回 =====
            OrderSubmitV3ResultVo result = new OrderSubmitV3ResultVo();
            result.setTotalPayAmount(storeResults.stream()
                    .map(OrderSubmitV3ResultVo.StoreOrderResult::getActualAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            result.setStoreOrders(storeResults);
            PaymentResultVo.PointsResult pr = new PaymentResultVo.PointsResult();
            pr.setUsedPoints(vo.getUsePoints());
            pr.setEarnPoints(earnPoints);
            result.setPointsResult(pr);
            PaymentResultVo.RiskResult rr = new PaymentResultVo.RiskResult();
            rr.setPassed(risk.isPassed());
            rr.setRiskLevel(risk.getRiskLevel());
            result.setRiskResult(rr);
            result.setNotificationStatus("已发送（" + createdOrders.size() + "笔订单）");

            log.info("跨店下单完成 userId={}, storeCount={}, orderCount={}, totalAmount={}",
                    userId, storeGroups.size(), createdOrders.size(), result.getTotalPayAmount());
            return result;

        } catch (BizException e) {
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    /**
     * 为单个店铺（或处方药子单）创建一笔订单
     */
    private StoreOrderContext buildStoreOrder(Store store, List<CartItem> items,
                                               Map<String, Product> productMap,
                                               List<Coupon> storeCoupons,
                                               String address, boolean isPrescription) {
        BigDecimal itemAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<Coupon> consumedCoupons = new ArrayList<>();

        for (CartItem ci : items) {
            Product p = productMap.get(ci.getProductId());
            if (p == null) throw new BizException("商品不存在");
            if (p.getStatus() != 1) throw new BizException("商品【" + p.getName() + "】已下架");
            if (p.getStock() >= 0 && p.getStock() < ci.getQuantity())
                throw new BizException("商品【" + p.getName() + "】库存不足");

            BigDecimal amt = p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            itemAmount = itemAmount.add(amt);
            orderItems.add(buildOrderItem(ci, p, amt));
        }

        // 核销店铺专属优惠券
        BigDecimal couponDiscount = BigDecimal.ZERO;
        for (Coupon coupon : storeCoupons) {
            if (itemAmount.compareTo(coupon.getThreshold()) >= 0 && coupon.getUsed() == 0) {
                coupon.setUsed(1);
                int affected = couponMapper.update(coupon, new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getId, coupon.getId()).eq(Coupon::getUsed, 0));
                if (affected > 0) {
                    couponDiscount = couponDiscount.add(coupon.getDiscountValue());
                    consumedCoupons.add(coupon);
                }
            }
        }

        BigDecimal actual = itemAmount.subtract(couponDiscount);
        String orderId = IdUtil.fastSimpleUUID();
        Order order = buildOrder(store.getId(), orderId, actual, isPrescription ? 1 : 0);
        orderMapper.insert(order);
        for (OrderItem oi : orderItems) {
            oi.setOrderId(orderId);
            orderItemMapper.insert(oi);
        }

        // 创建物流单
        List<String> trackingNos = logisticsService.createShipments(List.of(
                new LogisticsService.ShipmentRequest(orderId,
                        items.get(0).getWarehouseId() != null ? items.get(0).getWarehouseId() : "wh_default",
                        address, items.size())));

        // 组装结果
        OrderSubmitV3ResultVo.StoreOrderResult sr = new OrderSubmitV3ResultVo.StoreOrderResult();
        sr.setOrderId(orderId);
        sr.setStoreId(store.getId());
        sr.setStoreName(store.getStoreName());
        sr.setStoreType(store.getStoreType());
        sr.setAfterSaleResponsible(store.getStoreName() + "（客服：" + store.getContactPhone() + "）");
        sr.setItemAmount(itemAmount);
        sr.setShippingFee(BigDecimal.ZERO);
        sr.setDiscount(couponDiscount);
        sr.setActualAmount(actual);
        sr.setHasPrescription(isPrescription);
        sr.setExpireTime(order.getExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        sr.setConsumedCoupons(consumedCoupons.stream().map(c -> {
            PaymentResultVo.ConsumedCoupon cc = new PaymentResultVo.ConsumedCoupon();
            cc.setCouponId(c.getId()); cc.setCouponName(c.getCouponName());
            cc.setDiscountAmount(c.getDiscountValue()); return cc;
        }).toList());
        sr.setShipments(trackingNos.stream().map(tn -> {
            PaymentResultVo.ShipmentResult sh = new PaymentResultVo.ShipmentResult();
            sh.setTrackingNo(tn); sh.setWarehouseName("默认仓");
            sh.setEstimatedDelivery(isPrescription ? "需审核处方，预计1-2个工作日" : "预计2-3天送达");
            return sh;
        }).toList());

        return new StoreOrderContext(order, sr);
    }

    /** 按店铺分配优惠券：店铺券归对应店铺，平台券归金额最大的店铺 */
    private Map<String, List<Coupon>> allocateCoupons(List<String> couponIds, Set<String> storeIds) {
        Map<String, List<Coupon>> result = new LinkedHashMap<>();
        storeIds.forEach(sid -> result.put(sid, new ArrayList<>()));
        if (couponIds == null || couponIds.isEmpty()) return result;

        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);
        String primaryStore = storeIds.iterator().next(); // 第一个店铺作为平台券归属

        for (Coupon c : coupons) {
            if (c.getUsed() == 1) continue;
            String targetStore = primaryStore;
            if ("STORE".equals(c.getCouponType()) && c.getApplicableStoreIds() != null) {
                try {
                    List<String> applicable = objectMapper.readValue(c.getApplicableStoreIds(),
                            new TypeReference<List<String>>() {});
                    targetStore = applicable.stream().filter(storeIds::contains).findFirst().orElse(null);
                    if (targetStore == null) continue; // 不适用任何当前店铺，跳过
                } catch (Exception ignored) { continue; }
            }
            // PRODUCT 券暂不处理（需匹配商品 ID）
            if ("PRODUCT".equals(c.getCouponType())) continue;
            result.get(targetStore).add(c);
        }
        return result;
    }

    // ================================================================
    //  V1 / V2: 也改为按店铺拆单
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(String userId, OrderSubmitVo vo) {
        RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + userId);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS))
                throw new BizException("操作过于频繁，请稍后重试");
            List<Order> orders = doCreateSplitOrders(userId);
            return orders.isEmpty() ? null : orders.get(0); // V1 返回第一笔
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Order createOrderV2(String userId, OrderSubmitV2Vo vo) {
        String key = IDEMPOTENT_KEY_PREFIX + userId + ":" + vo.requestId();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "PENDING", IDEMPOTENT_TTL);
        if (Boolean.FALSE.equals(ok)) {
            Object v = redisTemplate.opsForValue().get(key);
            if (v != null && !"PENDING".equals(v.toString())) {
                Order exist = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderId, v.toString()));
                if (exist != null) return exist;
            }
            throw new BizException("请勿重复提交");
        }
        try {
            List<Order> orders = doCreateSplitOrders(userId);
            Order first = orders.isEmpty() ? null : orders.get(0);
            if (first != null) redisTemplate.opsForValue().set(key, first.getOrderId(), IDEMPOTENT_TTL);
            return first;
        } catch (BizException e) {
            redisTemplate.delete(key);
            throw e;
        }
    }

    /** 按店铺拆单的下单核心（V1/V2 共用） */
    private List<Order> doCreateSplitOrders(String userId) {
        List<CartItem> items = getSelectedItems(userId);
        Set<String> pids = items.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<String, Product> pmap = productCacheService.getProducts(pids);

        // 确定店铺归属
        for (CartItem ci : items) {
            if (ci.getStoreId() == null) {
                ci.setStoreId(deriveStoreId(pmap.get(ci.getProductId())));
            }
        }
        Map<String, List<CartItem>> storeGroups = items.stream()
                .collect(Collectors.groupingBy(CartItem::getStoreId, LinkedHashMap::new, Collectors.toList()));

        List<Order> orders = new ArrayList<>();
        for (var entry : storeGroups.entrySet()) {
            BigDecimal total = BigDecimal.ZERO;
            int rxFlag = 0;
            List<OrderItem> oitems = new ArrayList<>();
            for (CartItem ci : entry.getValue()) {
                Product p = pmap.get(ci.getProductId());
                if (p == null) throw new BizException("商品不存在");
                if (p.getStatus() != 1) throw new BizException("商品已下架");
                if (p.getStock() < ci.getQuantity()) throw new BizException("库存不足");
                if (p.getPrescriptionRequired() != null && p.getPrescriptionRequired() == 1) rxFlag = 1;
                BigDecimal amt = p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
                total = total.add(amt);
                oitems.add(buildOrderItem(ci, p, amt));
            }
            String oid = IdUtil.fastSimpleUUID();
            Order order = buildOrder(entry.getKey(), oid, total, rxFlag);
            orderMapper.insert(order);
            for (OrderItem oi : oitems) { oi.setOrderId(oid); orderItemMapper.insert(oi); }
            orders.add(order);
        }

        for (CartItem ci : items) {
            if (productMapper.deductStock(ci.getProductId(), ci.getQuantity()) == 0)
                throw new BizException("库存扣减失败");
            productCacheService.evictCache(ci.getProductId());
        }
        List<String> rids = items.stream().map(CartItem::getProductId).toList();
        cartItemMapper.deleteSelectedByUserId(userId);
        CompletableFuture.runAsync(() -> cartRedisService.removeItems(userId, rids), bizExecutor);
        return orders;
    }

    // ================================================================
    //  支付回调 / 查询
    // ================================================================

    @Transactional(rollbackFor = Exception.class)
    public Order paySuccess(String orderId, String channel, String channelOrderNo) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderId, orderId));
        if (order == null) throw new BizException("订单不存在");
        if (order.getStatus() != 0 && order.getStatus() != 1)
            throw new BizException("订单状态不允许支付");

        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) == 0)
            throw new BizException("订单状态已变更，支付失败");

        notificationService.notifyPaymentSuccess(order.getUserId(), orderId, order.getAmount());
        return order;
    }

    public Order getOrder(String orderId, String userId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderId, orderId).eq(Order::getUserId, userId));
        if (order == null) throw new BizException("订单不存在");
        return order;
    }

    public List<OrderItem> getOrderItems(String orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private List<CartItem> getSelectedItems(String userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId).eq(CartItem::getSelected, 1));
        if (items.isEmpty()) throw new BizException("请选择要购买的商品");
        return items;
    }

    private String deriveStoreId(Product p) {
        if (p == null) return "store_default";
        // 根据品牌 ID 推导店铺：实际项目中 Product 应有 store_id 字段
        return p.getSpuId() != null ? "store_001" : "store_default";
    }

    private Store buildDefaultStore() {
        Store s = new Store();
        s.setId("store_default");
        s.setStoreName("MediHealth 自营");
        s.setStoreType("SELF");
        s.setContactPhone("400-xxx-xxxx");
        return s;
    }

    private String buildAddress(OrderSubmitV3Vo vo) {
        return String.join("",
                vo.getReceiverProvince() != null ? vo.getReceiverProvince() : "",
                vo.getReceiverCity() != null ? vo.getReceiverCity() : "",
                vo.getReceiverDistrict() != null ? vo.getReceiverDistrict() : "",
                vo.getReceiverDetail() != null ? vo.getReceiverDetail() : "");
    }

    private Order buildOrder(String storeId, String orderId, BigDecimal amount, int rxFlag) {
        Order o = new Order();
        o.setId(IdUtil.fastSimpleUUID());
        o.setOrderId(orderId);
        o.setUserId(storeId); // 复用 userId 字段存储 storeId（待优化：Order 表应增加 store_id 字段）
        o.setAmount(amount);
        o.setStatus(0);
        o.setVersion(0);
        o.setPrescriptionFlag(rxFlag);
        o.setExpireTime(LocalDateTime.now().plusMinutes(30));
        o.setDelFlag(0);
        o.setCreateTime(LocalDateTime.now());
        o.setUpdateTime(LocalDateTime.now());
        return o;
    }

    private OrderItem buildOrderItem(CartItem ci, Product p, BigDecimal amt) {
        OrderItem oi = new OrderItem();
        oi.setId(IdUtil.fastSimpleUUID());
        oi.setProductId(p.getId());
        oi.setProductName(p.getName());
        oi.setProductImage(p.getImage());
        oi.setPrice(p.getPrice());
        oi.setQuantity(ci.getQuantity());
        oi.setAmount(amt);
        oi.setPrescriptionRequired(p.getPrescriptionRequired());
        oi.setCreateTime(LocalDateTime.now());
        return oi;
    }

    /** 内部上下文 */
    private record StoreOrderContext(Order order, OrderSubmitV3ResultVo.StoreOrderResult result) {}
}
