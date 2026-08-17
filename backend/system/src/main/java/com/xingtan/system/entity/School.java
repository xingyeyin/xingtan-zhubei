package com.xingtan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学校
 */
@Data
@TableName("school")
public class School {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String region;
    private String level;
    private String type;
    private Integer status;
    private LocalDateTime createdAt;
}
