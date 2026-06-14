package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品品牌
 * 例如：欧姆龙、鱼跃、三诺、罗氏
 */
@Data
@TableName("t_product_brand")
public class ProductBrand {

    @TableId
    private String id;

    /** 品牌名称 */
    private String name;

    /** 品牌 LOGO URL */
    private String logo;

    /** 品牌简介 */
    private String description;

    /** 首字母（用于索引） */
    private String initialLetter;

    /** 排序 */
    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
