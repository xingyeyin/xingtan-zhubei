package com.xingtan.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 使用埋点
 */
@Data
@TableName("usage_log")
public class UsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long schoolId;
    private String action;
    private String scene;
    private Integer durationSec;
    private Integer costCents;
    private LocalDateTime createdAt;
}
