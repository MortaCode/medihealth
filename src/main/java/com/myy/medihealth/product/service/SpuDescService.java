package com.myy.medihealth.product.service;

import com.myy.medihealth.product.entity.ProductDesc;
import com.myy.medihealth.product.mapper.ProductDescMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SPU 描述服务
 */
@Service
@RequiredArgsConstructor
public class SpuDescService {

    private final ProductDescMapper descMapper;

    public ProductDesc getBySpuId(String spuId) {
        return descMapper.selectById(spuId);
    }
}
