package com.myy.medihealth.thumb.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.thumb.entity.HealthArticle;
import com.myy.medihealth.thumb.mapper.HealthArticleMapper;
import com.myy.medihealth.thumb.vo.HealthArticleVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康文章服务。
 */
@Service
public class HealthArticleService extends ServiceImpl<HealthArticleMapper, HealthArticle> {

    public List<HealthArticle> getAll() {
        LambdaQueryWrapper<HealthArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(HealthArticle::getCreateTime);
        return list(wrapper);
    }

    public HealthArticle searchById(String articleId) {
        return getById(articleId);
    }

    public List<HealthArticle> searchByIds(List<String> ids) {
        return listByIds(ids);
    }

    public List<HealthArticle> getByCategory(String category) {
        LambdaQueryWrapper<HealthArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthArticle::getCategory, category)
               .orderByDesc(HealthArticle::getCreateTime);
        return list(wrapper);
    }

    @Transactional
    public HealthArticle createArticle(HealthArticleVo vo, String userId, String authorName) {
        HealthArticle article = new HealthArticle();
        article.setId(IdUtil.fastSimpleUUID());
        article.setUserId(userId);
        article.setTitle(vo.title());
        article.setContent(vo.content());
        article.setAuthorType(vo.authorType());
        article.setAuthorName(authorName);
        article.setCategory(vo.category());
        article.setLikeCount(0);
        article.setViewCount(0);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        save(article);
        return article;
    }

    @Transactional
    public HealthArticle updateArticle(String articleId, HealthArticleVo vo, String userId) {
        HealthArticle existing = getById(articleId);
        if (existing == null) {
            throw new BizException("文章不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new BizException("无权修改他人文章");
        }
        existing.setTitle(vo.title());
        existing.setContent(vo.content());
        existing.setAuthorType(vo.authorType());
        existing.setCategory(vo.category());
        existing.setUpdateTime(LocalDateTime.now());
        updateById(existing);
        return existing;
    }

    @Transactional
    public void deleteArticle(String articleId, String userId) {
        HealthArticle existing = getById(articleId);
        if (existing == null) {
            throw new BizException("文章不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new BizException("无权删除他人文章");
        }
        removeById(articleId);
    }
}
