package com.myy.medihealth.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 店铺结算分组 — 同一店铺的商品归为一组独立结算
 *
 * 每组包含：商品明细、小计、运费、店铺活动优惠、平台分摊优惠、券抵扣
 */
@Data
public class CartGroupVo {

    /** 店铺 ID */
    private String storeId;

    /** 店铺名称 */
    private String storeName;

    /** 店铺类型：SELF=自营, THIRD=第三方 */
    private String storeType;

    /** 该组商品明细 */
    private List<GroupItem> items;

    /** 商品小计（原价 × 数量） */
    private BigDecimal itemAmount;

    /** 运费 */
    private BigDecimal shippingFee;

    /** 可选的配送仓库及运费方案 */
    private List<ShippingOptionVo> shippingOptions;

    /** 店铺级优惠（满减/满折） */
    private DiscountDetail storePromotion;

    /** 分摊的平台级优惠 */
    private DiscountDetail platformPromotion;

    /** 优惠券抵扣 */
    private DiscountDetail couponDiscount;

    /** 该组合计 = itemAmount - 总优惠 + shippingFee */
    private BigDecimal groupTotal;

    /** 售后责任方（店铺名称 + 客服电话） */
    private String afterSaleResponsible;

    // ---- 内嵌类 ----

    @Data
    public static class GroupItem {
        private String cartItemId;
        private String productId;
        private String productName;
        private String productImage;
        private BigDecimal price;
        private Integer quantity;
        private Integer prescriptionRequired;
        private String warehouseId;
        private String warehouseName;
    }

    @Data
    public static class DiscountDetail {
        private String label;          // 如 "满200减30"
        private BigDecimal amount;     // 优惠金额
    }
}
