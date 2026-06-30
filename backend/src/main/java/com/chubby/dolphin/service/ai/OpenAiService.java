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
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OpenAiService implements AIService {

    private final WorkspaceAiCredentialService credentialService;
    private final WebClient webClient;
    private final boolean enabled;
    private final String model;
    private final String envApiKey;

    public OpenAiService(WorkspaceAiCredentialService credentialService,
                         @Value("${ai.openai.enabled:true}") boolean enabled,
                         @Value("${ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                         @Value("${ai.openai.model:gpt-4.1-mini}") String model,
                         @Value("${ai.openai.api-key:}") String envApiKey) {
        this.credentialService = credentialService;
        this.enabled = enabled;
        this.model = model;
        this.envApiKey = envApiKey;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        String apiKey = resolveApiKey(request.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("OpenAI API key is not configured for this workspace"));
        String fullPrompt = fullPrompt(request);

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", fullPrompt,
                    "temperature", request.getTemperature() != null ? request.getTemperature() : 0.3,
                    "max_output_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1024
            );

            JsonNode response = webClient.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            String responseText = extractText(response);
            if (responseText.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty response");
            }
            int promptTokens = estimateTokens(fullPrompt);
            int completionTokens = estimateTokens(responseText);

            return LlmResponse.builder()
                    .content(responseText)
                    .provider(LlmProvider.OPENAI.name())
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .estimatedCostUsd(0.0)
                    .cached(false)
                    .build();
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI provider failed: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    public boolean validateWorkspaceConnection(String workspaceId) {
        String apiKey = resolveApiKey(workspaceId)
                .orElseThrow(() -> new IllegalStateException("OpenAI API key is not configured for this workspace"));
        return validateApiKey(apiKey);
    }

    public boolean validateApiKey(String apiKey) {
        if (!enabled) {
            throw new IllegalStateException("OpenAI provider is disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key is blank");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "input", "Reply with exactly: ok",
                "temperature", 0,
                "max_output_tokens", 8
        );

        JsonNode response = webClient.post()
                .uri("/responses")
                .header("Authorization", "Bearer " + apiKey)
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
        return LlmProvider.OPENAI;
    }

    @Override
    public String getModelName() {
        return model;
    }

    private Optional<String> resolveApiKey(String workspaceId) {
        Optional<String> workspaceKey = credentialService.apiKey(workspaceId, "openai");
        if (workspaceKey.isPresent()) {
            return workspaceKey;
        }
        return envApiKey == null || envApiKey.isBlank() ? Optional.empty() : Optional.of(envApiKey.trim());
    }

    private String fullPrompt(LlmRequest request) {
        if (request.getSystemPrompt() == null || request.getSystemPrompt().isBlank()) {
            return request.getPrompt();
        }
        return "System instructions:\n" + request.getSystemPrompt() + "\n\nUser request:\n" + request.getPrompt();
    }

    private String extractText(JsonNode response) {
        if (response == null) return "";
        String direct = response.path("output_text").asText("");
        if (!direct.isBlank()) return direct.trim();
        StringBuilder text = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                String value = content.path("text").asText("");
                if (!value.isBlank()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(value.trim());
                }
            }
        }
        return text.toString().trim();
    }

    private int estimateTokens(String text) {
        return text == null || text.isBlank() ? 0 : Math.max(1, text.length() / 4);
    }
}
