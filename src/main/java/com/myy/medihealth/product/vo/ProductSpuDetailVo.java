package com.myy.medihealth.product.vo;

import com.myy.medihealth.product.entity.ProductBrand;
import com.myy.medihealth.product.entity.ProductCategory;
import com.myy.medihealth.product.entity.ProductDesc;
import com.myy.medihealth.product.entity.ProductImage;
import com.myy.medihealth.product.entity.ProductSpu;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * SPU 详情页聚合 VO — 对应京东/淘宝商品详情页全部信息
 *
 * 展示内容包括：SPU 信息、品牌、分类、所有 SKU 变体、
 * 图片轮播、富文本描述、规格参数分组、销售属性选择面板
 */
@Data
public class ProductSpuDetailVo {

    // ===== SPU 核心信息 =====

    /** SPU 基本信息 */
    private ProductSpu spuInfo;

    /** 品牌信息 */
    private ProductBrand brand;

    /** 分类信息 */
    private ProductCategory category;

    // ===== SKU 变体列表 =====

    /** 该 SPU 下所有有效 SKU（按价格排序） */
    private List<SkuBriefVo> skus;

    // ===== 图片 =====

    /** 所有图片（SPU 通用图 + 各 SKU 专属图） */
    private List<ProductImage> images;

    // ===== 描述 =====

    /** SPU 描述（富文本 HTML + JSON 规格 + 售后说明） */
    private ProductDesc description;

    // ===== 规格参数 =====

    /** 规格参数分组（技术参数面板） */
    private List<AttrGroupVo> attrGroups;

    // ===== 销售属性 =====

    /** 销售属性组合（SKU 变体选择面板） */
    private List<SkuSaleAttrVo> saleAttrs;

    // ===== 内嵌类 =====

    /**
     * SKU 简要信息 — 嵌入 SPU 详情页的 SKU 列表
     */
    @Data
    public static class SkuBriefVo {

        /** SKU ID */
        private String skuId;

        /** SKU 名称 */
        private String skuName;

        /** 售价 */
        private BigDecimal price;

        /** 库存（-1=无限） */
        private Integer stock;

        /** 状态：0=下架 1=上架 */
        private Integer status;

        /** 是否需要处方 */
        private Integer prescriptionRequired;

        /** SKU 主图 */
        private String image;

        /** 销量 */
        private Integer sales;

        /** 该 SKU 的销售属性键值对，如 {"颜色":"白色", "规格":"20粒/盒"} */
        private Map<String, String> saleAttrMap;
    }
}
