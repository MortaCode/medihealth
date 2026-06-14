package com.myy.medihealth.thumb.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点赞记录实体。
 */
@Data
@TableName("t_like_record")
public class LikeRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    /** 点赞用户ID */
    private String userId;

    /** 文章ID */
    private String articleId;

    /** 点赞时间 */
    private LocalDateTime createTime;
}
