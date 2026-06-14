package com.myy.medihealth.product.service;

import com.myy.medihealth.product.entity.ProductCategory;
import com.myy.medihealth.product.mapper.ProductCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 分类服务
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryMapper categoryMapper;

    public ProductCategory getById(String categoryId) {
        return categoryMapper.selectById(categoryId);
    }
}
