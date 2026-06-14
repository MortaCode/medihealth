package com.myy.medihealth.thumb.vo;

/**
 * 通用消息返回视图。
 */
public record MsgVo(String message, String data) {

    public static MsgVo of(String message, String data) {
        return new MsgVo(message, data);
    }
}
