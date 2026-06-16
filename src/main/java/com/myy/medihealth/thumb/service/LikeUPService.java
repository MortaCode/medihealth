package com.myy.medihealth.thumb.service;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.medihealth.login.entity.User;
import com.myy.medihealth.login.service.UserService;
import com.myy.medihealth.thumb.entity.LikeRecord;
import com.myy.medihealth.thumb.mapper.LikeRecordMapper;
import com.myy.medihealth.thumb.vo.MsgVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 点赞/取消点赞服务。
 * 使用 Redis Lua 脚本原子性地切换点赞状态，通过定时任务异步同步到 MySQL。
 */
@Service
@AllArgsConstructor
public class LikeUPService extends ServiceImpl<LikeRecordMapper, LikeRecord> {

    private static final Logger log = LoggerFactory.getLogger(LikeUPService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final DefaultRedisScript<Long> thumbToggleScript;

    public static final String LIKE_USER_KEY_PREFIX = "like:";
    public static final String LIKE_TEMP_KEY_PREFIX = "like:temp:";

    /**
     * 点赞/取消点赞切换（单次 Lua 原子执行，无竞态）。
     *
     * @param request   HTTP 请求（用于获取登录用户）
     * @param articleId 文章ID
     * @return 操作结果消息
     */
    public MsgVo like(HttpServletRequest request, String articleId) {
        User loginUser = userService.getLoginUser(request);
        String userId = loginUser.getId();

        String timeslice = timeslice();
        String userKey = thumbKey(userId);
        String tempThumbKey = tempThumbKey(timeslice);

        Long result = redisTemplate.execute(
                thumbToggleScript,
                List.of(tempThumbKey, userKey),
                userId,
                articleId
        );

        if (result != null && result == 1) {
            log.info("点赞成功 userId={}, articleId={}", userId, articleId);
            return MsgVo.of("点赞成功", articleId);
        } else if (result != null && result == 2) {
            log.info("取消点赞成功 userId={}, articleId={}", userId, articleId);
            return MsgVo.of("取消点赞成功", articleId);
        } else {
            log.warn("点赞操作异常 result={}, userId={}, articleId={}", result, userId, articleId);
            return MsgVo.of("操作失败", articleId);
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
        String userKey = thumbKey(userId);
        Boolean exists = redisTemplate.opsForHash().hasKey(userKey, articleId);
        if (Boolean.TRUE.equals(exists)) {return true;}

        // Redis 中没有，查数据库
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, userId)
               .eq(LikeRecord::getArticleId, articleId);
        long count = count(wrapper);
        return count > 0;
    }

    /**
     * 获取当前时间片（10秒粒度）。
     */
    public String timeslice() {
        return timeslice(new Date());
    }

    /**
     * 获取指定日期的时间片。
     */
    public String timeslice(Date date) {
        int second = DateUtil.second(date);
        int sliceSecond = (second / 10) * 10;
        return DateUtil.format(date, "yyyy-MM-dd HH:mm:") + String.format("%02d", sliceSecond);  //十进制、用零补充、长度为2
    }

    /**
     * 获取上一个时间片（用于定时同步读取已完成的时间片数据）。
     */
    public String previousTimeslice() {
        Date now = new Date();
        int second = (DateUtil.second(now) / 10 - 1) * 10;
        if (second == -10) {
            second = 50;
            now = DateUtil.offsetMinute(now, -1);
        }
        return DateUtil.format(now, "yyyy-MM-dd HH:mm:") + String.format("%02d", second);
    }

    public static String tempThumbKey(String time) {
        return LIKE_TEMP_KEY_PREFIX + time;
    }

    public static String thumbKey(String userId) {
        return LIKE_USER_KEY_PREFIX + userId;
    }
}
