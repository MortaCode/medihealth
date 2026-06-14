package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类 — 三级类目树
 * 例如：医疗器械(1级) > 血压检测(2级) > 电子血压计(3级)
 */
@Data
@TableName("t_product_category")
public class ProductCategory {

    @TableId
    private String id;

    /** 分类名称 */
    private String name;

    /** 父分类 ID，0 表示根 */
    private String parentId;

    /** 分类层级：1/2/3 */
    private Integer level;

    /** 排序 */
    private Integer sortOrder;

    /** 图标 */
    private String icon;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
