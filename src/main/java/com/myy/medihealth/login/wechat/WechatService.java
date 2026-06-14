package com.myy.medihealth.login.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myy.medihealth.common.config.WechatConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatService {

    private final WechatConfig wechatConfig;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().build();

    /**
     * 通过小程序 jsCode 获取 openid（小程序登录）
     *
     * @param jsCode 前端调用 wx.login 获取的临时凭证
     * @return openid
     */
    public String getOpenId(String jsCode) {
        WechatConfig.MiniProgram mini = wechatConfig.getMiniProgram();

        String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                mini.getCode2sessionUrl(), mini.getAppId(), mini.getAppSecret(), jsCode);

        try {
            String response = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.value() != HttpStatus.OK.value(), (req, res) -> {
                        log.error("微信 code2session 请求失败: status={}", res.getStatusCode());
                        throw new RuntimeException("微信登录失败，请稍后重试");
                    })
                    .body(String.class);

            log.info("微信 code2session 响应: {}", response);

            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.has("errmsg") ? json.get("errmsg").asText() : "未知错误";
                log.error("微信 code2session 返回错误: errcode={}, errmsg={}", json.get("errcode").asInt(), errmsg);
                throw new RuntimeException("微信登录失败: " + errmsg);
            }

            String openid = json.get("openid").asText();
            log.info("获取 openid 成功: {}", openid);
            return openid;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信 code2session 调用异常", e);
            throw new RuntimeException("微信登录失败，请稍后重试");
        }
    }

    /**
     * 构建微信开放平台授权 URL
     *
     * @param state 业务状态参数
     * @return 授权跳转 URL
     */
    public String buildAuthorizeUrl(String state) {
        WechatConfig.OpenPlatform open = wechatConfig.getOpenPlatform();
        return String.format("%s?appid=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s#wechat_redirect",
                open.getAuthorizeUrl(),
                open.getAppId(),
                URLEncoder.encode(open.getCallbackUrl(), StandardCharsets.UTF_8),
                open.getScope(),
                state);
    }

    /**
     * 通过开放平台 OAuth code 获取用户信息（包含 unionid）
     *
     * @param code 开放平台授权回调返回的 code
     * @return 微信用户信息
     */
    public WechatUserInfo getOpenPlatformUserInfo(String code) {
        WechatConfig.OpenPlatform open = wechatConfig.getOpenPlatform();

        // 第一步：通过 code 获取 access_token 和 openid
        String accessTokenUrl = String.format("%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
                open.getAccessTokenUrl(), open.getAppId(), open.getAppSecret(), code);

        try {
            String tokenResponse = restClient.get()
                    .uri(accessTokenUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            log.info("微信 OAuth access_token 响应: {}", tokenResponse);

            JsonNode tokenJson = objectMapper.readTree(tokenResponse);

            if (tokenJson.has("errcode") && tokenJson.get("errcode").asInt() != 0) {
                String errmsg = tokenJson.has("errmsg") ? tokenJson.get("errmsg").asText() : "未知错误";
                log.error("微信 OAuth access_token 返回错误: {}", errmsg);
                throw new RuntimeException("微信授权登录失败: " + errmsg);
            }

            String accessToken = tokenJson.get("access_token").asText();
            String openid = tokenJson.get("openid").asText();
            String unionid = tokenJson.has("unionid") ? tokenJson.get("unionid").asText() : null;

            // 第二步：通过 access_token 和 openid 获取用户信息
            String userInfoUrl = String.format(
                    "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s&lang=zh_CN",
                    accessToken, openid);

            String userInfoResponse = restClient.get()
                    .uri(userInfoUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            log.info("微信 OAuth userinfo 响应: {}", userInfoResponse);

            JsonNode userInfoJson = objectMapper.readTree(userInfoResponse);

            if (userInfoJson.has("errcode") && userInfoJson.get("errcode").asInt() != 0) {
                String errmsg = userInfoJson.has("errmsg") ? userInfoJson.get("errmsg").asText() : "未知错误";
                log.error("微信 OAuth userinfo 返回错误: {}", errmsg);
                throw new RuntimeException("获取微信用户信息失败: " + errmsg);
            }

            WechatUserInfo info = new WechatUserInfo();
            info.setOpenid(openid);
            info.setUnionid(unionid);
            info.setNickname(userInfoJson.has("nickname") ? userInfoJson.get("nickname").asText() : null);
            info.setAvatar(userInfoJson.has("headimgurl") ? userInfoJson.get("headimgurl").asText() : null);

            log.info("获取微信用户信息成功: openid={}, unionid={}", openid, unionid);
            return info;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信 OAuth 调用异常", e);
            throw new RuntimeException("微信登录失败，请稍后重试");
        }
    }
}
