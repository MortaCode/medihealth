package com.myy.medihealth.cart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券 — 用户持有的可抵扣凭证
 *
 * 类型：
 *   PLATFORM = 平台券（全场通用）
 *   STORE    = 店铺券（仅限指定店铺）
 *   PRODUCT  = 商品券（仅限指定商品）
 *
 * 优惠方式：
 *   FULL_REDUCE = 满减（满X元减Y元）
 *   DISCOUNT    = 折扣（满X元打Y折）
 */
@Data
@TableName("t_coupon")
public class Coupon {

    @TableId
    private String id;

    /** 持有用户 ID */
    private String userId;

    /** 券类型：PLATFORM/STORE/PRODUCT */
    private String couponType;

    /** 优惠方式：FULL_REDUCE/DISCOUNT */
    private String discountType;

    /** 使用门槛金额 */
    private BigDecimal threshold;

    /** 减免金额（FULL_REDUCE）或折扣值如 0.85（DISCOUNT） */
    private BigDecimal discountValue;

    /** 适用店铺 ID 列表（JSON 数组，PLATFORM券=空） */
    private String applicableStoreIds;

    /** 适用商品 ID 列表（JSON 数组，PRODUCT券专用） */
    private String applicableProductIds;

    /** 优惠券名称 */
    private String couponName;

    /** 生效时间 */
    private LocalDateTime startTime;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否已使用：0=未用 1=已用 */
    private Integer used;

    /** 领取时间 */
    private LocalDateTime createTime;
}
