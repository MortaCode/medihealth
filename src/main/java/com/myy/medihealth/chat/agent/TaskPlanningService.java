package com.myy.medihealth.chat.agent;

import com.myy.medihealth.chat.agent.model.AgentPlan;
import com.myy.medihealth.chat.agent.model.AgentTask;
import com.myy.medihealth.chat.agent.model.MedicalIntent;
import com.myy.medihealth.chat.agent.model.AgentTask.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 任务规划服务 - 根据识别的医疗意图编排 Agent 执行计划
 * 为不同意图类型生成相应的工具调用任务序列
 */
@Slf4j
@Service
public class TaskPlanningService {

    /**
     * 根据医疗意图生成 Agent 执行计划
     *
     * @param intent 识别的医疗意图
     * @return 包含任务序列和执行推理的AgentPlan
     */
    public AgentPlan plan(MedicalIntent intent) {
        if (intent == null) {
            return AgentPlan.builder()
                    .intent(null)
                    .tasks(Collections.emptyList())
                    .reasoning("未识别的意图，不生成任务计划")
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        List<AgentTask> tasks;
        String reasoning;

        switch (intent.getType()) {
            case HEALTH_ADVICE:
                // 复合意图：多步骤任务
                tasks = buildHealthAdviceTasks(intent);
                reasoning = buildHealthAdviceReasoning(intent);
                break;
            case SYMPTOM_INQUIRY:
                // 单步骤：症状分析
                tasks = buildSymptomInquiryTasks(intent);
                reasoning = "用户咨询症状，启动症状分析→疾病查询→健康建议单步流程";
                break;
            case DRUG_QUERY:
                // 单步骤：药品查询
                tasks = buildDrugQueryTasks(intent);
                reasoning = "用户查询药品信息，启动药品查询工具";
                break;
            case DISEASE_INFO:
                // 单步骤：疾病百科
                tasks = buildDiseaseInfoTasks(intent);
                reasoning = "用户查询疾病百科，启动疾病信息查询工具";
                break;
            case REGISTER_GUIDE:
                // 多步骤：挂号导诊
                tasks = buildRegisterGuideTasks(intent);
                reasoning = buildRegisterReasoning(intent);
                break;
            case GENERAL_CHAT:
            default:
                // 一般对话：无工具调用
                tasks = Collections.emptyList();
                reasoning = "一般性对话，无需调用医疗工具，直接由LLM回答";
                break;
        }

        AgentPlan plan = AgentPlan.builder()
                .intent(intent)
                .tasks(tasks)
                .reasoning(reasoning)
                .createdAt(LocalDateTime.now())
                .build();

        log.info("任务计划生成: intent={}, taskCount={}, reasoning={}",
                intent.getType().getName(), tasks.size(), reasoning);

        return plan;
    }

    /**
     * 构建健康建议多步骤任务（复合意图）
     * Step 1: symptomAnalysis → Step 2: diseaseInfo → Step 3: drugQuery → Step 4: healthAdvice
     */
    private List<AgentTask> buildHealthAdviceTasks(MedicalIntent intent) {
        List<AgentTask> tasks = new ArrayList<>();

        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);
        String symptomParam = symptom != null ? symptom : "综合健康评估";
        String ageParam = intent.getEntity(MedicalIntent.AGE);
        String genderParam = intent.getEntity(MedicalIntent.GENDER);

        // Step 1: 症状分析
        AgentTask step1 = AgentTask.builder()
                .id("task-1")
                .description("分析症状")
                .toolName("diseaseInfo")
                .toolPurpose("根据症状查询可能的疾病，了解疾病的基本信息")
                .order(0)
                .dependencies(Collections.emptyList())
                .params(buildParams("symptom", symptomParam))
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        // Step 2: 疾病信息
        AgentTask step2 = AgentTask.builder()
                .id("task-2")
                .description("基于症状查询相关疾病详情")
                .toolName("diseaseInfo")
                .toolPurpose("查询第一步分析出的相关疾病的详细信息，包括病因、治疗和预防")
                .order(1)
                .dependencies(List.of("task-1"))
                .params(buildParams("symptom", symptomParam))
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        // Step 3: 用药建议（依赖Step1）
        AgentTask step3 = AgentTask.builder()
                .id("task-3")
                .description("用药建议")
                .toolName("drugQuery")
                .toolPurpose("根据症状和疾病信息查询相关药品，提供用药指导")
                .order(2)
                .dependencies(List.of("task-1"))
                .params(buildParams("symptom", symptomParam))
                .required(false)
                .status(TaskStatus.PENDING)
                .build();

        // Step 4: 健康建议（依赖前3步）
        AgentTask step4 = AgentTask.builder()
                .id("task-4")
                .description("生成综合健康建议")
                .toolName("healthAdvice")
                .toolPurpose("整合症状分析、疾病信息和用药建议，生成个性化综合健康管理方案")
                .order(3)
                .dependencies(List.of("task-1", "task-2", "task-3"))
                .params(buildHealthAdviceParams(intent))
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        tasks.add(step1);
        tasks.add(step2);
        tasks.add(step3);
        tasks.add(step4);

        return tasks;
    }

