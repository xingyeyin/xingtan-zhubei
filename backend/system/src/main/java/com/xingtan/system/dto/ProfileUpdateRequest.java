package com.xingtan.system.dto;

import lombok.Data;

/**
 * 个人资料修改请求
 */
@Data
public class ProfileUpdateRequest {

    private String nickname;
    private String schoolName;
    private String subjects;
    private String grades;
}
