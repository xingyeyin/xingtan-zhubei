package com.xingtan.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingtan.stats.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 埋点 Mapper
 */
@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {

    @Select("SELECT CAST(created_at AS DATE) AS day, COUNT(*) AS cnt FROM usage_log "
            + "WHERE created_at >= #{since} GROUP BY CAST(created_at AS DATE) ORDER BY day")
    List<Map<String, Object>> trendByDay(@Param("since") LocalDateTime since);

    @Select("SELECT id, user_id, action, scene, duration_sec, created_at FROM usage_log "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> recent(@Param("limit") int limit);

    @Select("SELECT id, user_id, action, scene, duration_sec, created_at FROM usage_log "
            + "WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> recentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
