package com.xingtan.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教案区块
 */
@Data
@TableName("lesson_section")
public class LessonSection {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long lessonPlanId;
    private String sectionType;
    private Integer seq;
    private String content;
    private String standardRef;
}
