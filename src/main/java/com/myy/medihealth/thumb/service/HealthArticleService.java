package com.myy.medihealth.thumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.thumb.entity.HealthArticle;
import com.myy.medihealth.thumb.mapper.HealthArticleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 健康文章服务。
 */
@Service
public class HealthArticleService extends ServiceImpl<HealthArticleMapper, HealthArticle> {

    /**
     * 获取全部文章，按创建时间倒序。
     */
    public List<HealthArticle> getAll() {
        LambdaQueryWrapper<HealthArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(HealthArticle::getCreateTime);
        return list(wrapper);
    }

    /**
     * 按ID查询单篇文章。
     */
    public HealthArticle searchById(String articleId) {
        return getById(articleId);
    }

    /**
     * 批量按ID查询文章。
     */
    public List<HealthArticle> searchByIds(List<String> ids) {
        return listByIds(ids);
    }

    /**
     * 按分类查询文章。
     */
    public List<HealthArticle> getByCategory(String category) {
        LambdaQueryWrapper<HealthArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthArticle::getCategory, category)
               .orderByDesc(HealthArticle::getCreateTime);
        return list(wrapper);
    }
}
