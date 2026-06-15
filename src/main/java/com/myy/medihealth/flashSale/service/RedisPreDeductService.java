package com.myy.medihealth.flashSale.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Redis 预扣减服务。
 * 通过 Lua 脚本原子性地扣减 Redis 中的剩余名额，减轻数据库压力。
 */
@Service
@RequiredArgsConstructor
public class RedisPreDeductService {

    private static final Logger log = LoggerFactory.getLogger(RedisPreDeductService.class);

    private static final String QUOTA_KEY_PREFIX = "quota:";
    private static final String USER_QUOTA_KEY_PREFIX = "quota:user:";

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> deductScript;

    /**
     * 尝试在 Redis 中原子扣减一个名额（带一人一单校验）
     *
     * @param quotaId 名额编号
     * @param userId  用户ID
     * @return 扣减结果码
     *         0 或正数：扣减成功，返回剩余库存
     *         -1：库存不足
     *         -2：用户已购买过
     */
    public boolean tryDeductQuota(Long quotaId, String userId) {
        String stockKey = QUOTA_KEY_PREFIX + quotaId;
        String userKey = USER_QUOTA_KEY_PREFIX + quotaId;

        Long result = stringRedisTemplate.execute(
                deductScript,
                Arrays.asList(stockKey, userKey),  // 两个 KEYS
                userId                             // ARGV[1]
        );

        if (result == null) {
            log.warn("Redis 执行失败，返回 null key={}", stockKey);
            return false;
        }

        if (result < 0) {
            if (result == -2) {
                log.warn("用户已购买过 quotaId={}, userId={}", quotaId, userId);
            } else {
                log.warn("名额已用完 key={}", stockKey);
            }
            return false;
        }

        log.info("扣减成功 quotaId={}, userId={}, remaining={}", quotaId, userId, result);
        return true;
    }

    /**
     * 回滚名额：将指定名额 +1（INCR），用于异步补偿。
     */
    public void rollbackQuota(Long quotaId) {
        String key = QUOTA_KEY_PREFIX + quotaId;
        Long result = stringRedisTemplate.opsForValue().increment(key, 1);
        log.info("Redis回滚名额 key={}, afterRollback={}", key, result);
    }


    /**
     * 预热名额：将指定名额的剩余数量写入 Redis。
     *
     * @param quotaId 名额编号
     * @param count   剩余数量
     */
    public void warmUpQuota(Long quotaId, int count) {
        String key = QUOTA_KEY_PREFIX + quotaId;
        stringRedisTemplate.opsForValue().set(key, String.valueOf(count));
        log.info("预热名额 key={}, count={}", key, count);
    }

    /**
     * 查询 Redis 中当前剩余名额。
     *
     * @param quotaId 名额编号
     * @return 剩余名额数量，若 key 不存在返回 0
     */
    public long getCurrentQuota(Long quotaId) {
        String key = QUOTA_KEY_PREFIX + quotaId;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        return Long.parseLong(value);
    }
}
