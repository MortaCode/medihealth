package com.myy.medihealth.chat.service.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天记忆压缩服务 - 组合 ChatMemoryService 和 CompressionService
 * 提供带自动压缩的聊天记忆获取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryCompressService {

    private final ChatMemoryService chatMemoryService;
    private final CompressionService compressionService;

    /**
     * 获取会话记忆并自动压缩
     * 1. 从 ChatMemoryService 获取完整历史
     * 2. 通过 CompressionService 压缩（如需要）
     * 3. 返回压缩后的消息列表
     *
     * @param sessionId 会话ID
     * @return 压缩后的消息列表
     */
    public List<Message> getMemoryWithCompress(String sessionId) {
        return getMemoryWithCompress(sessionId, "deepseek");
    }

    /**
     * 获取会话记忆并自动压缩（指定模型）
     *
     * @param sessionId 会话ID
     * @param modelName 模型名称
     * @return 压缩后的消息列表
     */
    public List<Message> getMemoryWithCompress(String sessionId, String modelName) {
        // 1. 获取完整历史
        List<Message> fullHistory = chatMemoryService.getMemory(sessionId);

        if (fullHistory.isEmpty()) {
            log.debug("No memory found for session: {}", sessionId);
            return fullHistory;
        }

        // 2. 压缩历史
        CompressionService.CompressedResult result = compressionService.compress(fullHistory, modelName);

        if (result.compressed()) {
            log.info("Memory compressed for session: {} - {} messages -> {} messages (summary: {} chars)",
                    sessionId, fullHistory.size(), result.messages().size(),
                    result.summary() != null ? result.summary().length() : 0);
        }

        return result.messages();
    }
}
