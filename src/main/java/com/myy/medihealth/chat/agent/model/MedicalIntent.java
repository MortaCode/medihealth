package com.myy.medihealth.chat.agent.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 医疗意图识别结果 - 封装意图类型、置信度、实体提取等信息
 * 不可变设计，所有修改操作返回新实例
 */
public class MedicalIntent {

    /** 实体键常量 */
    public static final String SYMPTOM = "symptom";
    public static final String BODY_PART = "bodyPart";
    public static final String DURATION = "duration";
    public static final String DRUG_NAME = "drugName";
    public static final String DISEASE_NAME = "diseaseName";
    public static final String CITY = "city";
    public static final String DEPARTMENT = "department";
    public static final String AGE = "age";
    public static final String GENDER = "gender";
    public static final String SEVERITY = "severity";

    private final IntentType type;
    private final double confidence;
    private final Map<String, String> entities;
    private final String originalQuery;
    private final String reasoning;

    private MedicalIntent(IntentType type, double confidence, Map<String, String> entities,
                          String originalQuery, String reasoning) {
        this.type = type;
        this.confidence = confidence;
        this.entities = Collections.unmodifiableMap(new HashMap<>(entities));
        this.originalQuery = originalQuery;
        this.reasoning = reasoning;
    }

    /**
     * 静态工厂方法
     */
    public static MedicalIntent of(IntentType type, double confidence,
                                   String originalQuery, String reasoning) {
        return new MedicalIntent(type, confidence, new HashMap<>(),
                originalQuery, reasoning);
    }

    /**
     * 添加单个实体（不可变，返回新实例）
     */
    public MedicalIntent withEntity(String key, String value) {
        Map<String, String> newEntities = new HashMap<>(this.entities);
        newEntities.put(key, value);
        return new MedicalIntent(this.type, this.confidence, newEntities,
                this.originalQuery, this.reasoning);
    }

    /**
     * 批量添加实体（不可变，返回新实例）
     */
    public MedicalIntent withEntities(Map<String, String> additionalEntities) {
        Map<String, String> newEntities = new HashMap<>(this.entities);
        newEntities.putAll(additionalEntities);
        return new MedicalIntent(this.type, this.confidence, newEntities,
                this.originalQuery, this.reasoning);
    }

    /**
     * 获取指定键的实体值
     */
    public String getEntity(String key) {
        return entities.get(key);
    }

    /**
     * 检查是否包含指定键的实体
     */
    public boolean hasEntity(String key) {
        return entities.containsKey(key);
    }

    // ---- Getters ----

    public IntentType getType() {
        return type;
    }

    public double getConfidence() {
        return confidence;
    }

    public Map<String, String> getEntities() {
        return entities;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public String getReasoning() {
        return reasoning;
    }

    @Override
    public String toString() {
        return "MedicalIntent{" +
                "type=" + type +
                ", confidence=" + confidence +
                ", entities=" + entities +
                ", originalQuery='" + originalQuery + '\'' +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }
}
