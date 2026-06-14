package com.myy.medihealth.login.sms;

public interface SmsProvider {

    /**
     * 发送短信验证码
     *
     * @param mobile 手机号
     * @param code   验证码
     */
    void send(String mobile, String code);
}
