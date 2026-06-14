package com.myy.medihealth.login.vo;

import jakarta.validation.constraints.NotBlank;

public record PhoneLoginVo(
        @NotBlank(message = "手机号不能为空")
        String mobile,

        @NotBlank(message = "短信验证码不能为空")
        String smsCode
) {
}
