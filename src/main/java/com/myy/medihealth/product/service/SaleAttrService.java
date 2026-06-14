package com.myy.medihealth.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.product.entity.ProductSaleAttr;
import com.myy.medihealth.product.entity.ProductSaleAttrValue;
import com.myy.medihealth.product.mapper.ProductSaleAttrMapper;
import com.myy.medihealth.product.mapper.ProductSaleAttrValueMapper;
import com.myy.medihealth.product.vo.SkuSaleAttrVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售属性服务 — 组合销售属性及其值，用于 SKU 选择面板
 */
@Service
@RequiredArgsConstructor
public class SaleAttrService {

    private final ProductSaleAttrMapper saleAttrMapper;
    private final ProductSaleAttrValueMapper saleAttrValueMapper;

    /**
     * 获取 SPU 的销售属性及可选值列表
     * 例如：[{attrName:"颜色", values:[{value:"白色",skuId:"xxx"}, {value:"黑色",skuId:"yyy"}]}]
     */
    public List<SkuSaleAttrVo> getSaleAttrsBySpuId(String spuId) {
        // 查询该 SPU 下所有 SKU 的销售属性值
        List<ProductSaleAttrValue> values = saleAttrValueMapper.selectList(
                new LambdaQueryWrapper<ProductSaleAttrValue>()
                        .eq(ProductSaleAttrValue::getSpuId, spuId)
                        .orderByAsc(ProductSaleAttrValue::getSortOrder)
        );

        if (values.isEmpty()) {
            return List.of();
        }

        // 按 attrId 分组
        Map<String, List<ProductSaleAttrValue>> grouped = new LinkedHashMap<>();
        for (ProductSaleAttrValue v : values) {
            grouped.computeIfAbsent(v.getAttrId(), k -> new ArrayList<>()).add(v);
        }

        // 查询销售属性名
        Map<String, String> attrNames = new LinkedHashMap<>();
        for (String attrId : grouped.keySet()) {
            ProductSaleAttr attr = saleAttrMapper.selectById(attrId);
            if (attr != null) {
                attrNames.put(attrId, attr.getAttrName());
            }
        }

        List<SkuSaleAttrVo> result = new ArrayList<>();
        for (Map.Entry<String, List<ProductSaleAttrValue>> entry : grouped.entrySet()) {
            SkuSaleAttrVo vo = new SkuSaleAttrVo();
            vo.setAttrId(entry.getKey());
            vo.setAttrName(attrNames.getOrDefault(entry.getKey(), "未知"));
            vo.setValues(entry.getValue().stream().map(v -> {
                SkuSaleAttrVo.AttrValueWithSku av = new SkuSaleAttrVo.AttrValueWithSku();
                av.setAttrValue(v.getAttrValue());
                av.setSkuId(v.getSkuId());
                return av;
            }).toList());
            result.add(vo);
        }
        return result;
    }
}
