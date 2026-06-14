package com.myy.medihealth.chat.service.advisor;

import cn.hutool.core.util.IdUtil;
import com.myy.medihealth.chat.service.memory.ChatMemoryCompressService;
import com.myy.medihealth.chat.service.memory.ChatMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天记忆顾问 - 在每次对话请求前后管理记忆
 * 请求前：加载历史消息并注入到请求中
 * 请求后：保存用户消息和助手响应到记忆系统
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int DEFAULT_CONTEXT_WINDOW = 10;

    private final ChatMemoryCompressService chatMemoryCompressService;
    private final ChatMemoryService chatMemoryService;

    @Override
    public String getName() {
        return "chatMemoryAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * 同步调用顾问
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.debug("顾问开启");

        // 1. 获取或生成会话ID
        String sessionId = extractSessionId(chatClientRequest);
        if (!StringUtils.hasText(sessionId)) {
            sessionId = IdUtil.objectId();
            chatClientRequest = setSessionId(chatClientRequest, sessionId);
            log.debug("首次会话，生成新会话ID: {}", sessionId);
        }

        // 2. 加载历史消息（带压缩）
        List<Message> historyMessages = loadHistoryMessages(sessionId);
        log.debug("加载会话{}的历史消息：{}", sessionId, historyMessages.size());

        // 3. 构建包含历史消息的新Prompt
        ChatClientRequest enhancedRequest = enhanceRequestWithHistory(chatClientRequest, historyMessages);

        // 4. 执行调用链
        ChatClientResponse response = callAdvisorChain.nextCall(enhancedRequest);

        // 5. 保存本次对话到历史
        if (response != null && response.chatResponse() != null) {
            saveConversation(sessionId, chatClientRequest, response);
        }

        log.debug("顾问关闭");
        return response;
    }

    /**
     * 流式调用顾问
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        log.debug("顾问开启");

        // 1. 获取会话ID
        String sessionId = extractSessionId(chatClientRequest);
        if (!StringUtils.hasText(sessionId)) {
            sessionId = IdUtil.objectId();
            chatClientRequest = setSessionId(chatClientRequest, sessionId);
            log.debug("首次会话，生成新会话ID: {}", sessionId);
        }

        // 2. 加载历史消息（带压缩）
        List<Message> historyMessages = loadHistoryMessages(sessionId);
        log.debug("加载会话{}的历史消息：{}", sessionId, historyMessages.size());

        // 3. 构建包含历史消息的新Prompt
        ChatClientRequest enhancedRequest = enhanceRequestWithHistory(chatClientRequest, historyMessages);

        // 4. 执行流式调用链
        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(enhancedRequest);

        // 5. 收集流式响应并保存完整对话
        return saveStreamConversation(sessionId, chatClientRequest, responseFlux);
    }

    /**
     * 从请求上下文中提取 sessionId，不存在则返回 null
     */
    private String extractSessionId(ChatClientRequest request) {
        Map<String, Object> context = request.context();
        if (context != null && context.containsKey("sessionId")) {
            Object sid = context.get("sessionId");
            return sid != null ? sid.toString() : null;
        }
        return null;
    }

    /**
     * 设置会话ID到请求上下文
     */
    private ChatClientRequest setSessionId(ChatClientRequest chatClientRequest, String sessionId) {
        chatClientRequest.context().put("sessionId", sessionId);
        return chatClientRequest;
    }

    /**
     * 加载历史消息（带压缩）
     */
    private List<Message> loadHistoryMessages(String sessionId) {
        try {
            return chatMemoryCompressService.getMemoryWithCompress(sessionId);
        } catch (Exception e) {
            log.error("会话{}获取历史消息异常", sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将历史消息合并到当前请求的 Prompt 中
     */
    private ChatClientRequest enhanceRequestWithHistory(ChatClientRequest request, List<Message> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return request;
        }

        // 获取原始请求的消息
        Prompt originalPrompt = request.prompt();
        List<Message> originalMessages = (originalPrompt != null)
                ? new ArrayList<>(originalPrompt.getInstructions())
                : new ArrayList<>();

        // 构建增强后的消息列表：[历史消息] + [原始消息]
        List<Message> enhancedMessages = new ArrayList<>();
        enhancedMessages.addAll(historyMessages);
        if (!originalMessages.isEmpty()) {
            enhancedMessages.addAll(originalMessages);
        }

        // 限制上下文窗口大小
        if (enhancedMessages.size() > DEFAULT_CONTEXT_WINDOW * 5) {
            int keepCount = DEFAULT_CONTEXT_WINDOW * 5;
            enhancedMessages = enhancedMessages.subList(
                    enhancedMessages.size() - keepCount, enhancedMessages.size());
        }

        // 创建增强后的 Prompt
        Prompt enhancedPrompt = new Prompt(enhancedMessages);

        // 构建新的 ChatClientRequest，保留原始请求的其他属性
        return ChatClientRequest.builder()
                .prompt(enhancedPrompt)
                .context(request.context())
//                .chatOptions(request.chatOptions())
//                .functionCallbacks(request.functionCallbacks())
                .build();
    }

    /**
     * 保存同步对话到记忆系统
     */
    private void saveConversation(String sessionId, ChatClientRequest request, ChatClientResponse response) {
        try {
            String userMessage = extractUserMessage(request);
            String assistantMessage = extractAssistantMessage(response);

            if (userMessage != null && assistantMessage != null) {
                List<Message> messages = new ArrayList<>();
                messages.add(new UserMessage(userMessage));
                messages.add(new AssistantMessage(assistantMessage));
                chatMemoryService.saveMemory(sessionId, messages);
                log.debug("保存聊天信息，会话{}", sessionId);
            }
        } catch (Exception e) {
            log.error("保存聊天信息出错，会话{}", sessionId, e);
        }
    }

    /**
     * 保存流式对话到记忆系统
     */
    private Flux<ChatClientResponse> saveStreamConversation(String sessionId, ChatClientRequest request,
                                                            Flux<ChatClientResponse> responseFlux) {
        StringBuilder fullResponse = new StringBuilder();
        String userMessage = extractUserMessage(request);

        return responseFlux
                .doOnNext(response -> {
                    if (response != null && response.chatResponse() != null) {
                        String content = response.chatResponse().getResult().getOutput().getText();
                        if (content != null) {
                            fullResponse.append(content);
                        }
                    }
                })
                .doOnComplete(() -> {
                    try {
                        if (userMessage != null && fullResponse.length() > 0) {
                            List<Message> messages = new ArrayList<>();
                            messages.add(new UserMessage(userMessage));
                            messages.add(new AssistantMessage(fullResponse.toString()));
                            chatMemoryService.saveMemory(sessionId, messages);
                            log.debug("保存聊天信息，会话{}", sessionId);
                        }
                    } catch (Exception e) {
                        log.error("保存聊天信息出错，会话{}", sessionId, e);
                    }
                });
    }

    /**
     * 从请求中提取用户消息
     */
    private String extractUserMessage(ChatClientRequest request) {
        if (request.prompt() == null) {
            return null;
        }

        List<Message> messages = request.prompt().getInstructions();
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        // 获取最后一条用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return msg.getText();
            }
        }

        return null;
    }

    /**
     * 从响应中提取助手消息
     */
    private String extractAssistantMessage(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return null;
        }

        var chatResponse = response.chatResponse();
        if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }

        return chatResponse.getResult().getOutput().getText();
    }
}
