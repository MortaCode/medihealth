package com.myy.medihealth.flashSale.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.flashSale.entity.Quota;
import com.myy.medihealth.flashSale.entity.QuotaRecord;
import com.myy.medihealth.flashSale.mapper.QuotaMapper;
import com.myy.medihealth.flashSale.mapper.QuotaRecordMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 数据库更新服务。
 * 在 Redis 预扣减成功后，通过乐观锁将扣减结果持久化到 MySQL，并登记预约记录。
 */
@Service
@RequiredArgsConstructor
public class DatabaseUpdateService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUpdateService.class);

    private final QuotaMapper quotaMapper;
    private final QuotaRecordMapper quotaRecordMapper;

    /**
     * 使用乐观锁在数据库中扣减名额，并记录谁抢到了名额。
     * 扣减 + 登记在同一事务中，最多重试 3 次以应对并发冲突。
     *
     * @param quotaId 名额编号
     * @param userId  用户ID
     * @return true=持久化成功，false=所有重试均失败
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deductQuotaWithOptimisticLock(String quotaId, String userId) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
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
                QuotaRecord record = new QuotaRecord();
                record.setId(IdUtil.fastSimpleUUID());
                record.setUserId(userId);
                record.setQuotaId(quotaId);
                record.setSource(0);
                record.setCreateTime(LocalDateTime.now());
                quotaRecordMapper.insert(record);

                log.info("数据库扣减+登记成功 quotaId={}, userId={}, attempt={}", quotaId, userId, i + 1);
                return true;
            }
            log.warn("乐观锁冲突，重试 quotaId={}, userId={}, attempt={}", quotaId, userId, i + 1);
        }
        log.error("数据库扣减失败，超过最大重试次数 quotaId={}, userId={}", quotaId, userId);
        return false;
    }
}
