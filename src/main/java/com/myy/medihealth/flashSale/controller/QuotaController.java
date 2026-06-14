package com.myy.medihealth.flashSale.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.flashSale.service.DatabaseUpdateService;
import com.myy.medihealth.flashSale.service.RedisPreDeductService;
import com.myy.medihealth.flashSale.vo.QuotaBookRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 义诊名额秒杀控制器。
 * 提供名额预约（秒杀）和统计查询接口。
 */
@RestController
@RequestMapping("medical/quota")
@RequiredArgsConstructor
public class QuotaController {

    private static final Logger log = LoggerFactory.getLogger(QuotaController.class);

    @Value("${medical.quota.id}")
    private String quotaId;

    @Value("${medical.quota.total}")
    private int totalQuota;

    private final RedisPreDeductService redisPreDeductService;
    private final DatabaseUpdateService databaseUpdateService;

    /** 成功预约计数 */
    private final AtomicLong successCount = new AtomicLong(0);

    /** 失败预约计数 */
    private final AtomicLong failCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        redisPreDeductService.warmUpQuota(Long.parseLong(quotaId), totalQuota);
        log.info("秒杀名额预热完成 quotaId={}, total={}", quotaId, totalQuota);
    }

    /**
     * 预约名额（秒杀接口）。
     *
     * @param request 预约请求（包含用户ID和名额ID）
     * @return 预约结果
     */
    @PostMapping("/book")
    public Result<String> book(@RequestBody QuotaBookRequest request) {
        log.info("收到预约请求 userId={}, quotaId={}", request.userId(), request.quotaId());

        // 第一步：Redis 预扣减
        boolean deducted = redisPreDeductService.tryDeductQuota(Long.parseLong(request.quotaId()));
        if (!deducted) {
            failCount.incrementAndGet();
            return Result.error("名额已满，预约失败");
        }

        // 第二步：数据库持久化（乐观锁）
        boolean persisted = databaseUpdateService.deductQuotaWithOptimisticLock(request.quotaId());
        if (!persisted) {
            failCount.incrementAndGet();
            log.error("预约持久化失败 userId={}, quotaId={}", request.userId(), request.quotaId());
            return Result.error("系统繁忙，预约失败");
        }

        successCount.incrementAndGet();
        log.info("预约成功 userId={}, quotaId={}", request.userId(), request.quotaId());
        return Result.success("预约成功");
    }

    /**
     * 查询秒杀统计信息。
     *
     * @return 秒杀统计数据
     */
    @GetMapping("/stats")
    public Result<String> getStats() {
        long remaining = redisPreDeductService.getCurrentQuota(Long.parseLong(quotaId));
        String stats = String.format("成功: %d, 失败: %d, 剩余名额: %d",
                successCount.get(), failCount.get(), remaining);
        return Result.success(stats);
    }
}
