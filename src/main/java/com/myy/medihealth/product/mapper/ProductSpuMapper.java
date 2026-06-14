package com.myy.medihealth.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myy.medihealth.product.entity.ProductSpu;
import com.myy.medihealth.product.vo.ProductSpuListVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SPU Mapper — 含列表聚合 SQL（JOIN 品牌/分类/SKU MIN/MAX/SUM）
 */
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {

    @Select("""
        <script>
        SELECT spu.id AS spuId, spu.spu_name AS spuName, spu.main_image AS mainImage,
               b.name AS brandName, c.name AS categoryName,
               MIN(sku.price) AS minPrice, MAX(sku.price) AS maxPrice,
               COALESCE(SUM(sku.sales), 0) AS totalSales,
               COUNT(CASE WHEN sku.status = 1 THEN 1 END) AS skuCount
        FROM t_product_spu spu
        LEFT JOIN t_product_brand b ON spu.brand_id = b.id
        LEFT JOIN t_product_category c ON spu.category_id = c.id
        LEFT JOIN t_medical_product sku ON sku.spu_id = spu.id
        WHERE spu.publish_status = 1
          <if test='categoryId != null and categoryId != ""'>
            AND spu.category_id = #{categoryId}
          </if>
          <if test='brandId != null and brandId != ""'>
            AND spu.brand_id = #{brandId}
          </if>
          <if test='keyword != null and keyword != ""'>
            AND spu.spu_name LIKE CONCAT('%', #{keyword}, '%')
          </if>
        GROUP BY spu.id, spu.spu_name, spu.main_image, b.name, c.name
        <choose>
          <when test='sort == "sales"'>
            ORDER BY totalSales DESC
          </when>
          <when test='sort == "price_asc"'>
            ORDER BY minPrice ASC
          </when>
          <when test='sort == "price_desc"'>
            ORDER BY minPrice DESC
          </when>
          <otherwise>
            ORDER BY spu.create_time DESC
          </otherwise>
        </choose>
        </script>
    """)
    IPage<ProductSpuListVo> selectSpuPage(
            Page<ProductSpuListVo> page,
            @Param("categoryId") String categoryId,
            @Param("brandId") String brandId,
            @Param("keyword") String keyword,
            @Param("sort") String sort);
}
