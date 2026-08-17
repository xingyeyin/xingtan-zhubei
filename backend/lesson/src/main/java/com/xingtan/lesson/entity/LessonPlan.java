package com.xingtan.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教案
 */
@Data
@TableName("lesson_plan")
public class LessonPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long userId;
    private String title;
    private String subject;
    private String grade;
    private String textbook;
    private String lessonType;
    private String content;
    private Integer qualityScore;
    private Integer isPublic;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
