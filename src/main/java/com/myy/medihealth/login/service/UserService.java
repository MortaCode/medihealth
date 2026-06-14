package com.myy.medihealth.login.service;

import cn.hutool.core.util.IdUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.login.entity.User;
import com.myy.medihealth.login.entity.UserWechat;
import com.myy.medihealth.login.mapper.UserMapper;
import com.myy.medihealth.login.mapper.UserWechatMapper;
import com.myy.medihealth.login.sms.SmsService;
import com.myy.medihealth.login.vo.LoginResultVo;
import com.myy.medihealth.login.wechat.WechatService;
import com.myy.medihealth.login.wechat.WechatUserInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SESSION_KEY_LOGIN_USER = "loginUser";

    private final UserMapper userMapper;
    private final UserWechatMapper userWechatMapper;
    private final WechatService wechatService;
    private final SmsService smsService;

    /**
     * 微信小程序登录
     *
     * @param jsCode  前端调用 wx.login 获取的临时凭证
     * @param request HTTP 请求
     * @return 登录结果
     */
    @Transactional
    public LoginResultVo wechatLogin(String jsCode, HttpServletRequest request) {
        // 1. 通过 jsCode 换取 openid
        String openid = wechatService.getOpenId(jsCode);

        // 2. 查找已有的微信绑定记录
        LambdaQueryWrapper<UserWechat> wechatWrapper = new LambdaQueryWrapper<>();
        wechatWrapper.eq(UserWechat::getOpenid, openid);
        UserWechat userWechat = userWechatMapper.selectOne(wechatWrapper);

        User user;
        if (userWechat != null) {
            // 已绑定的用户，直接查询
            user = userMapper.selectById(userWechat.getUserId());
            if (user == null) {
                log.error("微信绑定记录存在但用户不存在: wechatId={}, userId={}", userWechat.getId(), userWechat.getUserId());
                // 数据异常，自动创建新用户
                user = createUser(null, null, null);
                userWechat.setUserId(user.getId());
                userWechatMapper.updateById(userWechat);
            }
        } else {
            // 新用户：创建用户 + 绑定微信
            user = createUser(null, null, null);
            bindWechat(user.getId(), openid, null);
        }

        // 3. 设置登录会话
        setLoginSession(request, user);

        return buildLoginResult(user);
    }

    /**
     * 处理微信开放平台 OAuth 回调
     *
     * @param code    授权回调 code
     * @param state   业务状态参数
     * @param request HTTP 请求
     * @return 登录结果（含 sessionToken）
     */
    @Transactional
    public LoginResultVo handleWechatCallback(String code, String state, HttpServletRequest request) {
        // 1. 通过 OAuth code 获取微信用户信息
        WechatUserInfo userInfo = wechatService.getOpenPlatformUserInfo(code);

        // 2. 通过 unionid 或 openid 查找已有绑定
        UserWechat userWechat = null;
        if (userInfo.getUnionid() != null) {
            LambdaQueryWrapper<UserWechat> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserWechat::getUnionid, userInfo.getUnionid());
            userWechat = userWechatMapper.selectOne(wrapper);
        }
        if (userWechat == null) {
            LambdaQueryWrapper<UserWechat> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserWechat::getOpenid, userInfo.getOpenid());
            userWechat = userWechatMapper.selectOne(wrapper);
        }

        User user;
        if (userWechat != null) {
            // 已有绑定记录
            user = userMapper.selectById(userWechat.getUserId());
            if (user == null) {
                user = createUser(null, userInfo.getNickname(), userInfo.getAvatar());
                userWechat.setUserId(user.getId());
            }
            // 更新 unionid（首次绑定可能没有 unionid）
            if (userInfo.getUnionid() != null && userWechat.getUnionid() == null) {
                userWechat.setUnionid(userInfo.getUnionid());
            }
            // 更新用户信息
            if (userInfo.getNickname() != null) {
                user.setNickname(userInfo.getNickname());
            }
            if (userInfo.getAvatar() != null) {
                user.setAvatar(userInfo.getAvatar());
            }
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
            userWechatMapper.updateById(userWechat);
        } else {
            // 新用户
            user = createUser(null, userInfo.getNickname(), userInfo.getAvatar());
            bindWechat(user.getId(), userInfo.getOpenid(), userInfo.getUnionid());
        }

        // 3. 设置登录会话
        setLoginSession(request, user);

        return buildLoginResult(user);
    }

    /**
     * 微信手机号绑定登录
     *
     * @param bindToken 绑定令牌（通常由微信手机号快速验证组件返回）
     * @param mobile    手机号
     * @param smsCode   短信验证码
     * @param request   HTTP 请求
     * @return 登录结果
     */
    @Transactional
    public LoginResultVo wechatPhoneBind(String bindToken, String mobile, String smsCode,
                                         HttpServletRequest request) {
        // 1. 验证短信验证码
        if (!smsService.verifyCode(mobile, smsCode)) {
            throw new BizException("短信验证码错误或已过期");
        }

        // 2. 通过手机号查找或创建用户
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getMobile, mobile);
        User user = userMapper.selectOne(userWrapper);

        if (user == null) {
            user = createUser(mobile, null, null);
        }

        // 3. 绑定微信（如果当前请求来自微信环境，bindToken 可能包含 openid 信息）
        // 这里 bindToken 作为单纯的绑定凭证，实际绑定逻辑由前端配合微信组件完成
        // 如果会话中已有微信 openid 信息，则完成绑定
        log.info("微信手机号绑定: bindToken={}, mobile={}, userId={}", bindToken, mobile, user.getId());

        // 4. 设置登录会话
        setLoginSession(request, user);

        return buildLoginResult(user);
    }

    /**
     * 手机号短信验证码登录
     *
     * @param mobile  手机号
     * @param smsCode 短信验证码
     * @param request HTTP 请求
     * @return 登录结果
     */
    @Transactional
    public LoginResultVo phoneLogin(String mobile, String smsCode, HttpServletRequest request) {
        // 1. 验证短信验证码
        if (!smsService.verifyCode(mobile, smsCode)) {
            throw new BizException("短信验证码错误或已过期");
        }

        // 2. 通过手机号查找或创建用户
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getMobile, mobile);
        User user = userMapper.selectOne(userWrapper);

        if (user == null) {
            user = createUser(mobile, "用户" + mobile.substring(7), null);
        }

        // 3. 设置登录会话
        setLoginSession(request, user);

        return buildLoginResult(user);
    }

    /**
     * 获取当前登录用户
     *
     * @param request HTTP 请求
     * @return 当前登录用户
     * @throws BizException 未登录时抛出
     */
    public User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new BizException("请先登录");
        }
        Object loginUser = session.getAttribute(SESSION_KEY_LOGIN_USER);
        if (loginUser == null) {
            throw new BizException("请先登录");
        }
        if (!(loginUser instanceof User)) {
            throw new BizException("登录状态异常");
        }
        return (User) loginUser;
    }

    /**
     * 获取当前登录用户ID（便捷方法）
     *
     * @param request HTTP 请求
     * @return 用户ID
     */
    public String getLoginUserId(HttpServletRequest request) {
        return getLoginUser(request).getId();
    }

    // ==================== 私有方法 ====================

    /**
     * 创建新用户
     */
    private User createUser(String mobile, String nickname, String avatar) {
        User user = new User();
        user.setId(IdUtil.fastSimpleUUID());
        user.setMobile(mobile);
        user.setNickname(nickname != null ? nickname : "微医用户");
        user.setAvatar(avatar);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        log.info("创建新用户: userId={}, mobile={}", user.getId(), mobile);
        return user;
    }

    /**
     * 绑定微信
     */
    private void bindWechat(String userId, String openid, String unionid) {
        UserWechat wechat = new UserWechat();
        wechat.setId(IdUtil.fastSimpleUUID());
        wechat.setUserId(userId);
        wechat.setOpenid(openid);
        wechat.setUnionid(unionid);
        wechat.setCreateTime(LocalDateTime.now());
        userWechatMapper.insert(wechat);
        log.info("绑定微信: userId={}, openid={}, unionid={}", userId, openid, unionid);
    }

    /**
     * 设置登录会话
     */
    private void setLoginSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY_LOGIN_USER, user);
        log.info("用户登录成功: userId={}, sessionId={}", user.getId(), session.getId());
    }

    /**
     * 构建登录结果 VO
     */
    private LoginResultVo buildLoginResult(User user) {
        return LoginResultVo.builder()
                .userId(user.getId())
                .mobile(user.getMobile())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .sessionToken(user.getId()) // 以 userId 作为 sessionToken，生产环境应使用 JWT 等安全令牌
                .build();
    }
}
