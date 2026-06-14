package com.myy.medihealth.chat.mcp.vo;

import java.util.List;

/**
 * 医疗MCP工具的值对象集合
 * 包含药品查询、疾病百科、医院挂号、健康建议的所有VO
 */
public class MedicalVO {

    // ======================== Drug Query ========================

    /**
     * 药品查询请求
     */
    public record DrugRequest(String drugName, String symptom) {}

    /**
     * 药品信息项
     */
    public record DrugItem(String name, String genericName, String category, String usage,
                           String dosage, String sideEffects, String contraindications,
                           String precautions, boolean prescriptionRequired, String priceRange) {}

    /**
     * 药品查询响应
     */
    public record DrugResponse(String query, List<DrugItem> drugs, String disclaimer) {}

    // ======================== Disease Info ========================

    /**
     * 疾病查询请求
     */
    public record DiseaseRequest(String diseaseName, String symptom) {}

    /**
     * 疾病信息
     */
    public record DiseaseInfo(String name, String overview, List<String> symptoms,
                              List<String> causes, List<String> treatments,
                              List<String> preventions, String department, String severity) {}

    /**
     * 疾病查询响应
     */
    public record DiseaseResponse(String query, List<DiseaseInfo> diseases, String disclaimer) {}

    // ======================== Hospital Register ========================

    /**
     * 医院查询请求
     */
    public record HospitalRequest(String city, String department, String symptom) {}

    /**
     * 医院信息项
     */
    public record HospitalItem(String name, String address, String phone, String level,
                               String department, boolean hasEmergency, double rating,
                               int queueCount) {}

    /**
     * 医院查询响应
     */
    public record HospitalResponse(String city, String department, List<HospitalItem> hospitals,
                                    String tip) {}

    // ======================== Health Advice ========================

    /**
     * 健康建议请求
     */
    public record HealthAdviceRequest(String symptom, String goal, String age, String gender) {}

    /**
     * 健康建议段落
     */
    public record AdviceSection(String category, String title, List<String> items) {}

    /**
     * 健康建议响应
     */
    public record HealthAdviceResponse(String title, String overview, List<AdviceSection> sections,
                                        List<String> warnings, String disclaimer, String generatedAt) {}
}
