package com.xingtan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生成绩
 */
@Data
@TableName("student_score")
public class StudentScore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String subject;
    private BigDecimal score;
    private LocalDateTime createdAt;
}
