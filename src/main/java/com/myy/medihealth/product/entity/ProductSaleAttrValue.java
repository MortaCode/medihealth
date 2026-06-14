package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品销售属性值 — SPU 下某个销售属性有哪些可选值
 * 例如 SPU "布洛芬" 的"规格"属性可选值："20片/盒"、"40片/盒"
 */
@Data
@TableName("t_product_sale_attr_value")
public class ProductSaleAttrValue {

    @TableId
    private String id;

    /** 所属 SPU ID */
    private String spuId;

    /** 所属 SKU ID（该值对应的具体 SKU） */
    private String skuId;

    /** 销售属性 ID */
    private String attrId;

    /** 销售属性名（冗余） */
    private String attrName;

    /** 属性值 */
    private String attrValue;

    /** 排序 */
    private Integer sortOrder;
}
