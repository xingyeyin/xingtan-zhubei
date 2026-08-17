package com.xingtan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师档案
 */
@Data
@TableName("teacher_profile")
public class TeacherProfile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String subjects;
    private String grades;
    private Integer teachingYears;
    private Boolean weakNetwork;
    private LocalDateTime createdAt;
}
