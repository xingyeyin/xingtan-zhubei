package com.xingtan.system.interceptor;

import com.xingtan.system.entity.AuthToken;
import com.xingtan.system.entity.SysUser;
import com.xingtan.system.mapper.AuthTokenMapper;
import com.xingtan.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 鉴权拦截器：校验 Bearer Token，将 userId 写入请求属性
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthTokenMapper tokenMapper;
    private final SysUserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            AuthToken token = tokenMapper.selectById(auth.substring(7));
            if (token != null && token.getExpiresAt() != null
                    && token.getExpiresAt().isAfter(LocalDateTime.now())) {
                String path = request.getRequestURI();
                boolean adminOnly = path.startsWith("/api/admin") || path.startsWith("/api/users");
                SysUser user = userMapper.selectById(token.getUserId());
                if (adminOnly && (user == null || !"ADMIN".equals(user.getRole()))) {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"code\":403,\"message\":\"需要管理员权限\"}");
                    return false;
                }
                request.setAttribute("userId", token.getUserId());
                return true;
            }
        }
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
        return false;
    }
}
