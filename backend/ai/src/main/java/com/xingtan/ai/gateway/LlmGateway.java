package com.xingtan.ai.gateway;

import com.xingtan.ai.config.ProviderConfig;

/**
 * 大模型网关抽象（后续可扩展流式、函数调用等）
 */
public interface LlmGateway {

    String complete(ProviderConfig provider, String systemPrompt, String userPrompt, boolean jsonMode);
}
