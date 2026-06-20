package com.myy.medihealth.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户可用优惠券 VO
 */
@Data
public class CouponVo {

    /** 券 ID */
    private String couponId;

    /** 券名称 */
    private String couponName;

    /** 券类型：PLATFORM/STORE/PRODUCT */
    private String couponType;

    /** 优惠方式：FULL_REDUCE/DISCOUNT */
    private String discountType;

    /** 使用门槛 */
    private BigDecimal threshold;

    /** 减免值 */
    private BigDecimal discountValue;

    /** 适用店铺名列表（空=全平台通用） */
    private List<String> applicableStoreNames;

    /** 过期时间描述 */
    private String expireDesc;

    /** 是否可用（当前金额是否满足门槛） */
    private Boolean available;

    /** 不可用原因 */
    private String unavailableReason;
}
