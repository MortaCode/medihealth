package com.myy.medihealth.payment.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.mapper.CartItemMapper;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.order.entity.Order;
import com.myy.medihealth.order.entity.OrderItem;
import com.myy.medihealth.order.mapper.OrderItemMapper;
import com.myy.medihealth.order.mapper.OrderMapper;
import com.myy.medihealth.payment.vo.OrderSubmitVo;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(String userId, OrderSubmitVo vo) {
        LambdaQueryWrapper<CartItem> cartWrapper = new LambdaQueryWrapper<>();
        cartWrapper.eq(CartItem::getUserId, userId);
        cartWrapper.eq(CartItem::getSelected, 1);
        List<CartItem> selectedItems = cartItemMapper.selectList(cartWrapper);

        if (selectedItems.isEmpty()) {
            throw new BizException("请选择要购买的商品");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        int prescriptionFlag = 0;

        for (CartItem cartItem : selectedItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
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
        }

        String orderId = IdUtil.fastSimpleUUID();
        Order order = new Order();
        order.setId(IdUtil.fastSimpleUUID());
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setAmount(totalAmount);
        order.setStatus(0);
        order.setVersion(0);
        order.setPrescriptionFlag(prescriptionFlag);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setDelFlag(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);

        for (CartItem cartItem : selectedItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            BigDecimal itemAmount = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));

            OrderItem orderItem = new OrderItem();
            orderItem.setId(IdUtil.fastSimpleUUID());
            orderItem.setOrderId(orderId);
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(product.getImage());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setAmount(itemAmount);
            orderItem.setPrescriptionRequired(product.getPrescriptionRequired());
            orderItem.setCreateTime(LocalDateTime.now());
            orderItemMapper.insert(orderItem);

            totalAmount = totalAmount.add(itemAmount);
        }

        order.setAmount(totalAmount);
        orderMapper.updateById(order);

        for (CartItem cartItem : selectedItems) {
            Product product = productMapper.selectById(cartItem.getProductId());
            int affected = productMapper.deductStock(product.getId(), cartItem.getQuantity());
            if (affected == 0) {
                throw new BizException("商品【" + product.getName() + "】扣减库存失败，可能库存不足");
            }
        }

        cartItemMapper.deleteSelectedByUserId(userId);

        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order paySuccess(String orderId, String channel, String channelOrderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderId, orderId);
        Order order = orderMapper.selectOne(wrapper);

        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BizException("订单状态不允许支付");
        }

        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        return order;
    }

    public Order getOrder(String orderId, String userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderId, orderId);
        wrapper.eq(Order::getUserId, userId);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return order;
    }

    public List<OrderItem> getOrderItems(String orderId) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        return orderItemMapper.selectList(wrapper);
    }
}
