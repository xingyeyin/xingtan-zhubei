package com.xingtan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xingtan.system.entity.AuthToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 令牌 Mapper
 */
@Mapper
public interface AuthTokenMapper extends BaseMapper<AuthToken> {
}
