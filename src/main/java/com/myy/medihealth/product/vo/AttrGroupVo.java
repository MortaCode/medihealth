package com.myy.medihealth.product.vo;

import lombok.Data;

import java.util.List;

/**
 * 规格参数分组 VO — 用于前端"规格与包装"面板
 * 例如：
 * 分组名="主体"，属性=[品牌=欧姆龙, 型号=HEM-7124, 颜色=白色]
 * 分组名="功能"，属性=[测量范围=0-299mmHg, 精度=±3mmHg]
 */
@Data
public class AttrGroupVo {

    /** 分组名（从规格名推断，如"主体"、"功能"、"电源"） */
    private String groupName;

    /** 该分组下的属性列表 */
    private List<SpecAttr> attrs;

    @Data
    public static class SpecAttr {

        /** 属性名 */
        private String attrName;

        /** 属性值 */
        private String attrValue;
    }
}
