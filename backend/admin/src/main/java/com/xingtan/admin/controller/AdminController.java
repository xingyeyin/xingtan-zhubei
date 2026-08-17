package com.xingtan.admin.controller;

import com.xingtan.ai.config.AiProperties;
import com.xingtan.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端基础接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AiProperties aiProperties;

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "xingtan-backend");
        data.put("status", "UP");
        return Result.ok(data);
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultProvider", aiProperties.getDefaultProvider());
        Map<String, Object> providers = new LinkedHashMap<>();
        aiProperties.getProviders().forEach((name, cfg) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("baseUrl", cfg.getBaseUrl());
            m.put("model", cfg.getModel());
            String key = cfg.getApiKey();
            m.put("configured", key != null && !key.isBlank());
            m.put("apiKeyMasked", key == null || key.isBlank() ? "" : key.substring(0, Math.min(6, key.length())) + "****");
            providers.put(name, m);
        });
        data.put("providers", providers);
        return Result.ok(data);
    }
}
