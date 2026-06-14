package com.myy.medihealth.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.product.entity.ProductImage;
import com.myy.medihealth.product.mapper.ProductImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SKU 图片服务
 */
@Service
@RequiredArgsConstructor
public class SkuImageService {

    private final ProductImageMapper imageMapper;

    /**
     * 获取 SKU 的所有图片（按排序升序）
     */
    public List<ProductImage> getImagesBySkuId(String skuId) {
        return imageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getSkuId, skuId)
                        .orderByAsc(ProductImage::getSortOrder)
        );
    }

    /**
     * 获取 SPU 的通用图片
     */
    public List<ProductImage> getImagesBySpuId(String spuId) {
        return imageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getSpuId, spuId)
                        .isNull(ProductImage::getSkuId)
                        .orderByAsc(ProductImage::getSortOrder)
        );
    }
}
