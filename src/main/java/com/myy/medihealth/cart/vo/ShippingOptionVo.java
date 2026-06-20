package com.myy.medihealth.cart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 配送方式选项 — 一个结算分组的可选物流方案
 */
@Data
public class ShippingOptionVo {

    /** 发货仓库 ID */
    private String warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 运费（元） */
    private BigDecimal shippingFee;

    /** 预计配送天数 */
    private Integer estimatedDays;

    /** 是否默认推荐 */
    private Boolean recommended;
}
