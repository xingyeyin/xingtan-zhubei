package com.xingtan.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingtan.stats.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 反馈 Mapper
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
