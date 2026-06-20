package com.myy.medihealth.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 通知服务 — 模拟接口
 *
 * 真实场景：对接短信平台（阿里云 SMS）/ 推送平台（极光/信鸽）/ 微信模板消息
 * 此处仅模拟调用效果
 */
@Slf4j
@Service
public class NotificationService {

    /**
     * 下单成功通知
     */
    public void notifyOrderCreated(String userId, String orderId, BigDecimal amount) {
        log.info("[通知] 模拟下单成功通知 userId={}, orderId={}, amount={}", userId, orderId, amount);
    }

    /**
     * 支付成功通知
     */
    public void notifyPaymentSuccess(String userId, String orderId, BigDecimal amount) {
        log.info("[通知] 模拟支付成功通知 userId={}, orderId={}, amount={}", userId, orderId, amount);
    }

    /**
     * 发货通知（含物流单号）
     */
    public void notifyShipped(String userId, String orderId, String trackingNo) {
        log.info("[通知] 模拟发货通知 userId={}, orderId={}, trackingNo={}",
                userId, orderId, trackingNo);
    }

    /**
     * 退款通知
     */
    public void notifyRefund(String userId, String orderId, BigDecimal amount) {
        log.info("[通知] 模拟退款通知 userId={}, orderId={}, amount={}", userId, orderId, amount);
    }
}
