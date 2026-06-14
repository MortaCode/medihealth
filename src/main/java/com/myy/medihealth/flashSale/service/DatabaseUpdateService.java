package com.myy.medihealth.flashSale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.flashSale.entity.Quota;
import com.myy.medihealth.flashSale.mapper.QuotaMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据库更新服务。
 * 在 Redis 预扣减成功后，通过乐观锁将扣减结果持久化到 MySQL。
 */
@Service
@RequiredArgsConstructor
public class DatabaseUpdateService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUpdateService.class);

    private final QuotaMapper quotaMapper;

    /**
     * 使用乐观锁在数据库中扣减名额。
     * 最多重试 3 次以应对并发冲突。
     *
     * @param quotaId 名额编号
     * @return true=持久化成功，false=所有重试均失败
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deductQuotaWithOptimisticLock(String quotaId) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            // 查询当前记录以获取最新版本号
            Quota quota = quotaMapper.selectOne(
                    new LambdaQueryWrapper<Quota>()
                            .eq(Quota::getQuotaId, quotaId)
            );
            if (quota == null) {
                log.error("名额记录不存在 quotaId={}", quotaId);
                return false;
            }
            if (quota.getRemainingQuota() <= 0) {
                log.warn("名额已用完 quotaId={}", quotaId);
                return false;
            }
            int affected = quotaMapper.deductWithOptimisticLock(quotaId, quota.getVersion());
            if (affected > 0) {
                log.info("数据库扣减成功 quotaId={}, attempt={}", quotaId, i + 1);
                return true;
            }
            log.warn("乐观锁冲突，重试 quotaId={}, attempt={}", quotaId, i + 1);
        }
        log.error("数据库扣减失败，超过最大重试次数 quotaId={}", quotaId);
        return false;
    }
}
