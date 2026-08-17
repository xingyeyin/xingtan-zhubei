package com.xingtan.system.config;

import com.xingtan.system.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册鉴权拦截器（保护教师端个人接口）
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/api/auth/profile",
                        "/api/auth/logout",
                        "/api/auth/password",
                        "/api/generations/**",
                        "/api/lessons/**",
                        "/api/feedbacks/**",
                        "/api/kb/my",
                        "/api/kb/upload",
                        "/api/ai/**",
                        "/api/stats/my",
                        "/api/classes/**",
                        "/api/stats/**",
                        "/api/admin/**",
                        "/api/users/**")
                .excludePathPatterns("/api/admin/ping");
    }
}
