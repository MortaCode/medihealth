package com.myy.medihealth.chat.service;

import com.myy.medihealth.chat.vo.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 模型选择服务 - 策略模式根据用户选择返回对应的ChatClient
 * 支持DeepSeek和通义千问两种模型
 */
@Slf4j
@Service
public class ModelSelectService {

    private final ChatClient.Builder deepseekChatClient;
    private final ChatClient.Builder qwenChatClient;

    public ModelSelectService(
            @Qualifier("deepseekChatClient") ChatClient.Builder deepseekChatClient,
            @Qualifier("qwenChatClient") ChatClient.Builder qwenChatClient) {
        this.deepseekChatClient = deepseekChatClient;
        this.qwenChatClient = qwenChatClient;
    }

    /**
     * 根据模型枚举选择对应的ChatClient
     *
     * @param model 聊天模型枚举
     * @return 对应模型的ChatClient实例
     */
    public ChatClient selectModel(ChatModel model) {
        log.debug("选择模型: {} ({})", model.getDisplayName(), model.getCode());

        return switch (model) {
            case DEEPSEEK -> deepseekChatClient.build();
            case QWEN -> qwenChatClient.build();
        };
    }

    /**
     * 根据模型名称字符串选择对应的ChatClient
     *
     * @param modelName 模型名称 (deepseek/qwen)
     * @return 对应模型的ChatClient实例
     */
    public ChatClient selectModel(String modelName) {
        return selectModel(ChatModel.fromString(modelName));
    }
}
