package com.myy.medihealth.chat.agent;

import com.myy.medihealth.chat.agent.model.AgentPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent Advisor - 智能医疗助手的 ReAct 工作模式注入器
 *
 * 职责：
 * 1. 为每次对话注入 ReAct（思考-行动-观察）模式的系统提示词
 * 2. 将任务执行计划转换为LLM可理解的指令
 * 3. 确保AI遵循医疗安全规范（免责声明、不编造建议等）
 *
 * 执行顺序：60（在RAGAdvisor(80)之前，在MemoryAdvisor(100)之前执行）
 */
@Slf4j
@Component
public class AgentAdvisor implements CallAdvisor {

    /** ReAct模式的医疗助手系统提示词 */
    private static final String SYSTEM_PROMPT = """
            ## Agent 工作模式：ReAct (思考-行动-观察) - 智能医疗助手

            你是一个专业的智能医疗健康助手，基于循证医学知识为用户提供健康咨询服务。你需要按照以下模式工作：

            **思考（Thought）**：分析用户的健康问题，判断需要什么信息。
            **行动（Action）**：调用合适的医疗工具获取数据。
            **观察（Observation）**：分析工具返回的医疗信息。
            **下一步思考**：根据观察结果决定是否需要进一步查询。
            **最终回答**：汇总所有信息，给出专业、安全的健康建议。

            ### 重要规则
            1. 每次只调用一个工具，等待结果后再决定下一步
            2. 严禁编造医疗建议，必须基于工具返回的信息
            3. 必须添加免责声明：以上建议仅供参考，严重症状请及时就医
            4. 如果一个工具返回错误或无结果，尝试调整参数重试，最多重试2次
            5. 用药建议必须说明禁忌和注意事项
            6. 回答应通俗易懂，避免过多专业术语
            7. 对于紧急症状（剧烈胸痛、呼吸困难、大出血等），优先建议立即就医

            ### 医疗工具使用指南
            - `diseaseInfo`：查询疾病百科，参数：diseaseName（疾病名称）、symptom（症状）
            - `drugQuery`：查询药品信息，参数：drugName（药品名称）、symptom（症状）
            - `hospitalRegister`：查询医院挂号，参数：city（城市）、department（科室）、symptom（症状）
            - `healthAdvice`：生成健康建议，参数：symptom（症状）、goal（目标）、age（年龄）、gender（性别）

            ### 回答格式要求
            最终回答请按以下结构组织：
            1. **问题分析**：简要总结用户的健康问题
            2. **查询结果**：展示工具查询获取的关键信息
            3. **专业建议**：基于查询结果给出的具体建议
            4. **注意事项**：需要特别注意的事项和禁忌
            5. **免责声明**：说明建议的局限性

            请始终保持专业、负责、谨慎的态度。""";

    @Override
    public String getName() {
        return "agentAdvisor";
    }

    @Override
    public int getOrder() {
        return 60;
    }

    /**
     * 围绕ChatClient调用执行Agent逻辑
     *
     * 在请求发送到LLM之前：
     * 1. 检查是否为新会话（无系统消息），注入ReAct系统提示词
     * 2. 从context中提取AgentPlan，生成任务指令注入到系统消息
     * 3. 确保安全规则和免责声明要求被包含
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // 从上下文获取AgentPlan
        Map<String, Object> context = chatClientRequest.context();
        AgentPlan agentPlan = null;
        if (context != null && context.containsKey("agentPlan")) {
            Object planObj = context.get("agentPlan");
            if (planObj instanceof AgentPlan plan) {
                agentPlan = plan;
                log.info("AgentAdvisor: 获取到任务计划, taskCount={}",
                        plan.getTasks() != null ? plan.getTasks().size() : 0);
            }
        }

        // 构建增强后的系统提示词（ReAct + 任务计划）
        String fullSystemPrompt = buildFullSystemPrompt(agentPlan);

        // 检查现有消息并注入系统提示词
        ChatClientRequest enhancedRequest = injectSystemPrompt(chatClientRequest, fullSystemPrompt);

        // 调用链中的下一个处理器
        return callAdvisorChain.nextCall(enhancedRequest);
    }

    /**
     * 构建完整的系统提示词（ReAct模式 + 任务计划指令）
     */
    private String buildFullSystemPrompt(AgentPlan agentPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT);

        if (agentPlan != null) {
            String planInstructions = agentPlan.toPromptInstructions();
            if (planInstructions != null && !planInstructions.isEmpty()) {
                sb.append("\n\n").append(planInstructions);
            }
        }

        return sb.toString();
    }

    /**
     * 将系统提示词注入到请求的消息列表中
     * - 如果已有系统消息，在其前面添加Agent提示
     * - 如果没有系统消息，创建新的系统消息
     */
    private ChatClientRequest injectSystemPrompt(ChatClientRequest request, String systemPrompt) {
        Prompt originalPrompt = request.prompt();
        List<Message> originalMessages = originalPrompt != null
                ? new ArrayList<>(originalPrompt.getInstructions())
                : new ArrayList<>();

        boolean hasSystemMessage = originalMessages.stream()
                .anyMatch(msg -> msg instanceof SystemMessage);

        List<Message> enhancedMessages = new ArrayList<>();

        if (!hasSystemMessage) {
            enhancedMessages.add(new SystemMessage(systemPrompt));
            originalMessages.stream()
                    .filter(msg -> !(msg instanceof SystemMessage))
                    .forEach(enhancedMessages::add);
        } else {
            enhancedMessages.add(new SystemMessage(systemPrompt));
            enhancedMessages.addAll(originalMessages);
        }

        Prompt enhancedPrompt = new Prompt(enhancedMessages);

        return ChatClientRequest.builder()
                .prompt(enhancedPrompt)
                .context(request.context())
//                .chatOptions(request.chatOptions())
//                .functionCallbacks(request.functionCallbacks())
                .build();
    }
}
