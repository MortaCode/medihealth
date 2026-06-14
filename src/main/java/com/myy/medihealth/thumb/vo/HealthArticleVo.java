package com.myy.medihealth.thumb.vo;

/**
 * 健康文章视图对象（用于创建/编辑文章）。
 */
public record HealthArticleVo(
        String title,
        String content,
        String authorType,
        String authorName,
        String category
) {
}
