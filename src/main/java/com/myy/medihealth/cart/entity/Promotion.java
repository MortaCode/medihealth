package com.myy.medihealth.cart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 促销活动 — 店铺/平台发起的限时优惠
 *
 * 活动类型：
 *   FULL_REDUCE    = 满减（如"满200减30"、"满500减80"）
 *   FULL_DISCOUNT  = 满折（如"满2件打8折"）
 *   FULL_GIFT      = 满赠（如"满300赠试纸"）
 *   N_PER_N_DISCOUNT = N件N折（如"3件7折"）
 *
 * 叠加规则：
 *   平台满减 + 店铺满减 = 可叠加
 *   店铺满减 + 店铺折扣 = 取最优（不叠加）
 *   满减 + 优惠券 = 先满减再券抵扣
 */
@Data
@TableName("t_promotion")
public class Promotion {

    @TableId
    private String id;

    /** 活动类型：FULL_REDUCE/FULL_DISCOUNT/FULL_GIFT/N_PER_N_DISCOUNT */
    private String promotionType;

    /** 所属店铺 ID（null=平台活动） */
    private String storeId;

    /** 活动名称 */
    private String promotionName;

    /**
     * 规则 JSON，支持阶梯：
     * [{"threshold":200,"value":30},{"threshold":500,"value":80}]
     * threshold: 门槛（金额或件数），value: 减免/折扣值
     */
    private String rules;

    /** 是否可叠加：0=不可 1=可叠加 */
    private Integer stacking;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 状态：0=停用 1=启用 */
    private Integer status;
}
