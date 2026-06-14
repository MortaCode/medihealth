package com.myy.medihealth.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myy.medihealth.product.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * SKU(Product) Mapper
 */
public interface ProductMapper extends BaseMapper<Product> {

    /** 乐观锁库存扣减（条件 UPDATE 保证不超卖） */
    @Update("UPDATE t_medical_product SET stock = stock - #{quantity}, sales = sales + #{quantity} "
            + "WHERE id = #{id} AND stock >= #{quantity}")
    int deductStock(@Param("id") String id, @Param("quantity") int quantity);

    /** 查询 SPU 下所有有效 SKU（按价格升序） */
    @Select("SELECT * FROM t_medical_product WHERE spu_id = #{spuId} AND status = 1 ORDER BY price ASC")
    List<Product> selectSkusBySpuId(@Param("spuId") String spuId);
}
