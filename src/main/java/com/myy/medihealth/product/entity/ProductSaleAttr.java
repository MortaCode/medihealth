package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品销售属性 — 定义 SKU 的变体维度
 * 例如：颜色、规格（片/盒）、剂型
 */
@Data
@TableName("t_product_sale_attr")
public class ProductSaleAttr {

    @TableId
    private String id;

    /** 销售属性名，如"颜色"、"规格" */
    private String attrName;

    /** 所属分类 ID */
    private String categoryId;

    /** 排序 */
    private Integer sortOrder;
}
