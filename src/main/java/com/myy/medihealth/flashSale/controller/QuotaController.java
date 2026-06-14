package com.myy.medihealth.flashSale.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.flashSale.service.*;
import com.myy.medihealth.flashSale.vo.QuotaBookRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 义诊名额秒杀控制器
 *
 * 流程：
 *   1. Redis Lua 原子预扣减
 *   2. DB 乐观锁持久化（最多 3 次重试）
 *   3. 若 3 次全失败 → RabbitMQ 异步补偿（最终一致性）
 */
@Slf4j
@RestController
@RequestMapping("medical/quota")
@RequiredArgsConstructor
public class QuotaController {

    @Value("${medical.quota.id}")
    private String quotaId;

    @Value("${medical.quota.total}")
    private int totalQuota;

    private final RedisPreDeductService redisPreDeductService;
    private final DatabaseUpdateService databaseUpdateService;
    private final QuotaMessageProducer quotaMessageProducer;

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failCount = new AtomicLong(0);
    /** MQ 异步补偿次数（DB 同步失败但已交 MQ 处理） */
    private final AtomicLong mqFallbackCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        redisPreDeductService.warmUpQuota(Long.parseLong(quotaId), totalQuota);
        log.info("秒杀名额预热完成 quotaId={}, total={}", quotaId, totalQuota);
    }

    /**
     * 预约名额（秒杀接口）
     */
    @PostMapping("/book")
    public Result<String> book(@RequestBody QuotaBookRequest request) {
        log.info("收到预约请求 userId={}, quotaId={}", request.userId(), request.quotaId());

        // 第一步：Redis 原子预扣减
        boolean deducted = redisPreDeductService.tryDeductQuota(Long.parseLong(request.quotaId()));
        if (!deducted) {
            failCount.incrementAndGet();
            return Result.error("名额已满，预约失败");
        }

        // 第二步：DB 乐观锁持久化（最多 3 次重试）
        boolean persisted = databaseUpdateService.deductQuotaWithOptimisticLock(request.quotaId());
        if (persisted) {
            successCount.incrementAndGet();
            log.info("预约成功 userId={}, quotaId={}", request.userId(), request.quotaId());
            return Result.success("预约成功");
        }

        // 第三步：DB 3 次全失败 → RabbitMQ 异步补偿
        // Redis 已扣减，不能丢失。MQ 消费者会持续重试 DB 落库，直到成功或最终回滚 Redis
        QuotaDeductMessage message = new QuotaDeductMessage(request.userId(), request.quotaId());
        quotaMessageProducer.sendDeductMessage(message);
        mqFallbackCount.incrementAndGet();

        log.warn("DB同步持久化失败，已转MQ异步补偿 userId={}, quotaId={}",
                request.userId(), request.quotaId());
        return Result.success("预约成功");
    }

    /**
     * 查询秒杀统计
     */
    @GetMapping("/stats")
    public Result<String> getStats() {
        long remaining = redisPreDeductService.getCurrentQuota(Long.parseLong(quotaId));
        String stats = String.format(
                "成功: %d, 失败: %d, MQ补偿中: %d, 剩余名额: %d",
                successCount.get(), failCount.get(), mqFallbackCount.get(), remaining);
        return Result.success(stats);
    }
}
