package com.myy.medihealth.thumb.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lua 脚本执行状态枚举。
 */
@Getter
@AllArgsConstructor
public enum LuaStateEnum {

    /** 操作成功 */
    SUCCESS(1, "操作成功"),

    /** 操作失败 */
    FAIL(0, "操作失败"),

    /** 已经点赞 */
    ALREADY_LIKED(2, "已经点赞"),

    /** 未点赞 */
    NOT_LIKED(3, "未点赞");

    private final int code;
    private final String message;

    /**
     * 根据 Lua 脚本返回码获取对应枚举值。
     */
    public static LuaStateEnum fromCode(long code) {
        for (LuaStateEnum state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return FAIL;
    }
}
