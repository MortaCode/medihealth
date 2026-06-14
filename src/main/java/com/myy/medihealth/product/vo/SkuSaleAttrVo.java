package com.myy.medihealth.product.vo;

import lombok.Data;

import java.util.List;

/**
 * SKU 销售属性 VO — 用于前端 SKU 选择面板
 * 例如：属性名=颜色，可选值=[白色, 黑色, 蓝色]
 */
@Data
public class SkuSaleAttrVo {

    /** 属性 ID */
    private String attrId;

    /** 属性名（如"颜色"、"规格"） */
    private String attrName;

    /** 该属性下的所有可选值 */
    private List<AttrValueWithSku> values;

    @Data
    public static class AttrValueWithSku {

        /** 属性值（如"白色"） */
        private String attrValue;

        /** 该值对应的 SKU ID（用于切换 SKU） */
        private String skuId;
    }
}
