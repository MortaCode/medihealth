package com.myy.medihealth.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsConfig {

    private String provider;
    private Aliyun aliyun = new Aliyun();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Aliyun {
        private String accessKeyId;
        private String accessKeySecret;
        private String signName;
        private String templateCode;
        private String endpoint;
    }

    @Data
    public static class RateLimit {
        private int perMinute;
        private int perHour;
        private int perDay;
    }
}
