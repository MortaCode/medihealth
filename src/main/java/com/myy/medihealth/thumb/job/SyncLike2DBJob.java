package com.myy.medihealth.thumb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.thumb.entity.LikeRecord;
import com.myy.medihealth.thumb.mapper.HealthArticleMapper;
import com.myy.medihealth.thumb.service.LikeUPService;
import com.myy.medihealth.thumb.vo.LikeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库。
 * 每 10 秒读取上一个时间片的临时数据，批量写入 MySQL。
 */
@Slf4j
@Component
public class SyncLike2DBJob {

    @Resource
    private LikeUPService likeUPService;

    @Resource
    private HealthArticleMapper healthArticleMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void init() {
        log.info("SyncLike2DBJob 初始化完成，定时任务已注册");
    }

    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {
        String previousTimeslice = likeUPService.previousTimeslice();
        log.debug("开始同步点赞数据 timeslice={}", previousTimeslice);
        syncLike2DBByDate(previousTimeslice);
        log.debug("点赞数据同步完成 timeslice={}", previousTimeslice);
    }

    public void syncLike2DBByDate(String date) {
        String tempThumbKey = LikeUPService.tempThumbKey(date);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        log.info("allTempThumbMap={}", allTempThumbMap);

        if (CollUtil.isEmpty(allTempThumbMap)) {
            return;
        }

        List<LikeRecord> likeList = new ArrayList<>();
        LambdaQueryWrapper<LikeRecord> removeWrapper = new LambdaQueryWrapper<>();
        Map<String, Long> articleLikeCountMap = new HashMap<>();
        boolean needRemove = false;

        for (Object keyObj : allTempThumbMap.keySet()) {
            String userIdArticleId = (String) keyObj;
            String[] parts = userIdArticleId.split(":");
            String userId = parts[0];
            String articleId = parts[1];

            Long likeType = Long.valueOf(allTempThumbMap.get(userIdArticleId).toString());

            if (likeType == LikeEnum.INCR.getValue()) {
                LikeRecord record = new LikeRecord();
                record.setId(IdUtil.objectId());
                record.setUserId(userId);
                record.setArticleId(articleId);
                record.setCreateTime(LocalDateTime.now());
                likeList.add(record);
            } else if (likeType == LikeEnum.DECR.getValue()) {
                needRemove = true;
                removeWrapper.or().eq(LikeRecord::getUserId, userId).eq(LikeRecord::getArticleId, articleId);
            } else {
                continue;
            }
            //文章点赞数
            articleLikeCountMap.put(articleId, articleLikeCountMap.getOrDefault(articleId, 0L) + likeType);
        }

        // 批量插入点赞记录
        if (!likeList.isEmpty()) {
            likeUPService.saveBatch(likeList);
        }

        // 批量删除取消点赞记录
        if (needRemove) {
            likeUPService.remove(removeWrapper);
        }

        // 原子更新文章点赞数
        articleLikeCountMap.forEach((articleId, delta) -> {
            healthArticleMapper.incrLikeCount(articleId, delta);
        });

        // 清理已处理的临时数据
        redisTemplate.delete(tempThumbKey);
    }

}
