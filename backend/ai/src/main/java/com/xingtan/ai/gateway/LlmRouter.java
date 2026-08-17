package com.xingtan.ai.gateway;

import com.xingtan.ai.config.AiProperties;
import com.xingtan.ai.config.ProviderConfig;
import com.xingtan.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 多模型路由：按配置选择供应商，自动容灾切换（后续增强）
 */
@Service
@RequiredArgsConstructor
public class LlmRouter {

    private final AiProperties aiProperties;
    private final OpenAiCompatLlmGateway gateway;

    public String complete(String providerName, String systemPrompt, String userPrompt) {
        return complete(providerName, systemPrompt, userPrompt, false);
    }

    public String completeJson(String providerName, String systemPrompt, String userPrompt) {
        return complete(providerName, systemPrompt, userPrompt, true);
    }

    private String complete(String providerName, String systemPrompt, String userPrompt, boolean jsonMode) {
        String name = providerName == null || providerName.isBlank()
                ? aiProperties.getDefaultProvider() : providerName;
        ProviderConfig config = aiProperties.getProviders().get(name);
        if (config == null) {
            throw new BusinessException(500, "未配置模型供应商: " + name);
        }
        return gateway.complete(config, systemPrompt, userPrompt, jsonMode);
    }
}
