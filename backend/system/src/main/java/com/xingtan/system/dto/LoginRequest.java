package com.xingtan.system.dto;

import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginRequest {

    private String phone;
    private String password;
}
