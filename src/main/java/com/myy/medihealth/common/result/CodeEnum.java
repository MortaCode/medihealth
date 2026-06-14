package com.myy.medihealth.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CodeEnum {
    SUCCESS(200, "操作成功"),
    ERROR(500, "系统异常"),
    BIZ_ERROR(400, "业务异常"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在");

    private final int code;
    private final String message;
}
