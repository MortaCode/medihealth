package com.myy.medihealth.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI模型枚举 - 支持的聊天模型定义
 */
@Getter
@AllArgsConstructor
public enum ChatModel {
    DEEPSEEK("deepseek", "DeepSeek Chat"),
    QWEN("qwen", "通义千问");

    private final String code;
    private final String displayName;

    /**
     * 从字符串解析模型枚举
     * @param s 模型名称字符串（不区分大小写）
     * @return 对应的ChatModel，默认返回DEEPSEEK
     */
    public static ChatModel fromString(String s) {
        if (s == null || s.isEmpty()) {
            return DEEPSEEK;
        }
        return "qwen".equalsIgnoreCase(s) ? QWEN : DEEPSEEK;
    }
}
