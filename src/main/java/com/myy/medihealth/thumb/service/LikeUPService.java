package com.myy.medihealth.thumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.login.entity.User;
import com.myy.medihealth.login.service.UserService;
import com.myy.medihealth.thumb.entity.LikeRecord;
import com.myy.medihealth.thumb.mapper.LikeRecordMapper;
import com.myy.medihealth.thumb.vo.LuaStateEnum;
import com.myy.medihealth.thumb.vo.MsgVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 点赞/取消点赞服务。
 * 使用 Redis Lua 脚本原子性地处理点赞操作，通过定时任务异步同步到 MySQL。
 */
@Service
@AllArgsConstructor
public class LikeUPService extends ServiceImpl<LikeRecordMapper, LikeRecord> {

    private static final Logger log = LoggerFactory.getLogger(LikeUPService.class);

    private static final String LIKE_USER_KEY_PREFIX = "like:";
    private static final String LIKE_TEMP_KEY_PREFIX = "like:temp:";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final DefaultRedisScript<Long> thumbScript;
    private final DefaultRedisScript<Long> unthumbScript;

    /**
     * 点赞/取消点赞切换。
     *
     * @param request   HTTP 请求（用于获取登录用户）
     * @param articleId 文章ID
     * @return 操作结果消息
     */
    public MsgVo like(HttpServletRequest request, String articleId) {
        // 1. 获取登录用户
        User loginUser = userService.getLoginUser(request);
        String userId = loginUser.getId();

        // 2. 构建 Redis Key
        String timeslice = timeslice();
        String thumbKey = LIKE_USER_KEY_PREFIX + userId;
        String tempThumbKey = LIKE_TEMP_KEY_PREFIX + timeslice;

        // 3. 检查当前点赞状态
        boolean alreadyLiked = hasLiked(userId, articleId);

        if (!alreadyLiked) {
            // 4. 未点赞 → 执行点赞
            Long result = redisTemplate.execute(
                    thumbScript,
                    List.of(tempThumbKey, thumbKey),
                    userId,
                    articleId
            );
            LuaStateEnum state = LuaStateEnum.fromCode(result != null ? result : 0);
            if (state == LuaStateEnum.SUCCESS) {
                log.info("点赞成功 userId={}, articleId={}", userId, articleId);
                return MsgVo.of("点赞成功", articleId);
            } else if (state == LuaStateEnum.ALREADY_LIKED) {
                return MsgVo.of("已经点赞过了", articleId);
            } else {
                return MsgVo.of("点赞失败", articleId);
            }
        } else {
            // 5. 已点赞 → 执行取消点赞
            Long result = redisTemplate.execute(
                    unthumbScript,
                    List.of(tempThumbKey, thumbKey),
                    userId,
                    articleId
            );
            LuaStateEnum state = LuaStateEnum.fromCode(result != null ? result : 0);
            if (state == LuaStateEnum.SUCCESS) {
                log.info("取消点赞成功 userId={}, articleId={}", userId, articleId);
                return MsgVo.of("取消点赞成功", articleId);
            } else if (state == LuaStateEnum.NOT_LIKED) {
                return MsgVo.of("还未点赞", articleId);
            } else {
                return MsgVo.of("取消点赞失败", articleId);
            }
        }
    }

    /**
     * 判断用户是否已对某文章点赞。
     * 先查 Redis Hash，若不存在再查数据库。
     *
     * @param userId    用户ID
     * @param articleId 文章ID
     * @return true=已点赞
     */
    public boolean hasLiked(String userId, String articleId) {
        // 先查 Redis Hash
        String userKey = LIKE_USER_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.opsForHash().hasKey(userKey, articleId);
        if (Boolean.TRUE.equals(exists)) {
            return true;
        }

        // Redis 中没有，查数据库
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, userId)
               .eq(LikeRecord::getArticleId, articleId);
        long count = count(wrapper);
        return count > 0;
    }

    /**
     * 获取当前时间片（10秒粒度）。
     * 格式：yyyy-MM-dd HH:mm:XX，其中 XX = (second / 10) * 10
     */
    public String timeslice() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:");
        Date now = new Date();
        int second = now.getSeconds();
        int sliceSecond = (second / 10) * 10;
        return sdf.format(now) + String.format("%02d", sliceSecond);
    }
}
