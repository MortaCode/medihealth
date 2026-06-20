package com.myy.medihealth.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 拆单建议 — 根据业务规则将结算分组拆分为独立订单
 *
 * 拆单规则：
 *   1. 不同店铺必须拆单
 *   2. 不同仓库按距离决定合/拆（同城合并，跨城分拆）
 *   3. 含处方药单独成单
 *   4. 预售商品单独成单
 *   5. 特殊商品（冷链/超重/超大件）单独成单
 */
@Data
public class SplitOrderVo {

    /** 建议订单编号（展示用） */
    private String suggestOrderNo;

    /** 拆单原因 */
    private String splitReason;

    /** 所属店铺 ID */
    private String storeId;

    /** 店铺名称 */
    private String storeName;

    /** 商品明细 */
    private List<SplitOrderItem> items;

    /** 商品金额 */
    private BigDecimal itemAmount;

    /** 运费 */
    private BigDecimal shippingFee;

    /** 优惠金额 */
    private BigDecimal discount;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 售后责任方 */
    private String afterSaleResponsible;

    /** 含处方药标记 */
    private Boolean hasPrescription;

    /** 预计送达时间 */
    private String estimatedDelivery;

    @Data
    public static class SplitOrderItem {
        private String productId;
        private String productName;
        private BigDecimal price;
        private Integer quantity;
        private String warehouseName;
    }
}
