package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品图片（轮播图 + 详情图）
 * 一个 SKU 可以有多张图片，支持排序和默认图标记
 */
@Data
@TableName("t_product_image")
public class ProductImage {

    @TableId
    private String id;

    /** 所属 SPU ID */
    private String spuId;

    /** 所属 SKU ID（SKU 专属图，null 则为 SPU 通用图） */
    private String skuId;

    /** 图片 URL */
    private String imageUrl;

    /** 排序（越小越靠前） */
    private Integer sortOrder;

    /** 是否为默认主图 */
    private Integer isDefault;
}
