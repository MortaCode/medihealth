package com.myy.medihealth.chat.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天快照实体 - 存储对话历史的压缩快照
 */
@Data
@TableName("t_snapshot")
public class ChatSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    /** 所属会话ID */
    private String sessionId;

    /** 快照二进制数据（JSON序列化，对应LONGBLOB） */
    private byte[] snapshotData;

    /** 快照时的消息数量 */
    private Integer messageCount;

    /** 快照数据校验和 */
    private String checksum;

    /** 删除标记（0-正常，1-已删除） */
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
