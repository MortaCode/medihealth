package com.myy.medihealth.product.service;

import com.myy.medihealth.product.entity.ProductBrand;
import com.myy.medihealth.product.mapper.ProductBrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 品牌服务
 */
@Service
@RequiredArgsConstructor
public class BrandService {

    private final ProductBrandMapper brandMapper;

    public ProductBrand getById(String brandId) {
        return brandMapper.selectById(brandId);
    }
}
