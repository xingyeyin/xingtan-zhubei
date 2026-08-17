package com.xingtan.ai.config;

import lombok.Data;

/**
 * 模型供应商配置
 */
@Data
public class ProviderConfig {

    private String baseUrl;
    private String apiKey;
    private String model;
}
