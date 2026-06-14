package com.myy.medihealth.product.vo;

import com.myy.medihealth.product.entity.*;
import lombok.Data;

import java.util.List;

/**
 * 商品详情页聚合 VO
 * 聚合 8+ 数据源，由 CompletableFuture 并行组装
 */
@Data
public class ProductDetailVo {

    // ===== 异步独立获取 =====

    /** 1. SKU 基本信息 */
    private Product skuInfo;

    /** 2. SKU 图片列表（轮播图） */
    private List<ProductImage> images;

    // ===== 依赖 SKU → SPU 信息 =====

    /** 3. SPU 基本信息（名称、品牌ID等） */
    private ProductSpu spuInfo;

    /** 4. 品牌信息 */
    private ProductBrand brand;

    /** 5. 分类信息 */
    private ProductCategory category;

    /** 6. SPU 描述（富文本详情 + JSON 规格） */
    private ProductDesc description;

    /** 7. 销售属性组合（SKU 选择面板用） */
    private List<SkuSaleAttrVo> saleAttrs;

    /** 8. 规格参数分组（技术参数面板用） */
    private List<AttrGroupVo> attrGroups;
}
