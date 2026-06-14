package com.myy.medihealth.login.sms;

import com.myy.medihealth.common.config.SmsConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "aliyun")
public class AliyunSmsProvider implements SmsProvider {

    private final SmsConfig smsConfig;

    @Override
    public void send(String mobile, String code) {
        log.info("发送短信验证码到{}: {}", mobile, code);

        SmsConfig.Aliyun aliyun = smsConfig.getAliyun();
        log.info("阿里云短信配置 - endpoint: {}, signName: {}, templateCode: {}",
                aliyun.getEndpoint(), aliyun.getSignName(), aliyun.getTemplateCode());

        // TODO: 接入阿里云短信SDK后替换为真实调用
        // DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou",
        //         aliyun.getAccessKeyId(), aliyun.getAccessKeySecret());
        // IAcsClient client = new DefaultAcsClient(profile);
        // CommonRequest request = new CommonRequest();
        // request.setSysMethod(MethodType.POST);
        // request.setSysDomain(aliyun.getEndpoint());
        // ...
        throw new RuntimeException("请配置阿里云短信服务");
    }
}
