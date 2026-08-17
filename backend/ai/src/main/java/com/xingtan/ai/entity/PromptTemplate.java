package com.xingtan.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词模板
 */
@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scene;
    private String subject;
    private String lessonType;
    private Integer version;
    private String content;
    private String config;
    private Integer status;
    private LocalDateTime createdAt;
}
