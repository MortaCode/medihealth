package com.myy.medihealth.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话实体
 */
@Data
@TableName("t_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    /** 用户ID */
    private String userId;

    /** 会话标题 */
    private String title;

    /** 使用的模型名称（deepseek / qwen） */
    private String modelName;

    /** 消息总数 */
    private Integer messageCount;

    /** 删除标记（0-正常，1-已删除） */
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
