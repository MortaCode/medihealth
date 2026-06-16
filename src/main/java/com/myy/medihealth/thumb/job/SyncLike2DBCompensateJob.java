package com.myy.medihealth.thumb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.myy.medihealth.thumb.service.LikeUPService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库的补偿措施。
 * 每天凌晨 2:00 扫描所有残留的临时时间片键，兜底同步到数据库。
 */
@Slf4j
@Component
public class SyncLike2DBCompensateJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SyncLike2DBJob syncLike2DBJob;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        log.info("开始补偿同步点赞数据");
        Set<String> thumbKeys = redisTemplate.keys(LikeUPService.LIKE_TEMP_KEY_PREFIX + "*");
        Set<String> needHandleDataSet = new HashSet<>();

        if (thumbKeys != null) {
            thumbKeys.stream()
                    .filter(ObjUtil::isNotNull)
                    .forEach(thumbKey -> needHandleDataSet.add(
                            thumbKey.replace(LikeUPService.LIKE_TEMP_KEY_PREFIX, "")
                    ));
        }

        if (CollUtil.isEmpty(needHandleDataSet)) {
            log.info("没有需要补偿的临时点赞数据");
            return;
        }

        // 逐时间片补偿数据
        for (String date : needHandleDataSet) {
            syncLike2DBJob.syncLike2DBByDate(date);
        }
        log.info("点赞数据补偿同步完成，处理 {} 个残留时间片", needHandleDataSet.size());
    }

    /**
     * 清理所有点赞相关的 Redis 数据（管理员工具）。
     */
    public long clearAllLikeData() {
        long deletedCount = 0;

        Set<String> tempKeys = redisTemplate.keys(LikeUPService.LIKE_TEMP_KEY_PREFIX + "*");
        if (tempKeys != null && !tempKeys.isEmpty()) {
            deletedCount += redisTemplate.delete(tempKeys);
        }

        Set<String> userKeys = redisTemplate.keys(LikeUPService.LIKE_USER_KEY_PREFIX + "*");
        if (userKeys != null && !userKeys.isEmpty()) {
            deletedCount += redisTemplate.delete(userKeys);
        }

        log.info("清理所有点赞Redis数据，共删除 {} 个键", deletedCount);
        return deletedCount;
    }
}
