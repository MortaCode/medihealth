package com.myy.medihealth.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.product.entity.ProductSpec;
import com.myy.medihealth.product.entity.ProductSpecValue;
import com.myy.medihealth.product.mapper.ProductSpecMapper;
import com.myy.medihealth.product.mapper.ProductSpecValueMapper;
import com.myy.medihealth.product.vo.AttrGroupVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品规格参数服务
 */
@Service
@RequiredArgsConstructor
public class ProductSpecService {

    private final ProductSpecMapper specMapper;
    private final ProductSpecValueMapper specValueMapper;

    /**
     * 获取 SPU 的规格参数，按分组聚合
     * 分组逻辑：从 specName 中提取分组前缀（如"主体_品牌" → 分组="主体"）
     */
    public List<AttrGroupVo> getAttrGroupsBySpuId(String spuId) {
        // 查询 SPU 下所有规格值
        List<ProductSpecValue> values = specValueMapper.selectList(
                new LambdaQueryWrapper<ProductSpecValue>()
                        .eq(ProductSpecValue::getSpuId, spuId)
                        .orderByAsc(ProductSpecValue::getSortOrder)
        );

        if (values.isEmpty()) {
            return List.of();
        }

        // 按 specName 前缀分组
        Map<String, List<ProductSpecValue>> grouped = new LinkedHashMap<>();
        for (ProductSpecValue v : values) {
            String groupName = extractGroupName(v.getSpecName());
            grouped.computeIfAbsent(groupName, k -> new ArrayList<>()).add(v);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    AttrGroupVo vo = new AttrGroupVo();
                    vo.setGroupName(entry.getKey());
                    vo.setAttrs(entry.getValue().stream().map(v -> {
                        AttrGroupVo.SpecAttr attr = new AttrGroupVo.SpecAttr();
                        attr.setAttrName(v.getSpecName());
                        attr.setAttrValue(v.getSpecValue());
                        return attr;
                    }).collect(Collectors.toList()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 从规格名提取分组名
     * "主体_品牌" → "主体"
     * "品牌" → "基本"
     */
    private String extractGroupName(String specName) {
        if (specName == null) return "基本";
        int idx = specName.indexOf('_');
        return idx > 0 ? specName.substring(0, idx) : "基本";
    }
}
