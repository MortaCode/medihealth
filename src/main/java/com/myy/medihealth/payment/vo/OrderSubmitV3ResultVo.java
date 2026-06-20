package com.myy.medihealth.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * V3 下单结果 — 按店铺拆单后的聚合返回
 */
@Data
public class OrderSubmitV3ResultVo {

    /** 支付总金额（所有店铺订单之和） */
    private BigDecimal totalPayAmount;

    /** 按店铺拆分的订单列表 */
    private List<StoreOrderResult> storeOrders;

    /** 积分汇总 */
    private PaymentResultVo.PointsResult pointsResult;

    /** 风控总结果 */
    private PaymentResultVo.RiskResult riskResult;

    /** 通知状态 */
    private String notificationStatus;

    // ---- 内嵌 ----

    @Data
    public static class StoreOrderResult {
        /** 订单号 */
        private String orderId;

        /** 店铺 ID */
        private String storeId;

        /** 店铺名称 */
        private String storeName;

        /** 店铺类型（SELF/THIRD） */
        private String storeType;

        /** 售后责任方 */
        private String afterSaleResponsible;

        /** 商品原价合计 */
        private BigDecimal itemAmount;

        /** 运费 */
        private BigDecimal shippingFee;

        /** 优惠金额 */
        private BigDecimal discount;

        /** 实付金额 */
        private BigDecimal actualAmount;

        /** 含处方药 */
        private Boolean hasPrescription;

        /** 订单过期时间 */
        private String expireTime;

        /** 已消耗的优惠券 */
        private List<PaymentResultVo.ConsumedCoupon> consumedCoupons;

        /** 物流信息 */
        private List<PaymentResultVo.ShipmentResult> shipments;
    }
}
