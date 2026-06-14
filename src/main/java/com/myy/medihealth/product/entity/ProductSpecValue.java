package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品规格值 — SPU 级别的规格参数取值
 * 例如 SPU "欧姆龙血氧仪" 的 "测量范围" = "0%~100%"
 */
@Data
@TableName("t_product_spec_value")
public class ProductSpecValue {

    @TableId
    private String id;

    /** 所属 SPU ID */
    private String spuId;

    /** 规格属性 ID */
    private String specId;

    /** 规格属性名（冗余，便于查询） */
    private String specName;

    /** 规格值 */
    private String specValue;

    /** 排序 */
    private Integer sortOrder;
}
