package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AnthropicAiService implements AIService {

    private final WorkspaceAiCredentialService credentialService;
    private final WebClient webClient;
    private final boolean enabled;
    private final String model;
    private final String envApiKey;

    public AnthropicAiService(WorkspaceAiCredentialService credentialService,
                              @Value("${ai.anthropic.enabled:true}") boolean enabled,
                              @Value("${ai.anthropic.base-url:https://api.anthropic.com/v1}") String baseUrl,
                              @Value("${ai.anthropic.model:claude-3-5-sonnet-latest}") String model,
                              @Value("${ai.anthropic.api-key:}") String envApiKey) {
        this.credentialService = credentialService;
        this.enabled = enabled;
        this.model = model;
        this.envApiKey = envApiKey;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        String apiKey = resolveApiKey(request.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Anthropic API key is not configured for this workspace"));

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1024,
                    "temperature", request.getTemperature() != null ? request.getTemperature() : 0.3,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", request.getPrompt()
                    ))
            );

            WebClient.RequestBodySpec spec = webClient.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON);
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                body = new java.util.HashMap<>(body);
                body.put("system", request.getSystemPrompt());
            }

            JsonNode response = spec.bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            String responseText = extractText(response);
            if (responseText.isBlank()) {
                throw new IllegalStateException("Anthropic returned an empty response");
            }
            int promptTokens = estimateTokens(request.getPrompt());
            int completionTokens = estimateTokens(responseText);

            return LlmResponse.builder()
                    .content(responseText)
                    .provider(LlmProvider.ANTHROPIC.name())
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .estimatedCostUsd(0.0)
                    .cached(false)
                    .build();
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            throw new RuntimeException("Anthropic provider failed: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    public boolean validateWorkspaceConnection(String workspaceId) {
        String apiKey = resolveApiKey(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Anthropic API key is not configured for this workspace"));
        return validateApiKey(apiKey);
    }

    public boolean validateApiKey(String apiKey) {
        if (!enabled) {
            throw new IllegalStateException("Anthropic provider is disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Anthropic API key is blank");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 8,
                "temperature", 0,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "Reply with exactly: ok"
                ))
        );

        JsonNode response = webClient.post()
                .uri("/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(20))
                .block();

        return !extractText(response).isBlank();
    }

    @Override
    public boolean isAvailable() {
        return enabled && envApiKey != null && !envApiKey.isBlank();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.ANTHROPIC;
    }

    @Override
    public String getModelName() {
        return model;
    }

    private Optional<String> resolveApiKey(String workspaceId) {
        Optional<String> workspaceKey = credentialService.apiKey(workspaceId, "anthropic");
        if (workspaceKey.isPresent()) {
            return workspaceKey;
        }
        return envApiKey == null || envApiKey.isBlank() ? Optional.empty() : Optional.of(envApiKey.trim());
    }

    private String extractText(JsonNode response) {
        if (response == null) return "";
        StringBuilder text = new StringBuilder();
        for (JsonNode content : response.path("content")) {
            String value = content.path("text").asText("");
            if (!value.isBlank()) {
                if (!text.isEmpty()) text.append('\n');
                text.append(value.trim());
            }
        }
        return text.toString().trim();
    }

    private int estimateTokens(String text) {
        return text == null || text.isBlank() ? 0 : Math.max(1, text.length() / 4);
    }
}
