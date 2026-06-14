package com.myy.medihealth.login.controller;

import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.login.entity.User;
import com.myy.medihealth.login.service.UserService;
import com.myy.medihealth.login.sms.SmsService;
import com.myy.medihealth.login.vo.LoginResultVo;
import com.myy.medihealth.login.vo.PhoneLoginVo;
import com.myy.medihealth.login.vo.SmsSendVo;
import com.myy.medihealth.login.vo.WechatLoginVo;
import com.myy.medihealth.login.vo.WechatPhoneBindVo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("login")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SmsService smsService;

    /**
     * 微信小程序登录
     */
    @PostMapping("/wechat")
    public Result<LoginResultVo> wechatLogin(@Validated @RequestBody WechatLoginVo vo,
                                              HttpServletRequest request) {
        log.info("微信小程序登录请求: code={}", vo.code());
        LoginResultVo result = userService.wechatLogin(vo.code(), request);
        return Result.success(result);
    }

    /**
     * 微信开放平台 OAuth 回调
     * GET /login/wechat/callback?code=xxx&state=xxx
     */
    @GetMapping("/wechat/callback")
    public void wechatCallback(@RequestParam String code,
                               @RequestParam String state,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        log.info("微信开放平台回调: code={}, state={}", code, state);
        LoginResultVo result = userService.handleWechatCallback(code, state, request);

        // 构建前端重定向 URL，携带登录信息
        String redirectUrl = String.format("%s?userId=%s&sessionToken=%s&nickname=%s&avatar=%s",
                state,
                result.getUserId(),
                result.getSessionToken(),
                result.getNickname() != null ? java.net.URLEncoder.encode(result.getNickname(), "UTF-8") : "",
                result.getAvatar() != null ? java.net.URLEncoder.encode(result.getAvatar(), "UTF-8") : "");

        response.sendRedirect(redirectUrl);
    }

    /**
     * 微信手机号绑定登录
     */
    @PostMapping("/bind/wechat")
    public Result<LoginResultVo> wechatPhoneBind(@Validated @RequestBody WechatPhoneBindVo vo,
                                                  HttpServletRequest request) {
        log.info("微信手机号绑定请求: mobile={}", vo.mobile());
        LoginResultVo result = userService.wechatPhoneBind(vo.bindToken(), vo.mobile(), vo.smsCode(), request);
        return Result.success(result);
    }

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms/send")
    public Result<Map<String, String>> sendSms(@Validated @RequestBody SmsSendVo vo) {
        log.info("短信发送请求: mobile={}", vo.mobile());
        smsService.sendCode(vo.mobile());
        Map<String, String> data = new HashMap<>();
        data.put("mobile", vo.mobile());
        data.put("message", "验证码已发送");
        return Result.success(data);
    }

    /**
     * 手机号短信验证码登录
     */
    @PostMapping("/phone")
    public Result<LoginResultVo> phoneLogin(@Validated @RequestBody PhoneLoginVo vo,
                                             HttpServletRequest request) {
        log.info("手机号登录请求: mobile={}", vo.mobile());
        LoginResultVo result = userService.phoneLogin(vo.mobile(), vo.smsCode(), request);
        return Result.success(result);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/getCur")
    public Result<Map<String, Object>> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", loginUser.getId());
        data.put("mobile", loginUser.getMobile());
        data.put("nickname", loginUser.getNickname());
        data.put("avatar", loginUser.getAvatar());
        data.put("createTime", loginUser.getCreateTime());
        return Result.success(data);
    }
}
