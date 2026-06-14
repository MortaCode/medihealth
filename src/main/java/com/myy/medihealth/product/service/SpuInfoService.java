package com.myy.medihealth.product.service;

import com.myy.medihealth.product.entity.ProductSpu;
import com.myy.medihealth.product.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SPU 信息服务
 */
@Service
@RequiredArgsConstructor
public class SpuInfoService {

    private final ProductSpuMapper spuMapper;

    public ProductSpu getById(String spuId) {
        return spuMapper.selectById(spuId);
    }
}
