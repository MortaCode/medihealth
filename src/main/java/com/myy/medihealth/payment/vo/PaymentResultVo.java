package com.myy.medihealth.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下单结果 VO — 完整链路返回
 *
 * 聚合：订单信息 + 优惠消耗 + 积分变动 + 物流信息 + 风控结果 + 通知状态
 */
@Data
public class PaymentResultVo {

    // ===== 订单 =====

    /** 订单号 */
    private String orderId;

    /** 订单状态：0=待支付 */
    private Integer orderStatus;

    /** 商品原价合计 */
    private BigDecimal totalItemAmount;

    /** 优惠总额 */
    private BigDecimal totalDiscount;

    /** 实付金额 */
    private BigDecimal actualAmount;

    /** 订单过期时间 */
    private String expireTime;

    // ===== 优惠 =====

    /** 已消耗的优惠券列表 */
    private List<ConsumedCoupon> consumedCoupons;

    @Data
    public static class ConsumedCoupon {
        private String couponId;
        private String couponName;
        private BigDecimal discountAmount;
    }

    // ===== 积分 =====

    /** 积分处理结果 */
    private PointsResult pointsResult;

    @Data
    public static class PointsResult {
        /** 本次抵扣积分数 */
        private int usedPoints;
        /** 预期赠送积分数 */
        private int earnPoints;
    }

    // ===== 物流 =====

    /** 物流信息（拆单场景多条） */
    private List<ShipmentResult> shipments;

    @Data
    public static class ShipmentResult {
        private String trackingNo;
        private String warehouseName;
        private String estimatedDelivery;
    }

    // ===== 风控 =====

    /** 风控结果 */
    private RiskResult riskResult;

    @Data
    public static class RiskResult {
        private boolean passed;
        private String riskLevel;
    }

    // ===== 通知 =====

    /** 通知发送状态 */
    private String notificationStatus;
}
