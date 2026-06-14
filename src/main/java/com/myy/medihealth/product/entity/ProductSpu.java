package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SPU (Standard Product Unit) — 标准化产品单元
 * 例如："欧姆龙电子血压计 HEM-7124" 就是一个 SPU，
 * 它下面有多个 SKU（白色款、黑色款、蓝牙款）
 */
@Data
@TableName("t_product_spu")
public class ProductSpu {

    @TableId
    private String id;

    /** SPU 名称 */
    private String spuName;

    /** 所属分类 ID */
    private String categoryId;

    /** 所属品牌 ID */
    private String brandId;

    /** 主图 */
    private String mainImage;

    /** 上架状态：0=下架, 1=上架 */
    private Integer publishStatus;

    /** 审批状态：0=待审, 1=通过, 2=驳回 */
    private Integer auditStatus;

    /** 重量(g) */
    private Integer weight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
