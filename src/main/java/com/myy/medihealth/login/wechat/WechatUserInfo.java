package com.myy.medihealth.login.wechat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WechatUserInfo {

    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;
}
