package com.myy.medihealth.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单提交 V3 请求 — 结算页 → 下单全链路
 *
 * 包含：购物车结算结果 + 优惠券选择 + 收货地址 + 支付方式
 */
@Data
public class OrderSubmitV3Vo {

    /** 幂等请求 ID（客户端生成，防重复提交） */
    private String requestId;

    /** 收货地址 ID */
    private String addressId;

    /** 收货人 */
    private String receiverName;

    /** 收货电话 */
    private String receiverPhone;

    /** 收货地址（省/市/区/详细） */
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetail;

    /** 支付方式：WECHAT/ALIPAY */
    private String payChannel;

    /** 使用的优惠券 ID 列表 */
    private List<String> couponIds;

    /** 使用的积分数 */
    private int usePoints;

    /** 买家留言 */
    private String buyerNote;

    /** 发票信息（可选） */
    private InvoiceInfo invoiceInfo;

    @Data
    public static class InvoiceInfo {
        /** 发票类型：PERSONAL/COMPANY */
        private String invoiceType;
        /** 发票抬头 */
        private String invoiceTitle;
        /** 税号（企业发票必填） */
        private String taxNumber;
    }
}
