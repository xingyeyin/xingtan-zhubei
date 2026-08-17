package com.xingtan.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容审核日志
 */
@Data
@TableName("content_review_log")
public class ContentReviewLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String contentType;
    private String result;
    private String detail;
    private LocalDateTime createdAt;
}
