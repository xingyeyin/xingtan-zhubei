package com.xingtan.system.dto;

import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterRequest {

    private String phone;
    private String password;
    private String nickname;
    private String schoolName;
    private String subjects;
}
