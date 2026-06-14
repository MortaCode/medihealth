package com.myy.medihealth.thumb.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.thumb.entity.HealthArticle;
import com.myy.medihealth.thumb.job.SyncLike2DBCompensateJob;
import com.myy.medihealth.thumb.manage.CacheManager;
import com.myy.medihealth.thumb.service.HealthArticleService;
import com.myy.medihealth.thumb.service.HeavyKeeper;
import com.myy.medihealth.thumb.service.LikeUPService;
import com.myy.medihealth.thumb.vo.ArticleThumbResult;
import com.myy.medihealth.thumb.vo.MsgVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 健康文章控制器。
 * 提供文章查询、点赞/取消点赞、数据清理等接口。
 */
@RestController
@RequestMapping("health/article")
@RequiredArgsConstructor
public class HealthArticleController {

    private static final Logger log = LoggerFactory.getLogger(HealthArticleController.class);

    private final HealthArticleService healthArticleService;
    private final LikeUPService likeUPService;
    private final CacheManager cacheManager;
    private final HeavyKeeper heavyKeeper;
    private final SyncLike2DBCompensateJob compensateJob;

    /**
     * 按ID查询单篇文章（含点赞数和当前用户点赞状态）。
     * 优先走多级缓存。
     */
    @GetMapping("/searchById")
    public Result<ArticleThumbResult> searchById(HttpServletRequest request,
                                                  @RequestParam String articleId) {
        HealthArticle article = cacheManager.getArticle(articleId);
        if (article == null) {
            return Result.error(404, "文章不存在");
        }

        // 判断当前用户是否已点赞
        boolean liked = false;
        try {
            String userId = (String) request.getAttribute("loginUserId");
            if (userId != null) {
                liked = likeUPService.hasLiked(userId, articleId);
            }
        } catch (Exception ignored) {
            // 未登录用户不返回点赞状态
        }

        int likeCount = article.getLikeCount() == null ? 0 : article.getLikeCount();
        return Result.success(new ArticleThumbResult(likeCount, liked));
    }

    /**
     * 批量按ID查询文章。
     */
    @GetMapping("/searchByIds")
    public Result<List<HealthArticle>> searchByIds(@RequestParam String articleIds) {
        List<String> ids = Arrays.stream(articleIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Result.error("参数 articleIds 不能为空");
        }
        List<HealthArticle> articles = healthArticleService.searchByIds(ids);
        return Result.success(articles);
    }

    /**
     * 获取全部文章（按创建时间倒序）。
     */
    @GetMapping("/all")
    public Result<List<HealthArticle>> getAll() {
        return Result.success(healthArticleService.getAll());
    }

    /**
     * 按分类查询文章。
     */
    @GetMapping("/category")
    public Result<List<HealthArticle>> getByCategory(@RequestParam String category) {
        return Result.success(healthArticleService.getByCategory(category));
    }

    /**
     * 点赞/取消点赞切换（需要登录）。
     * 通过 Redis Lua 脚本原子性地处理点赞操作。
     */
    @GetMapping("/like")
    public Result<MsgVo> like(HttpServletRequest request,
                               @RequestParam String articleId) {
        if (articleId == null || articleId.isBlank()) {
            return Result.error("articleId 不能为空");
        }
        MsgVo msg = likeUPService.like(request, articleId);
        // 点赞/取消点赞后刷新缓存
        cacheManager.evictCache(articleId);
        return Result.success(msg);
    }

    /**
     * 查询用户是否已对文章点赞。
     */
    @GetMapping("/hasLiked")
    public Result<Boolean> hasLiked(HttpServletRequest request,
                                     @RequestParam String articleId) {
        try {
            String userId = (String) request.getAttribute("loginUserId");
            if (userId == null) {
                return Result.success(false);
            }
            return Result.success(likeUPService.hasLiked(userId, articleId));
        } catch (Exception e) {
            return Result.success(false);
        }
    }

    /**
     * 获取热门文章 Top K（管理员工具）。
     */
    @GetMapping("/hot")
    public Result<List<String>> getHotArticles(@RequestParam(defaultValue = "10") int k) {
        return Result.success(heavyKeeper.top(Math.min(k, 20)));
    }

    /**
     * 清除所有点赞相关的 Redis 数据（管理员工具）。
     * 谨慎使用。
     */
    @GetMapping("/clearLikeData")
    public Result<String> clearLikeData() {
        long deletedCount = compensateJob.clearAllLikeData();
        log.warn("管理员清除了所有点赞Redis数据，共 {} 个键", deletedCount);
        return Result.success("已清除 " + deletedCount + " 个 Redis 点赞相关键");
    }
}
