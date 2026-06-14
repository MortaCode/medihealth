package com.myy.medihealth.login.vo;

import jakarta.validation.constraints.NotNull;

public record WechatLoginVo(
        @NotNull(message = "微信授权码不能为空")
        String code
) {
}
