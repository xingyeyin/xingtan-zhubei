package com.xingtan.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingtan.ai.entity.GenerationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生成任务 Mapper
 */
@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {
}
