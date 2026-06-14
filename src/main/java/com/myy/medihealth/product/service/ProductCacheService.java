package com.myy.medihealth.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 商品 Redis 缓存服务
 * 缓存策略：Redis Hash 存储商品 JSON，TTL 30 分钟，
 * 命中直接返回，未命中回源 DB 并回填缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private static final String CACHE_KEY_PREFIX = "product:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService bizExecutor;

    /**
     * 异步获取单个商品（先查缓存，miss 回源 DB）
     */
    public CompletableFuture<Product> getProductAsync(String productId) {
        return CompletableFuture.supplyAsync(() -> getProduct(productId), bizExecutor);
    }

    /**
     * 获取单个商品
     */
    public Product getProduct(String productId) {
        String key = CACHE_KEY_PREFIX + productId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached instanceof Product p) {
                return p;
            }
            try {
                return objectMapper.convertValue(cached, Product.class);
            } catch (Exception e) {
                log.warn("商品缓存反序列化失败 productId={}", productId);
            }
        }
        // 回源 DB
        Product product = productMapper.selectById(productId);
        if (product != null) {
            redisTemplate.opsForValue().set(key, product, CACHE_TTL);
        }
        return product;
    }

    /**
     * 批量获取商品（优先从缓存，miss 的批量回源 DB 后回填）
     */
    public CompletableFuture<Map<String, Product>> getProductsAsync(Set<String> productIds) {
        return CompletableFuture.supplyAsync(() -> getProducts(productIds), bizExecutor);
    }

    /**
     * 批量获取商品
     */
    public Map<String, Product> getProducts(Set<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 批量查缓存
        List<String> keys = productIds.stream()
                .map(id -> CACHE_KEY_PREFIX + id)
                .toList();
        List<Object> cachedValues = redisTemplate.opsForValue().multiGet(keys);

        Map<String, Product> result = new java.util.HashMap<>();
        List<String> missedIds = new ArrayList<>();

        int i = 0;
        for (String id : productIds) {
            Object cached = cachedValues != null && i < cachedValues.size() ? cachedValues.get(i) : null;
            if (cached != null) {
                try {
                    Product p = cached instanceof Product product
                            ? product
                            : objectMapper.convertValue(cached, Product.class);
                    result.put(id, p);
                } catch (Exception e) {
                    missedIds.add(id);
                }
            } else {
                missedIds.add(id);
            }
            i++;
        }

        // 2. 批量回源 DB
        if (!missedIds.isEmpty()) {
            List<Product> dbProducts = productMapper.selectBatchIds(missedIds);
            for (Product p : dbProducts) {
                result.put(p.getId(), p);
                redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + p.getId(), p, CACHE_TTL);
            }
        }

        return result;
    }

    /**
     * 缓存单个商品
     */
    public void cacheProduct(Product product) {
        if (product != null) {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + product.getId(), product, CACHE_TTL);
        }
    }

    /**
     * 清除商品缓存
     */
    public void evictCache(String productId) {
        redisTemplate.delete(CACHE_KEY_PREFIX + productId);
    }

    /**
     * 批量清除商品缓存
     */
    public void evictCache(Set<String> productIds) {
        List<String> keys = productIds.stream()
                .map(id -> CACHE_KEY_PREFIX + id)
                .collect(Collectors.toList());
        redisTemplate.delete(keys);
    }
}
