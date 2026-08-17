package com.xingtan.system.dto;

import lombok.Data;

/**
 * 登录返回
 */
@Data
public class LoginVO {

    private String token;
    private Long userId;
    private String nickname;
    private String role;
    private String schoolName;
}