    /**
     * 构建症状咨询单步任务
     */
    private List<AgentTask> buildSymptomInquiryTasks(MedicalIntent intent) {
        List<AgentTask> tasks = new ArrayList<>();

        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);
        String bodyPart = intent.getEntity(MedicalIntent.BODY_PART);

        String querySymptom = buildSymptomQuery(symptom, bodyPart);

        AgentTask task = AgentTask.builder()
                .id("task-1")
                .description("症状分析咨询")
                .toolName("diseaseInfo")
                .toolPurpose("根据用户描述的症状查询可能的疾病和健康建议")
                .order(0)
                .dependencies(Collections.emptyList())
                .params(buildParams("symptom", querySymptom))
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        tasks.add(task);
        return tasks;
    }

    /**
     * 构建药品查询单步任务
     */
    private List<AgentTask> buildDrugQueryTasks(MedicalIntent intent) {
        List<AgentTask> tasks = new ArrayList<>();

        String drugName = intent.getEntity(MedicalIntent.DRUG_NAME);
        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);

        Map<String, String> params = new HashMap<>();
        if (drugName != null) params.put("drugName", drugName);
        if (symptom != null) params.put("symptom", symptom);
        if (params.isEmpty()) params.put("drugName", intent.getOriginalQuery());

        AgentTask task = AgentTask.builder()
                .id("task-1")
                .description("药品信息查询")
                .toolName("drugQuery")
                .toolPurpose("查询药品的详细信息，包括用法用量、副作用、禁忌和注意事项")
                .order(0)
                .dependencies(Collections.emptyList())
                .params(params)
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        tasks.add(task);
        return tasks;
    }

    /**
     * 构建疾病百科单步任务
     */
    private List<AgentTask> buildDiseaseInfoTasks(MedicalIntent intent) {
        List<AgentTask> tasks = new ArrayList<>();

        String diseaseName = intent.getEntity(MedicalIntent.DISEASE_NAME);
        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);

        Map<String, String> params = new HashMap<>();
        if (diseaseName != null) {
            params.put("diseaseName", diseaseName);
        } else {
            // 尝试从原始输入提取疾病名
            String query = intent.getOriginalQuery();
            params.put("diseaseName", query);
        }
        if (symptom != null) params.put("symptom", symptom);

        AgentTask task = AgentTask.builder()
                .id("task-1")
                .description("疾病百科查询")
                .toolName("diseaseInfo")
                .toolPurpose("查询疾病的详细信息：概述、症状、病因、治疗方法、预防措施")
                .order(0)
                .dependencies(Collections.emptyList())
                .params(params)
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        tasks.add(task);
        return tasks;
    }

    /**
     * 构建挂号导诊多步骤任务
     * Step 1: symptomAnalysis → Step 2: hospitalSearch
     */
    private List<AgentTask> buildRegisterGuideTasks(MedicalIntent intent) {
        List<AgentTask> tasks = new ArrayList<>();

        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);
        String city = intent.getEntity(MedicalIntent.CITY);
        String department = intent.getEntity(MedicalIntent.DEPARTMENT);

        // Step 1: 分析应挂什么科室
        AgentTask step1 = AgentTask.builder()
                .id("task-1")
                .description("分析应该挂什么科室")
                .toolName("diseaseInfo")
                .toolPurpose("根据症状分析可能对应的疾病和科室，为挂号提供科室推荐")
                .order(0)
                .dependencies(Collections.emptyList())
                .params(buildParams("symptom", symptom != null ? symptom : intent.getOriginalQuery()))
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        // Step 2: 查询附近医院对应科室
        Map<String, String> hospitalParams = new HashMap<>();
        if (city != null) hospitalParams.put("city", city);
        if (department != null) {
            hospitalParams.put("department", department);
        }
        if (symptom != null) hospitalParams.put("symptom", symptom);
        if (hospitalParams.isEmpty()) {
            hospitalParams.put("city", "北京");
        }

        AgentTask step2 = AgentTask.builder()
                .id("task-2")
                .description("查询附近医院对应科室")
                .toolName("hospitalRegister")
                .toolPurpose("根据科室和城市查询附近的医院，包括等级、地址、排队情况")
                .order(1)
                .dependencies(List.of("task-1"))
                .params(hospitalParams)
                .required(true)
                .status(TaskStatus.PENDING)
                .build();

        tasks.add(step1);
        tasks.add(step2);

        return tasks;
    }

    // ---- 推理构建辅助方法 ----

    private String buildHealthAdviceReasoning(MedicalIntent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户意图为健康建议，启动多步骤综合评估流程：");
        sb.append("\n1) 先查询症状相关的疾病信息，了解可能涉及的健康问题");
        sb.append("\n2) 根据识别的疾病信息，进一步获取治疗和预防措施");
        sb.append("\n3) 查询相关的药品信息，提供用药建议");
        sb.append("\n4) 综合前三步结果，生成包含饮食、运动、作息和预防的全面健康建议");

        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);
        if (symptom != null) {
            sb.append("\n\n检测到症状：").append(symptom);
        }
        return sb.toString();
    }

    private String buildRegisterReasoning(MedicalIntent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户意图为挂号导诊，启动两步流程：");
        sb.append("\n1) 先分析症状对应的科室，为用户推荐最合适的就诊科室");
        sb.append("\n2) 根据推荐的科室和用户所在城市，查询附近可挂号医院");

        String city = intent.getEntity(MedicalIntent.CITY);
        if (city != null) {
            sb.append("\n\n定位城市：").append(city);
        }
        return sb.toString();
    }

    // ---- 工具方法 ----

    private Map<String, String> buildParams(String key, String value) {
        Map<String, String> params = new HashMap<>();
        params.put(key, value);
        return params;
    }

    private Map<String, String> buildHealthAdviceParams(MedicalIntent intent) {
        Map<String, String> params = new HashMap<>();
        String symptom = intent.getEntity(MedicalIntent.SYMPTOM);
        String age = intent.getEntity(MedicalIntent.AGE);
        String gender = intent.getEntity(MedicalIntent.GENDER);

        if (symptom != null) params.put("symptom", symptom);
        if (age != null) params.put("age", age);
        if (gender != null) params.put("gender", gender);
        params.put("goal", "综合健康管理");

        return params;
    }

    private String buildSymptomQuery(String symptom, String bodyPart) {
        if (symptom == null && bodyPart == null) return "综合症状分析";
        StringBuilder sb = new StringBuilder();
        if (bodyPart != null) sb.append(bodyPart);
        if (symptom != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(symptom);
        }
        return sb.toString();
    }
}
