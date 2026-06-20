package com.myy.medihealth.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 风控服务 — 模拟接口
 *
 * 真实场景：对接风控引擎（美团/阿里/自研），
 * 进行用户行为分析、设备指纹、IP风险、订单风控评分
 * 此处仅模拟调用效果，默认通过
 */
@Slf4j
@Service
public class RiskControlService {

    /**
     * 下单前风控检查
     *
     * @param userId      用户 ID
     * @param amount      订单金额
     * @param productIds  商品 ID 列表
     * @return 风控结果
     */
    public RiskResult checkOrderRisk(String userId, BigDecimal amount,
                                      List<String> productIds) {
        log.info("[风控] 模拟下单风控检查 userId={}, amount={}, products={}",
                userId, amount, productIds != null ? productIds.size() : 0);

        RiskResult result = new RiskResult();
        result.setPassed(true);
        result.setRiskLevel("LOW");
        result.setScore(15); // 0-100, lower is safer
        result.setReason("模拟风控：默认通过");
        return result;
    }

    /**
     * 支付前风控检查
     */
    public RiskResult checkPaymentRisk(String userId, String orderId,
                                        BigDecimal amount, String channel) {
        log.info("[风控] 模拟支付风控检查 userId={}, orderId={}, channel={}",
                userId, orderId, channel);

        RiskResult result = new RiskResult();
        result.setPassed(true);
        result.setRiskLevel("LOW");
        result.setScore(10);
        result.setReason("模拟风控：支付默认通过");
        return result;
    }

    public static class RiskResult {
        private boolean passed;
        private String riskLevel;
        private int score;
        private String reason;

        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
