package com.myy.medihealth.chat.vo;

import lombok.Data;

/**
 * 聊天消息VO - 前端请求参数封装
 */
@Data
public class ChatMessageVo {

    /** 会话ID */
    private String sessionId;

    /** 用户输入内容 */
    private String userInput;

    /** 选择的模型名称（deepseek / qwen） */
    private String modelName;
}
