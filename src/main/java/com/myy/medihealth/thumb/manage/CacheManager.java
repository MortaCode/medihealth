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
 * 缓存层级：Caffeine（本地）→ Redis（分布式）→ MySQL（持久化）。
 * 读取时逐级回填，提升读性能。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private static final String CACHE_KEY_PREFIX = "article:";
    private static final long CAFFEINE_MAX_SIZE = 10_000;
    private static final long CAFFEINE_EXPIRE_MINUTES = 10;
    private static final long REDIS_EXPIRE_MINUTES = 30;

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
     * 多级缓存获取文章。
     * 查找顺序：Caffeine → Redis → MySQL，命中后逐级回填。
     *
     * @param articleId 文章ID
     * @return 文章实体，不存在返回 null
     */
    public HealthArticle getArticle(String articleId) {
        String cacheKey = CACHE_KEY_PREFIX + articleId;

        // 第一级：Caffeine 本地缓存
        HealthArticle article = caffeineCache.getIfPresent(cacheKey);
        if (article != null) {
            log.debug("Caffeine 缓存命中 articleId={}", articleId);
            heavyKeeper.add(articleId, 1);
            return article;
        }

        // 第二级：Redis 分布式缓存
        Object redisValue = redisTemplate.opsForValue().get(cacheKey);
        if (redisValue instanceof HealthArticle cached) {
            log.debug("Redis 缓存命中 articleId={}", articleId);
            caffeineCache.put(cacheKey, cached);
            heavyKeeper.add(articleId, 1);
            return cached;
        }

        // 第三级：MySQL 数据库
        article = healthArticleService.searchById(articleId);
        if (article != null) {
            log.debug("MySQL 命中 articleId={}，回填缓存", articleId);
            redisTemplate.opsForValue().set(cacheKey, article, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);
            caffeineCache.put(cacheKey, article);
            heavyKeeper.add(articleId, 1);
            return article;
        }

        log.warn("文章不存在 articleId={}", articleId);
        return null;
    }

    /**
     * 主动刷新缓存（文章更新时调用）。
     *
     * @param articleId 文章ID
     */
    public void refreshCache(String articleId) {
        String cacheKey = CACHE_KEY_PREFIX + articleId;
        HealthArticle article = healthArticleService.searchById(articleId);
        if (article != null) {
            redisTemplate.opsForValue().set(cacheKey, article, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);
            caffeineCache.put(cacheKey, article);
            log.info("缓存已刷新 articleId={}", articleId);
        } else {
            // 文章已删除，清理缓存
            evictCache(articleId);
        }
    }

    /**
     * 清除指定文章的缓存。
     *
     * @param articleId 文章ID
     */
    public void evictCache(String articleId) {
        String cacheKey = CACHE_KEY_PREFIX + articleId;
        redisTemplate.delete(cacheKey);
        caffeineCache.invalidate(cacheKey);
        log.info("缓存已清除 articleId={}", articleId);
    }

    /**
     * 每 20 秒对 HeavyKeeper 执行一次全局衰减，淘汰冷数据。
     */
    @Scheduled(fixedRate = 20000)
    public void decayHeavyKeeper() {
        heavyKeeper.decayAll();
        log.debug("HeavyKeeper 定时衰减完成，当前热门项数量={}", heavyKeeper.getHotItemCount());
    }

    /**
     * 获取 Caffeine 缓存统计信息。
     */
    public String getCaffeineStats() {
        return caffeineCache.stats().toString();
    }
}
