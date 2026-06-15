package com.myy.medihealth.cart.service;

import cn.hutool.core.util.IdUtil;
import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.mapper.CartItemMapper;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 购物车服务 —— Redis Hash 主导，异步双写 MySQL
 * 核心链路始终走 Redis，保证高性能；MySQL 通过异步消息同步保证最终一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final CartRedisService cartRedisService;
    private final ProductCacheService productCacheService;

    /**
     * 获取购物车列表 —— Redis 优先，miss 回源 DB
     */
    public List<CartItem> list(String userId) {
        Map<String, CartItem> cart = cartRedisService.getCart(userId);
        if (!cart.isEmpty()) {
            return new ArrayList<>(cart.values());
        }
        // Redis miss → 回源 DB 并回填
        Map<String, CartItem> dbCart = cartRedisService.loadFromDb(userId);
        return new ArrayList<>(dbCart.values());
    }

    /**
     * 加入购物车 —— 异步校验库存 + Redis 写入 + 异步同步 DB
     */
    public CartItem add(String userId, String productId, int quantity) {
        // 1. 异步从缓存获取商品，同时检查 Redis 购物车是否已有
        CompletableFuture<Product> productFuture = productCacheService.getProductAsync(productId);
        Product product = productFuture.join();
        if (product == null) {throw new BizException("商品不存在");}
        if (product.getStatus() != 1) {throw new BizException("商品已下架");}

        Map<String, CartItem> existingCart = cartRedisService.getCart(userId);
        CartItem existingItem = existingCart.get(productId);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + quantity;
            if (product.getStock() < newQuantity) {
                throw new BizException("商品库存不足");
            }
            existingItem.setQuantity(newQuantity);
            cartRedisService.updateItem(userId, existingItem);
            cartRedisService.syncToDbAsync(userId, List.of(existingItem));
            return existingItem;
        }

        if (product.getStock() < quantity) {throw new BizException("商品库存不足");}

        CartItem newItem = new CartItem();
        newItem.setId(IdUtil.fastSimpleUUID());
        newItem.setUserId(userId);
        newItem.setProductId(productId);
        newItem.setQuantity(quantity);
        newItem.setSelected(1);
        newItem.setCreateTime(LocalDateTime.now());

        cartRedisService.addItem(userId, newItem);
        cartRedisService.syncToDbAsync(userId, List.of(newItem));
        return newItem;
    }

    /**
     * 更新购物车项数量 —— Redis 立即生效 + 异步 DB
     */
    public CartItem updateQuantity(String userId, String id, int quantity) {
        Map<String, CartItem> cart = getCartOrThrow(userId);
        CartItem cartItem = findCartItem(cart, id, userId);

        // 异步校验库存
        Product product = productCacheService.getProductAsync(cartItem.getProductId()).join();
        if (product == null || product.getStatus() != 1) {
            throw new BizException("商品已下架");
        }
        if (product.getStock() < quantity) {
            throw new BizException("商品库存不足");
        }

        cartItem.setQuantity(quantity);
        cartRedisService.updateItem(userId, cartItem);
        cartRedisService.syncToDbAsync(userId, List.of(cartItem));
        return cartItem;
    }

    /**
     * 切换选中状态 —— Redis 立即生效 + 异步 DB
     */
    public CartItem toggleSelect(String userId, String id) {
        Map<String, CartItem> cart = getCartOrThrow(userId);
        CartItem cartItem = findCartItem(cart, id, userId);

        cartItem.setSelected(cartItem.getSelected() == 1 ? 0 : 1);  //选择状态
        cartRedisService.updateItem(userId, cartItem);
        cartRedisService.syncToDbAsync(userId, List.of(cartItem));
        return cartItem;
    }

    /**
     * 删除购物车项 —— Redis 立即生效 + 异步 DB
     */
    public void remove(String userId, String id) {
        Map<String, CartItem> cart = getCartOrThrow(userId);
        CartItem cartItem = findCartItem(cart, id, userId);

        cartRedisService.removeItem(userId, cartItem.getProductId());
        // DB 删除同步执行（避免残留数据）
        cartItemMapper.deleteById(id);
    }

    // ---------- helpers ----------

    private Map<String, CartItem> getCartOrThrow(String userId) {
        Map<String, CartItem> cart = cartRedisService.getCart(userId);
        if (cart.isEmpty()) {
            cart = cartRedisService.loadFromDb(userId);
        }
        return cart;
    }

    /**
     * 从购物车 Map 中按 cartItem.id 查找项
     */
    private CartItem findCartItem(Map<String, CartItem> cart, String id, String userId) {
        return cart.values().stream()
                .filter(item -> item.getId().equals(id) && item.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BizException("购物车项不存在"));
    }
}
