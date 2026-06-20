package com.myy.medihealth.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 积分服务 — 模拟接口
 *
 * 真实场景：对接积分中心，下单赠送积分、支付抵扣积分
 * 此处仅模拟调用效果，返回固定结果
 */
@Slf4j
@Service
public class PointsService {

    /**
     * 预冻结积分（下单时调用，防止重复抵扣）
     *
     * @param userId 用户 ID
     * @param points 拟使用积分数
     * @return 冻结流水号
     */
    public String freezePoints(String userId, int points) {
        log.info("[积分] 模拟冻结积分 userId={}, points={}", userId, points);
        return "points_freeze_" + System.currentTimeMillis();
    }

    /**
     * 确认消耗积分（支付成功后调用）
     *
     * @param freezeId 冻结流水号
     */
    public void confirmPoints(String freezeId) {
        log.info("[积分] 模拟确认消耗积分 freezeId={}", freezeId);
    }

    /**
     * 回滚积分（订单取消/支付失败时调用）
     *
     * @param freezeId 冻结流水号
     */
    public void rollbackPoints(String freezeId) {
        log.info("[积分] 模拟回滚积分 freezeId={}", freezeId);
    }

    /**
     * 赠送积分（支付成功后按实付金额赠送）
     *
     * @param userId 用户 ID
     * @param amount 实付金额
     * @return 赠送积分数
     */
    public int grantPoints(String userId, BigDecimal amount) {
        int earned = amount.intValue(); // 1元 = 1积分
        log.info("[积分] 模拟赠送积分 userId={}, amount={}, earned={}", userId, amount, earned);
        return earned;
    }
}
