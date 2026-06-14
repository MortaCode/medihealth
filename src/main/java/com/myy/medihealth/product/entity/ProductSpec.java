package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品规格参数 — 定义 SPU 有哪些规格属性
 * 例如血氧仪："测量范围"、"精度"、"电池类型"、"重量"
 */
@Data
@TableName("t_product_spec")
public class ProductSpec {

    @TableId
    private String id;

    /** 规格属性名，如"测量范围" */
    private String specName;

    /** 所属分类 ID（分类级规格模板） */
    private String categoryId;

    /** 属性类型：0=规格参数, 1=销售属性 */
    private Integer attrType;

    /** 是否支持搜索 */
    private Integer searchable;

    /** 排序 */
    private Integer sortOrder;
}
