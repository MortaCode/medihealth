package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * SPU 描述 — 富文本详情 + JSON 规格参数
 */
@Data
@TableName("t_product_desc")
public class ProductDesc {

    @TableId
    private String spuId;

    /** 商品详情（HTML 富文本，PC端渲染） */
    private String description;

    /** 商品规格参数（JSON 格式，按分组组织的规格列表） */
    private String specifications;

    /** 售后服务说明 */
    private String afterSale;
}
