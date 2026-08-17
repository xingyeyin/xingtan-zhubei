package com.xingtan.lesson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingtan.lesson.entity.LessonPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教案 Mapper
 */
@Mapper
public interface LessonPlanMapper extends BaseMapper<LessonPlan> {
}
