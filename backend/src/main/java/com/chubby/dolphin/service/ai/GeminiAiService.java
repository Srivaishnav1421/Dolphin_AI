package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.IntegrationSetting;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.repository.IntegrationSettingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class GeminiAiService implements AIService {

    private static final String PROVIDER_ID = "gemini";

    private final IntegrationSettingRepository integrationSettingRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final boolean enabled;
    private final String model;
    private final String envApiKey;

    public GeminiAiService(
            IntegrationSettingRepository integrationSettingRepository,
            ObjectMapper objectMapper,
            @Value("${ai.gemini.enabled:true}") boolean enabled,
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
            @Value("${ai.gemini.model:gemini-1.5-flash}") String model,
            @Value("${ai.gemini.api-key:${gemini.api.key:}}") String envApiKey) {
        this.integrationSettingRepository = integrationSettingRepository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.model = model;
        this.envApiKey = envApiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        if (!enabled) {
            throw new IllegalStateException("Gemini provider is disabled");
        }

        String apiKey = resolveApiKey(request.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Gemini API key is not configured for this workspace"));

        String fullPrompt = request.getPrompt();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            fullPrompt = "System instructions:\n" + request.getSystemPrompt() + "\n\nUser request:\n" + request.getPrompt();
        }

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", fullPrompt))
                    )),
                    "generationConfig", Map.of(
                            "temperature", request.getTemperature() != null ? request.getTemperature() : 0.3,
                            "maxOutputTokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1024
                    )
            );

            JsonNode response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            String responseText = extractText(response);
            if (responseText.isBlank()) {
                throw new IllegalStateException("Gemini returned an empty response");
            }

            int promptTokens = estimateTokens(fullPrompt);
            int completionTokens = estimateTokens(responseText);
            int totalTokens = promptTokens + completionTokens;

            return LlmResponse.builder()
                    .content(responseText)
                    .provider(LlmProvider.GEMINI.name())
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCostUsd(calculateCost(promptTokens, completionTokens))
                    .cached(false)
                    .build();
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini provider failed: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    public boolean hasWorkspaceCredentials(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return hasEnvApiKey();
        }
        return integrationSettingRepository.existsByWorkspaceIdAndProviderId(workspaceId, PROVIDER_ID) || hasEnvApiKey();
    }

    public boolean validateWorkspaceConnection(String workspaceId) {
        String apiKey = resolveApiKey(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Gemini API key is not configured for this workspace"));
        return validateApiKey(apiKey);
    }

    public boolean validateApiKey(String apiKey) {
        if (!enabled) {
            throw new IllegalStateException("Gemini provider is disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API key is blank");
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", "Reply with exactly: ok"))
                )),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "maxOutputTokens", 8
                )
        );

        JsonNode response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(model))
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
        return enabled && hasEnvApiKey();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.GEMINI;
    }

    @Override
    public String getModelName() {
        return model;
    }

    private Optional<String> resolveApiKey(String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            Optional<IntegrationSetting> setting = integrationSettingRepository.findByWorkspaceIdAndProviderId(workspaceId, PROVIDER_ID);
            if (setting.isPresent()) {
                Optional<String> storedKey = extractApiKey(setting.get().getCredentialsJson());
                if (storedKey.isPresent()) {
                    return storedKey;
                }
            }
        }
        return hasEnvApiKey() ? Optional.of(envApiKey.trim()) : Optional.empty();
    }

    private Optional<String> extractApiKey(String credentialsJson) {
        try {
            Map<String, String> credentials = objectMapper.readValue(credentialsJson, new TypeReference<Map<String, String>>() {});
            String apiKey = credentials.get("api_key");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = credentials.get("apiKey");
            }
            return apiKey == null || apiKey.isBlank() ? Optional.empty() : Optional.of(apiKey.trim());
        } catch (Exception e) {
            log.warn("Could not parse Gemini credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean hasEnvApiKey() {
        return envApiKey != null && !envApiKey.isBlank();
    }

    private String extractText(JsonNode response) {
        if (response == null || !response.has("candidates")) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                String value = part.path("text").asText("");
                if (!value.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(value.trim());
                }
            }
        }
        return text.toString().trim();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private double calculateCost(int promptTokens, int completionTokens) {
        double inputCost = (promptTokens * 0.075) / 1_000_000.0;
        double outputCost = (completionTokens * 0.30) / 1_000_000.0;
        return inputCost + outputCost;
    }
}
