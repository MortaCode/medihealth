package com.myy.medihealth.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
@Data
@TableName("t_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    /** 所属会话ID */
    private String sessionId;

    /** 消息角色（user / assistant / system） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息类型（text / image / file），默认text */
    private String messageType;

    /** Token消耗数量 */
    private Integer tokenCount;

    /** 删除标记（0-正常，1-已删除） */
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
