package com.xingtan.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生成任务
 */
@Data
@TableName("generation_task")
public class GenerationTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private String params;
    private String provider;
    private String status;
    private String errorMessage;
    private Integer costCents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
