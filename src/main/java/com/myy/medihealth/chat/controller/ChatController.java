package com.myy.medihealth.chat.controller;

import com.myy.medihealth.chat.agent.AgentOrchestrator;
import com.myy.medihealth.chat.agent.AgentOrchestrator.AgentResponse;
import com.myy.medihealth.chat.vo.ChatMessageVo;
import com.myy.medihealth.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Agent 对话控制器
 * 提供智能医疗助手的主对话接口
 */
@Slf4j
@RestController
@RequestMapping("chat")
@Validated
public class ChatController {

    private final AgentOrchestrator agentOrchestrator;

    public ChatController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    /**
     * Agent智能对话接口
     * 接收用户健康咨询，通过AI Agent进行意图识别、任务编排、工具调用并返回专业建议
     *
     * @param messageVo 聊天请求（包含sessionId, userInput, modelName）
     * @return 包含AI回答和Agent处理元信息的结果
     */
    @PostMapping("/ai")
    public Result<Map<String, Object>> chat(@RequestBody ChatMessageVo messageVo) {
        String sessionId = messageVo.getSessionId();
        String userInput = messageVo.getUserInput();
        String modelName = messageVo.getModelName();

        // 参数校验
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default-" + System.currentTimeMillis();
        }
        if (userInput == null || userInput.isBlank()) {
            return Result.error("用户输入不能为空");
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "deepseek";
        }

        log.info("AI对话请求: sessionId={}, model={}, input='{}'",
                sessionId, modelName, userInput.substring(0, Math.min(50, userInput.length())));

        // 调用Agent编排器处理
        AgentResponse response = agentOrchestrator.process(sessionId, userInput, modelName);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("content", response.content());
        result.put("sessionId", sessionId);
        result.put("modelName", modelName);

        // Agent元信息
        Map<String, Object> metaInfo = new HashMap<>();
        metaInfo.put("intent", response.intent() != null ? response.intent().getType().getName() : "未知");
        metaInfo.put("confidence", String.format("%.0f%%",
                response.intent() != null ? response.intent().getConfidence() * 100 : 0));
        metaInfo.put("taskCount", response.plan() != null && response.plan().getTasks() != null
                ? response.plan().getTasks().size() : 0);
        metaInfo.put("elapsedMs", response.elapsedMs());
        metaInfo.put("reasoning", response.intent() != null ? response.intent().getReasoning() : "");
        metaInfo.put("planReasoning", response.plan() != null ? response.plan().getReasoning() : "");
        result.put("meta", metaInfo);

        log.info("AI对话响应: elapsedMs={}ms, intent={}, taskCount={}",
                response.elapsedMs(),
                response.intent() != null ? response.intent().getType().getName() : "N/A",
                metaInfo.get("taskCount"));

        return Result.success(result);
    }
}
