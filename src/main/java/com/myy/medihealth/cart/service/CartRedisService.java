package com.myy.medihealth.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myy.medihealth.cart.entity.CartItem;
import com.myy.medihealth.cart.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 购物车 Redis 缓存服务
 * 使用 Redis Hash 结构：key=cart:{userId}, field=productId, value=CartItem(JSON)
 * 高频读写走 Redis，异步批量同步至 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CartItemMapper cartItemMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService bizExecutor;

    private static final String CART_KEY_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    /**
     * 从 Redis 获取用户购物车
     */
    public Map<String, CartItem> getCart(String userId) {
        String key = CART_KEY_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }
        // 保持插入顺序
        Map<String, CartItem> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                CartItem item = objectMapper.convertValue(entry.getValue(), CartItem.class);
                result.put(entry.getKey().toString(), item);
            } catch (Exception e) {
                log.warn("购物车项反序列化失败 userId={}, productId={}", userId, entry.getKey());
            }
        }
        return result;
    }

    /**
     * 从 Redis 获取用户购物车（异步）
     */
    public CompletableFuture<Map<String, CartItem>> getCartAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> getCart(userId), bizExecutor);
    }

    // ---------- 写入 ----------

    /**
     * 添加商品到购物车 Redis Hash
     */
    public void addItem(String userId, CartItem item) {
        String key = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(key, item.getProductId(), item);
        redisTemplate.expire(key, CART_TTL);
    }

    /**
     * 更新购物车项
     */
    public void updateItem(String userId, CartItem item) {
        String key = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(key, item.getProductId(), item);
    }

    /**
     * 删除购物车项
     */
    public void removeItem(String userId, String productId) {
        String key = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(key, productId);
    }

    /**
     * 批量删除购物车项
     */
    public void removeItems(String userId, List<String> productIds) {
        String key = CART_KEY_PREFIX + userId;
        Object[] fields = productIds.toArray();
        redisTemplate.opsForHash().delete(key, fields);
    }

    /**
     * 清空购物车缓存
     */
    public void clearCart(String userId) {
        redisTemplate.delete(CART_KEY_PREFIX + userId);
    }

    // ---------- 同步 ----------

    /**
     * 异步将 Redis 购物车同步到 MySQL（异步双写）
     */
    public void syncToDbAsync(String userId, List<CartItem> items) {
        CompletableFuture.runAsync(() -> {
            try {
                for (CartItem item : items) {
                    CartItem existing = cartItemMapper.selectById(item.getId());
                    if (existing != null) {
                        cartItemMapper.updateById(item);
                    } else {
                        cartItemMapper.insert(item);
                    }
                }
                log.debug("购物车异步同步完成 userId={}, count={}", userId, items.size());
            } catch (Exception e) {
                log.error("购物车异步同步失败 userId={}", userId, e);
            }
        }, bizExecutor);
    }

    /**
     * 从 DB 加载购物车并回填 Redis
     */
    public Map<String, CartItem> loadFromDb(String userId) {
        List<CartItem> dbItems = cartItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getCreateTime)
        );
        if (dbItems.isEmpty()) {return Collections.emptyMap();}
        // 回填 Redis
        String key = CART_KEY_PREFIX + userId;
        Map<String, CartItem> result = new LinkedHashMap<>();
        for (CartItem item : dbItems) {
            redisTemplate.opsForHash().put(key, item.getProductId(), item);
            result.put(item.getProductId(), item);
        }
        redisTemplate.expire(key, CART_TTL);
        return result;
    }
}
