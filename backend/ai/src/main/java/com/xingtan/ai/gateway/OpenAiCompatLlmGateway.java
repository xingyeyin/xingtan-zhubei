package com.xingtan.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingtan.ai.config.ProviderConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议网关（DeepSeek / 通义千问 compatible-mode 均支持）
 */
@Component
public class OpenAiCompatLlmGateway implements LlmGateway {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiCompatLlmGateway(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(120_000);
        this.restClient = builder.requestFactory(factory).build();
    }

    @Override
    public String complete(ProviderConfig provider, String systemPrompt, String userPrompt, boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", 0.7);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("stream", false);

        String response = restClient.post()
                .uri(provider.getBaseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(response);
            return node.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new IllegalStateException("解析模型返回失败: " + e.getMessage(), e);
        }
    }
}
