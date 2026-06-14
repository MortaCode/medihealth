package com.myy.medihealth.chat.service.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

/**
 * RAG 知识检索顾问 - 为医学知识检索预留
 * 当前为透传实现，后续可集成向量检索和BM25检索
 * <p>
 * 计划功能：
 * 1. 从请求中提取医学相关问题
 * 2. 调用 HybridSearchService 或 HierarchicalRetrieverService
 * 3. 将检索到的相关医学知识注入 System Prompt
 * 4. 传递增强后的请求给后续处理链
 */
@Slf4j
@Component
public class RAGAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "ragAdvisor";
    }

    @Override
    public int getOrder() {
        return 80;
    }

    /**
     * 当前为透传实现
     * <p>
     * 后续增强方案：
     * <pre>
     * // 1. 提取用户查询
     * String userText = request.userText();
     *
     * // 2. 多级检索
     * List&lt;SearchResult&gt; results = hierarchicalRetriever.search(userText, 5);
     *
     * // 3. 构建知识上下文
     * String knowledgeCtx = buildKnowledgeContext(results);
     *
     * // 4. 注入 System Prompt
     * ChatClientRequest enhanced = ChatClientRequest.builder()
     *     .prompt(...)
     *     .context(request.context())
     *     .chatOptions(request.chatOptions())
     *     .functionCallbacks(request.functionCallbacks())
     *     .build();
     *
     * return callAdvisorChain.nextCall(enhanced);
     * </pre>
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.debug("RAGAdvisor: pass-through, request to be enhanced with medical knowledge in future");
        return callAdvisorChain.nextCall(chatClientRequest);
    }
}
