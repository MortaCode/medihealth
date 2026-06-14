package com.myy.medihealth.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPU 列表条目 VO — 对应京东/淘宝列表页的每个商品卡
 *
 * 每个 SPU 在列表中展示为一张卡片：
 *   主图 + 名称 + 品牌 + 价格区间 + 销量 + 变体标签（如"3色可选"）
 */
@Data
public class ProductSpuListVo {

    /** SPU ID */
    private String spuId;

    /** SPU 名称 */
    private String spuName;

    /** 品牌名称（JOIN 自 t_product_brand） */
    private String brandName;

    /** 分类名称（JOIN 自 t_product_category） */
    private String categoryName;

    /** 主图 URL */
    private String mainImage;

    /** 最低售价（该 SPU 下所有 SKU 的最低价） */
    private BigDecimal minPrice;

    /** 最高售价（该 SPU 下所有 SKU 的最高价） */
    private BigDecimal maxPrice;

    /** 总销量（该 SPU 下所有 SKU 销量之和） */
    private Integer totalSales;

    /** SKU 数量（该 SPU 下有多少个有效 SKU） */
    private Integer skuCount;

    /** 变体标签列表（如 ["白色","黑色","红色"]），用于列表快速预览 */
    private List<String> saleAttrTags;
}
