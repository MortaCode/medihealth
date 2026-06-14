package com.myy.medihealth.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 意图类型枚举 - 定义智能医疗助手支持的所有意图分类
 */
@Getter
@AllArgsConstructor
public enum IntentType {
    SYMPTOM_INQUIRY("症状咨询", "用户描述症状，询问可能的疾病、用药建议或就医指导", false),
    DRUG_QUERY("药品查询", "用户查询药品信息、用法用量、禁忌等", false),
    DISEASE_INFO("疾病百科", "用户查询特定疾病的信息、症状、治疗方法", false),
    REGISTER_GUIDE("挂号导诊", "用户需要挂号建议、科室推荐、医院查询", false),
    HEALTH_ADVICE("健康建议", "用户寻求饮食、运动、作息等健康管理建议", true),
    GENERAL_CHAT("一般对话", "用户进行一般性对话或问候", false);

    private final String name;
    private final String description;
    private final boolean composite;
}
