package com.myy.medihealth.chat.agent;

import com.myy.medihealth.chat.agent.model.AgentPlan;
import com.myy.medihealth.chat.agent.model.IntentType;
import com.myy.medihealth.chat.agent.model.MedicalIntent;
import com.myy.medihealth.chat.mcp.tool.DiseaseInfoTool;
import com.myy.medihealth.chat.mcp.tool.DrugQueryTool;
import com.myy.medihealth.chat.mcp.tool.HealthAdviceTool;
import com.myy.medihealth.chat.mcp.tool.HospitalRegisterTool;
import com.myy.medihealth.chat.service.ModelSelectService;
import com.myy.medihealth.chat.vo.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

/**
 * Agent 编排器 - 医疗AI Agent的核心调度器
 * 负责意图识别 -> 任务规划 -> 工具调用 -> LLM回答的完整流程编排
 *
 * 工作流程：
 * 1. 意图识别：分析用户输入，识别医疗意图类型
 * 2. 任务规划：根据意图编排工具调用任务序列
 * 3. 增强输入：将意图和计划信息注入到用户输入中
 * 4. LLM调用：通过ChatClient调用大模型，自动调用MCP工具获取医疗数据
 * 5. 结果封装：汇总所有信息返回AgentResponse
 */
@Slf4j
@Service
public class AgentOrchestrator {

    private final IntentRecognitionService intentService;
    private final TaskPlanningService taskPlanningService;
    private final ModelSelectService modelSelectService;
    private final ChatClient.Builder deepseekChatClient;
    private final ChatClient.Builder qwenChatClient;
    private final AgentAdvisor agentAdvisor;

    /** MCP医疗工具 */
    private final DrugQueryTool drugQueryTool;
    private final DiseaseInfoTool diseaseInfoTool;
    private final HospitalRegisterTool hospitalRegisterTool;
    private final HealthAdviceTool healthAdviceTool;

    public AgentOrchestrator(IntentRecognitionService intentService,
                             TaskPlanningService taskPlanningService,
                             ModelSelectService modelSelectService,
                             ChatClient.Builder deepseekChatClient,
                             ChatClient.Builder qwenChatClient,
                             AgentAdvisor agentAdvisor,
                             DrugQueryTool drugQueryTool,
                             DiseaseInfoTool diseaseInfoTool,
                             HospitalRegisterTool hospitalRegisterTool,
                             HealthAdviceTool healthAdviceTool) {
        this.intentService = intentService;
        this.taskPlanningService = taskPlanningService;
        this.modelSelectService = modelSelectService;
        this.deepseekChatClient = deepseekChatClient;
        this.qwenChatClient = qwenChatClient;
        this.agentAdvisor = agentAdvisor;
        this.drugQueryTool = drugQueryTool;
        this.diseaseInfoTool = diseaseInfoTool;
        this.hospitalRegisterTool = hospitalRegisterTool;
        this.healthAdviceTool = healthAdviceTool;
    }

    /**
     * 处理用户对话请求 - Agent完整工作流
     *
     * @param sessionId 会话ID
     * @param userInput 用户输入内容
     * @param modelName 模型名称 (deepseek/qwen)
     * @return Agent响应，包含意图、计划、LLM回答、耗时等信息
     */
    public AgentResponse process(String sessionId, String userInput, String modelName) {
        long startTime = System.currentTimeMillis();
        log.info("Agent开始处理: sessionId={}, input='{}', model={}", sessionId, userInput, modelName);

        try {
            // ========== Step 1: 意图识别 ==========
            MedicalIntent intent = intentService.recognize(userInput);
            log.info("意图识别: type={}, confidence={:.2f}",
                    intent.getType().getName(), intent.getConfidence());

            // ========== Step 2: 任务规划 ==========
            AgentPlan plan = taskPlanningService.plan(intent);
            log.info("任务规划: taskCount={}, reasoning={}",
                    plan.getTasks() != null ? plan.getTasks().size() : 0,
                    plan.getReasoning());

            // ========== Step 3: 构建增强的用户输入 ==========
            String enhancedInput = buildEnhancedInput(userInput, intent, plan);

            // ========== Step 4: 选择并构建ChatClient ==========
            ChatClient chatClient = buildChatClient(modelName, plan);

            // ========== Step 5: 调用LLM ==========
            String content = chatClient.prompt()
                    .user(enhancedInput)
                    .advisors(spec -> {
                        spec.param("sessionId", sessionId);
                        spec.param("modelName", modelName);
                        spec.param("agentPlan", plan);
                    })
                    .call()
                    .content();

            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("Agent完成: elapsedMs={}ms, responseLength={}",
                    elapsedMs, content != null ? content.length() : 0);

            return new AgentResponse(intent, plan,
                    content != null ? content : "",
                    elapsedMs, LocalDateTime.now());

        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.error("Agent处理异常: {}", e.getMessage(), e);

            // 降级处理：返回通用错误响应
            return new AgentResponse(
                    MedicalIntent.of(IntentType.GENERAL_CHAT, 0.1, userInput,
                            "系统异常回退: " + e.getMessage()),
                    AgentPlan.builder()
                            .tasks(Collections.emptyList())
                            .reasoning("处理异常，回退到一般对话模式")
                            .createdAt(LocalDateTime.now())
                            .build(),
                    buildErrorContent(e),
                    elapsedMs,
                    LocalDateTime.now()
            );
        }
    }

