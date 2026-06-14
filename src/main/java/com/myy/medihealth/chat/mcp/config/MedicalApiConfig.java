package com.myy.medihealth.chat.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 医疗API配置 - 药品、疾病、医院、健康建议等外部API配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "medical.api")
public class MedicalApiConfig {

    private Drug drug = new Drug();
    private Disease disease = new Disease();
    private Hospital hospital = new Hospital();
    private Health health = new Health();
    private Http http = new Http();

    @Data
    public static class Drug {
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class Disease {
        private String baseUrl;
    }

    @Data
    public static class Hospital {
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class Health {
        private String baseUrl;
    }

    @Data
    public static class Http {
        private int connectTimeout = 5000;
        private int readTimeout = 30000;

        public Duration getConnectTimeoutDuration() {
            return Duration.ofMillis(connectTimeout);
        }

        public Duration getReadTimeoutDuration() {
            return Duration.ofMillis(readTimeout);
        }
    }

    /**
     * 创建药品查询 RestClient（使用 common 配置的 bean 作为基础）
     */
    @Bean
    public RestClient drugRestClient(RestClient.Builder builder) {
        if (drug.getBaseUrl() == null || drug.getBaseUrl().isEmpty()) {
            return builder.build();
        }
        return builder.baseUrl(drug.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (drug.getApiKey() != null ? drug.getApiKey() : ""))
                .build();
    }

    /**
     * 创建疾病查询 RestClient
     */
    @Bean
    public RestClient diseaseRestClient(RestClient.Builder builder) {
        if (disease.getBaseUrl() == null || disease.getBaseUrl().isEmpty()) {
            return builder.build();
        }
        return builder.baseUrl(disease.getBaseUrl()).build();
    }

    /**
     * 创建医院查询 RestClient
     */
    @Bean
    public RestClient hospitalRestClient(RestClient.Builder builder) {
        if (hospital.getBaseUrl() == null || hospital.getBaseUrl().isEmpty()) {
            return builder.build();
        }
        return builder.baseUrl(hospital.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (hospital.getApiKey() != null ? hospital.getApiKey() : ""))
                .build();
    }

    /**
     * 创建健康建议 RestClient
     */
    @Bean
    public RestClient healthRestClient(RestClient.Builder builder) {
        if (health.getBaseUrl() == null || health.getBaseUrl().isEmpty()) {
            return builder.build();
        }
        return builder.baseUrl(health.getBaseUrl()).build();
    }
}
