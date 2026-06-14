package com.myy.medihealth.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品上架/更新表单 VO — 涵盖 SPU + SKU + 图片 + 规格 + 销售属性 + 描述
 *
 * 一次提交完成全链路写入，对标京东/淘宝商家后台商品发布表单
 */
@Data
public class ProductCreateVo {

    // ===== SPU 级字段 =====

    /** SPU 名称 */
    private String spuName;

    /** 所属分类 ID（关联 t_product_category） */
    private String categoryId;

    /** 所属品牌 ID（关联 t_product_brand） */
    private String brandId;

    /** SPU 主图 */
    private String mainImage;

    /** 重量（克） */
    private Integer weight;

    // ===== 子资源列表 =====

    /** SKU 列表（至少 1 个） */
    private List<SkuItem> skus;

    /** 图片列表 */
    private List<ImageItem> images;

    /** 规格参数值列表 */
    private List<SpecValueItem> specValues;

    /** 描述信息 */
    private DescItem description;

    // ===== 内嵌类 =====

    @Data
    public static class SkuItem {

        /** SKU 名称 */
        private String skuName;

        /** 售价 */
        private BigDecimal price;

        /** 库存（-1=无限） */
        private Integer stock;

        /** SKU 主图 */
        private String imageUrl;

        /** 是否需要处方：0=否 1=是 */
        private Integer prescriptionRequired;

        /** 附加上图 URL 列表 */
        private List<String> additionalImages;

        /** 该 SKU 的销售属性，如 [{attrId:"sale_attr_01", attrName:"颜色", attrValue:"白色"}] */
        private List<SaleAttrEntry> saleAttrs;
    }

    @Data
    public static class SaleAttrEntry {

        /** 销售属性定义 ID（关联 t_product_sale_attr） */
        private String attrId;

        /** 销售属性名（冗余，如"颜色"） */
        private String attrName;

        /** 属性值（如"白色"） */
        private String attrValue;

        /** 排序 */
        private Integer sortOrder;
    }

    @Data
    public static class ImageItem {

        /** 所属 SKU ID（null=SPU 通用图） */
        private String skuId;

        /** 图片 URL */
        private String imageUrl;

        /** 排序（越小越靠前） */
        private Integer sortOrder;

        /** 是否默认主图 */
        private Integer isDefault;
    }

    @Data
    public static class SpecValueItem {

        /** 规格属性定义 ID（关联 t_product_spec） */
        private String specId;

        /** 规格属性名（冗余，如"功能_测量范围"） */
        private String specName;

        /** 规格值（如"0-299mmHg"） */
        private String specValue;

        /** 排序 */
        private Integer sortOrder;
    }

    @Data
    public static class DescItem {

        /** 商品详情（HTML 富文本） */
        private String description;

        /** 规格参数（JSON 格式） */
        private String specifications;

        /** 售后服务说明 */
        private String afterSale;
    }
}
