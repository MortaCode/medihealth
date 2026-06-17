package com.myy.medihealth.thumb.manage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.myy.medihealth.thumb.entity.HealthArticle;
import com.myy.medihealth.thumb.service.HeavyKeeper;
import com.myy.medihealth.thumb.service.HealthArticleService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存管理器。
 * <p>
 * 缓存层级：Caffeine（本地，仅热点）→ Redis（分布式）→ MySQL（持久化）。
 * 使用 Cache-Aside 模式：读回填，写失效。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private static final String CACHE_KEY_PREFIX = "article:";
    private static final String NULL_MARKER = "__NULL__";
    private static final long CAFFEINE_MAX_SIZE = 10_000;
    private static final long CAFFEINE_EXPIRE_MINUTES = 10;
    private static final long REDIS_EXPIRE_MINUTES = 30;
    private static final long NULL_MARKER_REDIS_TTL_MINUTES = 5;

    private final HealthArticleService healthArticleService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HeavyKeeper heavyKeeper;

    private Cache<String, HealthArticle> caffeineCache;

    @PostConstruct
    public void init() {
        this.caffeineCache = Caffeine.newBuilder()
                .maximumSize(CAFFEINE_MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(CAFFEINE_EXPIRE_MINUTES))
                .recordStats()
                .build();
        log.info("CacheManager 初始化完成 caffeineMaxSize={}, redisExpireMinutes={}",
                CAFFEINE_MAX_SIZE, REDIS_EXPIRE_MINUTES);
    }

    /**
     * 多级缓存获取文章（Cache-Aside 读）。
     * Caffeine → Redis → MySQL，仅热点文章回填 Caffeine。
     */
    public HealthArticle getArticle(String articleId) {
        String cacheKey = CACHE_KEY_PREFIX + articleId;

        // L1: Caffeine 本地缓存
        HealthArticle article = caffeineCache.getIfPresent(cacheKey);
        if (article != null) {
            heavyKeeper.add(articleId, 1);
            return article;
        }

        // L2: Redis 分布式缓存
        Object redisValue = redisTemplate.opsForValue().get(cacheKey);
        if (redisValue != null) {
            // 缓存穿透防护：null 标记
            if (NULL_MARKER.equals(redisValue)) {
                return null;
            }
            if (redisValue instanceof HealthArticle cached) {
                heavyKeeper.add(articleId, 1);
                // 仅热点文章回填 Caffeine
                if (heavyKeeper.isHot(articleId)) {
                    caffeineCache.put(cacheKey, cached);
                }
                return cached;
            }
        }

        // L3: MySQL 数据库
        article = healthArticleService.searchById(articleId);
        if (article != null) {
            heavyKeeper.add(articleId, 1);
            // 始终回填 Redis
            redisTemplate.opsForValue().set(cacheKey, article, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);
            // 仅热点文章回填 Caffeine
            if (heavyKeeper.isHot(articleId)) {
                caffeineCache.put(cacheKey, article);
            }
            return article;
        }

        // Miss: 写入 null 标记防穿透
        redisTemplate.opsForValue().set(cacheKey, NULL_MARKER, NULL_MARKER_REDIS_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("缓存穿透防护：写入 null 标记 articleId={}", articleId);
        return null;
    }

    /**
     * 主动刷新缓存（文章更新后调用）。
     */
    public void refreshCache(String articleId) {
        String cacheKey = CACHE_KEY_PREFIX + articleId;
        HealthArticle article = healthArticleService.searchById(articleId);
        if (article != null) {
            redisTemplate.opsForValue().set(cacheKey, article, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);
            if (heavyKeeper.isHot(articleId)) {
                caffeineCache.put(cacheKey, article);
            }
            log.debug("缓存已刷新 articleId={}", articleId);
        } else {
            evictCache(articleId);
        }
    }

    /**
     * 清除指定文章的缓存（Cache-Aside 写失效）。
     */
    public void evictCache(String articleId) {
        String cacheKey = CACHE_KEY_PREFIX + articleId;
        redisTemplate.delete(cacheKey);
        caffeineCache.invalidate(cacheKey);
        log.debug("缓存已清除 articleId={}", articleId);
    }

    @Scheduled(fixedRate = 20000)
    public void decayHeavyKeeper() {
        heavyKeeper.decayAll();
    }

    public String getCaffeineStats() {
        return caffeineCache.stats().toString();
    }
}
