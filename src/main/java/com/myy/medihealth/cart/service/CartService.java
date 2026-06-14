package com.myy.medihealth.cart.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.mapper.CartItemMapper;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public List<CartItem> list(String userId) {
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId);
        wrapper.orderByDesc(CartItem::getCreateTime);
        return cartItemMapper.selectList(wrapper);
    }

    public CartItem add(String userId, String productId, int quantity) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        if (product.getStatus() != 1) {
            throw new BizException("商品已下架");
        }
        if (product.getStock() < quantity) {
            throw new BizException("商品库存不足");
        }

        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId);
        wrapper.eq(CartItem::getProductId, productId);
        CartItem existingItem = cartItemMapper.selectOne(wrapper);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + quantity;
            if (product.getStock() < newQuantity) {
                throw new BizException("商品库存不足");
            }
            existingItem.setQuantity(newQuantity);
            cartItemMapper.updateById(existingItem);
            return existingItem;
        }

        CartItem cartItem = new CartItem();
        cartItem.setId(IdUtil.fastSimpleUUID());
        cartItem.setUserId(userId);
        cartItem.setProductId(productId);
        cartItem.setQuantity(quantity);
        cartItem.setSelected(1);
        cartItem.setCreateTime(LocalDateTime.now());
        cartItemMapper.insert(cartItem);
        return cartItem;
    }

    public CartItem updateQuantity(String userId, String id, int quantity) {
        CartItem cartItem = cartItemMapper.selectById(id);
        if (cartItem == null || !cartItem.getUserId().equals(userId)) {
            throw new BizException("购物车项不存在");
        }
        Product product = productMapper.selectById(cartItem.getProductId());
        if (product == null || product.getStatus() != 1) {
            throw new BizException("商品已下架");
        }
        if (product.getStock() < quantity) {
            throw new BizException("商品库存不足");
        }
        cartItem.setQuantity(quantity);
        cartItemMapper.updateById(cartItem);
        return cartItem;
    }

    public CartItem toggleSelect(String userId, String id) {
        CartItem cartItem = cartItemMapper.selectById(id);
        if (cartItem == null || !cartItem.getUserId().equals(userId)) {
            throw new BizException("购物车项不存在");
        }
        cartItem.setSelected(cartItem.getSelected() == 1 ? 0 : 1);
        cartItemMapper.updateById(cartItem);
        return cartItem;
    }

    public void remove(String userId, String id) {
        CartItem cartItem = cartItemMapper.selectById(id);
        if (cartItem == null || !cartItem.getUserId().equals(userId)) {
            throw new BizException("购物车项不存在");
        }
        cartItemMapper.deleteById(id);
    }
}
