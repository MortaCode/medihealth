package com.myy.medihealth.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车结算预览主结果
 *
 * 聚合：按店铺分组 → 运费计算 → 优惠匹配 → 拆单建议
 */
@Data
public class CheckoutResultVo {

    /** 商品总金额（原价合计） */
    private BigDecimal totalItemAmount;

    /** 总优惠金额（店铺活动 + 平台活动 + 优惠券） */
    private BigDecimal totalDiscount;

    /** 总运费 */
    private BigDecimal totalShippingFee;

    /** 实付金额 = totalItemAmount - totalDiscount + totalShippingFee */
    private BigDecimal actualAmount;

    /** 按店铺分组的结算明细 */
    private List<CartGroupVo> groups;

    /** 拆单建议（根据商品属性/仓库/店铺规则拆分订单） */
    private List<SplitOrderVo> splitOrders;

    /** 不可购买的商品列表（下架/库存不足） */
    private List<UnavailableItem> unavailableItems;

    @Data
    public static class UnavailableItem {
        private String productId;
        private String productName;
        private String reason;
    }
}
