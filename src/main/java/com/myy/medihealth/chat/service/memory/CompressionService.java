package com.myy.medihealth.chat.service.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话历史压缩服务
 * 当消息超过阈值时，使用 LLM 将历史压缩为摘要，保留最近消息以控制上下文窗口
 */
@Slf4j
@Service
public class CompressionService {

    private static final int COMPRESSION_THRESHOLD = 30;
    private static final int KEEP_RECENT_COUNT = 10;

    private final ChatModel deepseekChatModel;

    public CompressionService(@Qualifier("openAiChatModel") ChatModel deepseekChatModel) {
        this.deepseekChatModel = deepseekChatModel;
    }

    /**
     * 压缩结果记录
     */
    public record CompressedResult(boolean compressed, List<Message> messages, String summary) {
        public static CompressedResult unchanged(List<Message> messages) {
            return new CompressedResult(false, messages, null);
        }

        public static CompressedResult compressed(List<Message> messages, String summary) {
            return new CompressedResult(true, messages, summary);
        }
    }

    /**
     * 压缩对话历史
     * - 消息数 <= 30：不做压缩，直接返回全部消息
     * - 消息数 > 30：提取新消息，调用 LLM 生成摘要，返回 [SystemMessage(摘要)] + [最近10条消息]
     *
     * @param messages  完整消息历史
     * @param modelName 模型名称
     * @return 压缩结果
     */
    public CompressedResult compress(List<Message> messages, String modelName) {
        if (messages == null || messages.isEmpty()) {
            return CompressedResult.unchanged(List.of());
        }

        // 消息数不超过阈值，无需压缩
        if (messages.size() <= COMPRESSION_THRESHOLD) {
            return CompressedResult.unchanged(messages);
        }

        try {
            // 1. 提取需要压缩的历史消息（排除最近 KEEP_RECENT_COUNT 条）
            int splitIndex = Math.max(0, messages.size() - KEEP_RECENT_COUNT);
            List<Message> historyToCompress = messages.subList(0, splitIndex);
            List<Message> recentMessages = messages.subList(splitIndex, messages.size());

            // 2. 构建压缩提示词
            String historyText = buildHistoryText(historyToCompress);
            String compressionPrompt = buildCompressionPrompt(historyText);

            // 3. 调用 LLM 生成摘要
            Prompt prompt = new Prompt(compressionPrompt);
            String summary = deepseekChatModel.call(prompt)
                    .getResult()
                    .getOutput()
                    .getText();

            if (summary == null || summary.isBlank()) {
                log.warn("Compression summary is empty, falling back to recent messages only");
                return CompressedResult.compressed(
                        new ArrayList<>(recentMessages), "对话历史过长，已截断至最近消息");
            }

            // 4. 构建压缩后的消息列表：[SystemMessage(摘要)] + [最近10条消息]
            List<Message> compressedMessages = new ArrayList<>();
            compressedMessages.add(new SystemMessage(
                    "以下是对之前对话历史的摘要，请基于此上下文继续对话：\n" + summary));
            compressedMessages.addAll(recentMessages);

            log.info("Compressed {} messages to summary ({} chars) + {} recent messages",
                    historyToCompress.size(), summary.length(), recentMessages.size());

            return CompressedResult.compressed(compressedMessages, summary);

        } catch (Exception e) {
            log.error("Failed to compress chat history, falling back to recent messages", e);
            // 压缩失败时降级：只保留最近消息
            int keepIndex = Math.max(0, messages.size() - KEEP_RECENT_COUNT);
            return CompressedResult.compressed(
                    new ArrayList<>(messages.subList(keepIndex, messages.size())),
                    "压缩失败，已截断至最近消息");
        }
    }

    /**
     * 将消息列表转换为纯文本历史记录
     */
    private String buildHistoryText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String role = getRoleLabel(msg);
            String text = msg.getText();
            if (text != null && !text.isBlank()) {
                sb.append(role).append(": ").append(text.trim()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取消息角色标签
     */
    private String getRoleLabel(Message msg) {
        if (msg instanceof UserMessage) return "用户";
        if (msg instanceof AssistantMessage) return "助手";
        if (msg instanceof SystemMessage) return "系统";
        return "未知";
    }

    /**
     * 构建压缩提示词 - 指示 LLM 将对话历史压缩为简洁摘要
     */
    private String buildCompressionPrompt(String history) {
        return """
                请将以下对话历史压缩为不超过500字的摘要。
                压缩要求：
                1. 保留关键医疗信息和建议（症状、诊断、用药、健康建议等）
                2. 保留用户的基本健康信息和偏好
                3. 保留重要的决策和结论
                4. 去除重复、闲聊和无效信息
                5. 按时间顺序组织摘要内容
                6. 直接输出摘要内容，不要添加任何前缀或说明

                对话历史：
                %s

                请输出摘要：
                """.formatted(history);
    }
}
