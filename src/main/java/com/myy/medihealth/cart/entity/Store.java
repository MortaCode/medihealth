package com.myy.medihealth.cart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺 — 商品归属商家
 *
 * 店铺是结算分组的核心维度：不同店铺必须拆单结算，
 * 各自独立承担运费、优惠和售后责任
 */
@Data
@TableName("t_store")
public class Store {

    @TableId
    private String id;

    /** 店铺名称 */
    private String storeName;

    /** 店铺类型：SELF=自营, THIRD=第三方 */
    private String storeType;

    /** 平台服务费率（如 0.05 = 5%） */
    private BigDecimal serviceFeeRate;

    /** 结算周期（天），如 T+7 */
    private Integer settleCycle;

    /** 客服电话 */
    private String contactPhone;

    /** 状态：0=关闭 1=营业 */
    private Integer status;

    /** 入驻时间 */
    private LocalDateTime createTime;
}
