package com.myy.medihealth.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {

    private MiniProgram miniProgram = new MiniProgram();
    private OpenPlatform openPlatform = new OpenPlatform();
    private Http http = new Http();

    @Data
    public static class MiniProgram {
        private String appId;
        private String appSecret;
        private String code2sessionUrl;
    }

    @Data
    public static class OpenPlatform {
        private String appId;
        private String appSecret;
        private String authorizeUrl;
        private String accessTokenUrl;
        private String callbackUrl;
        private String scope;
    }

    @Data
    public static class Http {
        private int connectTimeout;
        private int readTimeout;
        private int maxRetry;
    }
}
