package com.myy.medihealth.flashSale.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Redis 预扣减服务。
 * 通过 Lua 脚本原子性地扣减 Redis 中的剩余名额，减轻数据库压力。
 */
@Service
@RequiredArgsConstructor
public class RedisPreDeductService {

    private static final Logger log = LoggerFactory.getLogger(RedisPreDeductService.class);

    private static final String QUOTA_KEY_PREFIX = "quota:";

    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> deductScript;

    @PostConstruct
    public void init() {
        deductScript = new DefaultRedisScript<>();
        deductScript.setScriptText(
                "local key = KEYS[1]\n" +
                "local stock = redis.call('get', key)\n" +
                "if stock and tonumber(stock) > 0 then\n" +
                "   local newStock = redis.call('decr', key)\n" +
                "   return newStock\n" +
                "else\n" +
                "   return -1\n" +
                "end"
        );
        deductScript.setResultType(Long.class);
        log.info("Redis 秒杀 Lua 扣减脚本初始化完成");
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
     * 尝试在 Redis 中原子扣减一个名额。
     *
     * @param quotaId 名额编号
     * @return true=扣减成功，false=名额已用完
     */
    public boolean tryDeductQuota(Long quotaId) {
        String key = QUOTA_KEY_PREFIX + quotaId;
        Long result = stringRedisTemplate.execute(deductScript, Collections.singletonList(key));
        if (result == null || result < 0) {
            log.warn("Redis 扣减失败，名额已用完 key={}", key);
            return false;
        }
        log.info("Redis 扣减成功 key={}, remaining={}", key, result);
        return true;
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