    /**
     * 构建带工具和顾问的ChatClient
     */
    private ChatClient buildChatClient(String modelName, AgentPlan plan) {
        ChatClient.Builder builder = selectClientBuilder(modelName);

        // 注册Agent顾问（ReAct系统提示词 + 任务计划）
        builder.defaultAdvisors(agentAdvisor);

        // 注册MCP医疗工具
        builder.defaultTools(
                drugQueryTool,
                diseaseInfoTool,
                hospitalRegisterTool,
                healthAdviceTool
        );

        return builder.build();
    }

    /**
     * 构建增强的用户输入（包含意图和计划信息）
     */
    private String buildEnhancedInput(String userInput, MedicalIntent intent, AgentPlan plan) {
        StringBuilder sb = new StringBuilder();

        // 意图识别摘要
        sb.append("## 意图识别\n");
        sb.append("- **意图类型**：").append(intent.getType().getName()).append("\n");
        sb.append("- **置信度**：")
                .append(String.format("%.0f%%", intent.getConfidence() * 100)).append("\n");

        // 提取的实体信息
        Map<String, String> entities = intent.getEntities();
        if (!entities.isEmpty()) {
            sb.append("- **关键信息**：\n");
            for (Map.Entry<String, String> entry : entities.entrySet()) {
                sb.append("  - ").append(getEntityLabel(entry.getKey()))
                        .append("：").append(entry.getValue()).append("\n");
            }
        }

        sb.append("\n");

        // 任务计划摘要
        if (plan.getTasks() != null && !plan.getTasks().isEmpty()) {
            sb.append("## 执行计划\n");
            sb.append(plan.getReasoning()).append("\n\n");
        }

        // 用户原始问题
        sb.append("## 用户问题\n");
        sb.append(userInput).append("\n\n");

        // 最终指令
        sb.append("---\n");
        sb.append("请根据以上信息，按照Agent工作模式处理用户的问题。");
        sb.append("需要调用工具时，严格按执行计划的步骤顺序进行。");
        sb.append("最终回答必须包含免责声明：\"以上建议仅供参考，不能替代专业医疗诊断。" +
                "如症状持续或加重，请及时到正规医院就诊。\"");

        return sb.toString();
    }

    /**
     * 获取实体中文标签
     */
    private String getEntityLabel(String key) {
        return switch (key) {
            case "symptom" -> "症状";
            case "bodyPart" -> "身体部位";
            case "duration" -> "持续时间";
            case "drugName" -> "药品名称";
            case "diseaseName" -> "疾病名称";
            case "city" -> "所在城市";
            case "department" -> "目标科室";
            case "age" -> "年龄";
            case "gender" -> "性别";
            case "severity" -> "严重程度";
            default -> key;
        };
    }

    /**
     * 根据模型名称选择对应的ChatClient Builder
     */
    private ChatClient.Builder selectClientBuilder(String modelName) {
        ChatModel model = ChatModel.fromString(modelName);
        log.debug("选择AI模型: {} ({})", model.getDisplayName(), model.getCode());

        return switch (model) {
            case QWEN -> qwenChatClient;
            case DEEPSEEK -> deepseekChatClient;
        };
    }

    /**
     * 构建系统异常时的降级响应
     */
    private String buildErrorContent(Exception e) {
        return """
                非常抱歉，系统在处理您的健康咨询时遇到了技术问题。

                可能的原因：
                - AI模型服务暂时不可用
                - 网络连接异常
                - 系统内部错误

                建议您：
                1. 稍后重新尝试提问
                2. 如果是紧急医疗问题，请立即拨打120急救电话
                3. 或前往最近的医院急诊科就诊

                【重要免责声明】
                本系统提供的信息仅供参考，不能替代专业医疗诊断和治疗。
                如您有紧急医疗需求，请立即就医。

                错误详情：%s""".formatted(e.getMessage() != null ? e.getMessage() : "未知错误");
    }

    /**
     * Agent 响应 - 封装完整的Agent处理结果
     */
    public record AgentResponse(
            MedicalIntent intent,
            AgentPlan plan,
            String content,
            long elapsedMs,
            LocalDateTime timestamp
    ) {
        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        /**
         * 生成响应摘要（用于日志和调试）
         */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Agent 响应摘要 ===\n");
            sb.append("时间: ").append(timestamp != null ? timestamp.format(FMT) : "N/A").append("\n");
            sb.append("耗时: ").append(elapsedMs).append("ms\n");

            if (intent != null) {
                sb.append("意图: ").append(intent.getType().getName())
                        .append(" (置信度: ")
                        .append(String.format("%.0f%%", intent.getConfidence() * 100))
                        .append(")\n");
                sb.append("推理: ").append(intent.getReasoning()).append("\n");
            }

            if (plan != null && plan.getTasks() != null) {
                sb.append("任务数: ").append(plan.getTasks().size()).append("\n");
                sb.append("编排推理: ").append(plan.getReasoning()).append("\n");
            }

            int contentLen = content != null ? content.length() : 0;
            sb.append("回答长度: ").append(contentLen).append(" 字符\n");

            return sb.toString();
        }
    }
}
