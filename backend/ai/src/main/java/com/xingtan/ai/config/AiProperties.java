package com.xingtan.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 相关配置：xingtan.ai.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "xingtan.ai")
public class AiProperties {

    private String defaultProvider = "deepseek";
    private Map<String, ProviderConfig> providers = new HashMap<>();
}
