package com.myy.medihealth.thumb.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 健康资讯 / 医学文章实体。
 */
@Data
@TableName("t_health_article")
public class HealthArticle implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String id;

    /** 作者用户ID */
    private String userId;

    /** 文章标题 */
    private String title;

    /** 封面图片URL */
    private String coverImg;

    /** 文章正文内容 */
    private String content;

    /** 作者类型：DOCTOR-医生 / USER-用户 / SYSTEM-系统 */
    private String authorType;

    /** 作者名称 */
    private String authorName;

    /** 文章分类：疾病知识 / 用药指南 / 养生保健 / 医学科普 / 饮食健康 / 运动健身 */
    private String category;

    /** 点赞数 */
    private Integer likeCount;

    /** 浏览次数 */
    private Integer viewCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
