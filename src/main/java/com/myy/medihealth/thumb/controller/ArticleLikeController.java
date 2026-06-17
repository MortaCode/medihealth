package com.myy.medihealth.thumb.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.thumb.manage.CacheManager;
import com.myy.medihealth.thumb.service.LikeUPService;
import com.myy.medihealth.thumb.vo.MsgVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文章点赞。
 */
@Slf4j
@RestController
@RequestMapping("article")
@RequiredArgsConstructor
public class ArticleLikeController {

    private final LikeUPService likeUPService;
    private final CacheManager cacheManager;


    /**
     * 点赞
     * @param request
     * @param articleId
     * @return
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
}
