package com.myy.medihealth.thumb.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.thumb.entity.HealthArticle;
import com.myy.medihealth.thumb.entity.LikeRecord;
import com.myy.medihealth.thumb.mapper.HealthArticleMapper;
import com.myy.medihealth.thumb.mapper.LikeRecordMapper;
import com.myy.medihealth.thumb.service.LikeUPService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 点赞数据定时同步任务。
 * 每 10 秒将上一个时间片的点赞临时数据从 Redis 同步到 MySQL。
 */
@Component
@RequiredArgsConstructor
public class SyncLike2DBJob {

    private static final Logger log = LoggerFactory.getLogger(SyncLike2DBJob.class);

    private static final String LIKE_TEMP_KEY_PREFIX = "like:temp:";

    private final RedisTemplate<String, String> redisTemplate;
    private final LikeRecordMapper likeRecordMapper;
    private final HealthArticleMapper healthArticleMapper;
    private final LikeUPService likeUPService;

    /**
     * 每 10 秒执行一次：读取上一个时间片的临时点赞数据，批量同步到数据库。
     */
    @Scheduled(fixedRate = 10000)
    public void syncLikeData() {
        // 计算上一个时间片（10秒前）
        String previousTimeslice = likeUPService.timeslice();
        String tempKey = LIKE_TEMP_KEY_PREFIX + previousTimeslice;

        // 读取该时间片的所有文章点赞变更数据
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(tempKey);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        log.info("开始同步点赞数据 timeslice={}, articleCount={}", previousTimeslice, entries.size());

        List<String> updatedArticleIds = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String articleId = (String) entry.getKey();
            String countStr = (String) entry.getValue();

            try {
                int count = Integer.parseInt(countStr);
                if (count > 0) {
                    // 点赞增量：需要为新增点赞创建 LikeRecord
                    // 注意：这里无法精确知道哪些用户执行了点赞，
                    // 实际生产环境应使用更细粒度的时间片或事件记录
                    // 这里更新文章的总点赞数
                    updateArticleLikeCount(articleId, count);
                    updatedArticleIds.add(articleId);
                } else if (count < 0) {
                    // 取消点赞增量：减少文章点赞数
                    updateArticleLikeCount(articleId, count);
                    updatedArticleIds.add(articleId);
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析点赞计数 articleId={}, value={}", articleId, countStr);
            }
        }

        // 清理已处理的临时数据
        redisTemplate.delete(tempKey);
        log.info("点赞数据同步完成 timeslice={}, updatedArticles={}", previousTimeslice, updatedArticleIds.size());
    }

    /**
     * 更新文章点赞计数并在数据库中增删点赞记录。
     */
    private void updateArticleLikeCount(String articleId, int increment) {
        HealthArticle article = healthArticleMapper.selectById(articleId);
        if (article == null) {
            log.warn("文章不存在，跳过点赞同步 articleId={}", articleId);
            return;
        }

        int newCount = Math.max(0, (article.getLikeCount() == null ? 0 : article.getLikeCount()) + increment);
        article.setLikeCount(newCount);
        article.setUpdateTime(LocalDateTime.now());
        healthArticleMapper.updateById(article);
    }

    /**
     * 扫描所有临时时间片键（用于排查）。
     */
    public Set<String> scanTempKeys() {
        return redisTemplate.keys(LIKE_TEMP_KEY_PREFIX + "*");
    }
}
