package com.myy.medihealth.thumb.job;

import com.myy.medihealth.thumb.entity.HealthArticle;
import com.myy.medihealth.thumb.mapper.HealthArticleMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 点赞数据补偿同步任务。
 * 每天凌晨 2:00 扫描所有残留的临时时间片键，兜底同步到数据库。
 */
@Component
@RequiredArgsConstructor
public class SyncLike2DBCompensateJob {

    private static final Logger log = LoggerFactory.getLogger(SyncLike2DBCompensateJob.class);

    private static final String LIKE_TEMP_KEY_PREFIX = "like:temp:";
    private static final String LIKE_USER_KEY_PREFIX = "like:";

    private final RedisTemplate<String, String> redisTemplate;
    private final HealthArticleMapper healthArticleMapper;

    /**
     * 每天凌晨 2:00 执行补偿同步。
     * 扫描所有残留的临时时间片键，将未同步的数据写入数据库。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void compensateSync() {
        log.info("补偿同步任务开始");

        Set<String> tempKeys = redisTemplate.keys(LIKE_TEMP_KEY_PREFIX + "*");
        if (tempKeys == null || tempKeys.isEmpty()) {
            log.info("无残留临时数据，补偿同步结束");
            return;
        }

        log.info("发现 {} 个残留时间片键，开始补偿处理", tempKeys.size());
        int processedKeys = 0;

        for (String tempKey : tempKeys) {
            try {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(tempKey);
                if (entries == null || entries.isEmpty()) {
                    // 空键直接清理
                    redisTemplate.delete(tempKey);
                    continue;
                }

                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    String articleId = (String) entry.getKey();
                    String countStr = (String) entry.getValue();

                    try {
                        int count = Integer.parseInt(countStr);
                        HealthArticle article = healthArticleMapper.selectById(articleId);
                        if (article != null) {
                            int currentLikes = article.getLikeCount() == null ? 0 : article.getLikeCount();
                            int newLikes = Math.max(0, currentLikes + count);
                            article.setLikeCount(newLikes);
                            article.setUpdateTime(LocalDateTime.now());
                            healthArticleMapper.updateById(article);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("补偿同步：无法解析计数 articleId={}, value={}", articleId, countStr);
                    }
                }

                // 处理完毕后删除临时键
                redisTemplate.delete(tempKey);
                processedKeys++;
            } catch (Exception e) {
                log.error("补偿同步处理失败 key={}", tempKey, e);
            }
        }

        log.info("补偿同步任务完成，处理 {} 个残留键", processedKeys);
    }

    /**
     * 清理所有用户点赞标记数据（管理员工具）。
     */
    public long clearAllLikeData() {
        long deletedCount = 0;

        // 清理临时时间片数据
        Set<String> tempKeys = redisTemplate.keys(LIKE_TEMP_KEY_PREFIX + "*");
        if (tempKeys != null && !tempKeys.isEmpty()) {
            deletedCount += redisTemplate.delete(tempKeys);
        }

        // 清理用户点赞标记数据
        Set<String> userKeys = redisTemplate.keys(LIKE_USER_KEY_PREFIX + "*");
        if (userKeys != null && !userKeys.isEmpty()) {
            deletedCount += redisTemplate.delete(userKeys);
        }

        log.info("清理所有点赞Redis数据，共删除 {} 个键", deletedCount);
        return deletedCount;
    }
}
