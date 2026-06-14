package com.myy.medihealth.login.vo;

import jakarta.validation.constraints.NotBlank;

public record WechatPhoneBindVo(
        @NotBlank(message = "绑定令牌不能为空")
        String bindToken,

        @NotBlank(message = "手机号不能为空")
        String mobile,

        @NotBlank(message = "短信验证码不能为空")
        String smsCode
) {
}
