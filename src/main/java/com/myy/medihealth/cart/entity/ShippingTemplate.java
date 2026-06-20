package com.myy.medihealth.cart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 运费模板 — 按店铺配置的配送计费规则
 *
 * 计费方式：
 *   PIECE   = 按件数（首件X元，续件Y元）
 *   WEIGHT  = 按重量（首重X元/首重kg，续重Y元/kg）
 *   AMOUNT  = 按金额（订单满X元包邮，不满收Y元）
 *   FREE    = 全场包邮
 */
@Data
@TableName("t_shipping_template")
public class ShippingTemplate {

    @TableId
    private String id;

    /** 所属店铺 ID */
    private String storeId;

    /** 模板名称 */
    private String templateName;

    /** 计费方式：PIECE/WEIGHT/AMOUNT/FREE */
    private String chargeType;

    /** 首件/首重数量 */
    private BigDecimal firstUnit;

    /** 首件/首重费用 */
    private BigDecimal firstFee;

    /** 续件/续重单位 */
    private BigDecimal continueUnit;

    /** 续件/续重费用 */
    private BigDecimal continueFee;

    /** 包邮门槛金额（chargeType=AMOUNT时生效，null=不包邮） */
    private BigDecimal freeThreshold;
}
