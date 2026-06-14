package com.myy.medihealth.login.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResultVo {

    private String userId;

    private String mobile;

    private String nickname;

    private String avatar;

    private String sessionToken;
}
