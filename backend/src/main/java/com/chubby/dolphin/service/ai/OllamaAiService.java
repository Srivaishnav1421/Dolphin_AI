package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OllamaAiService implements AIService {

    private final WebClient webClient;
    private final boolean enabled;
    private final String model;
    private final String baseUrl;

    public OllamaAiService(
            @Value("${ai.ollama.enabled:true}") boolean enabled,
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ai.ollama.model:llama3}") String model) {
        this.enabled = enabled;
        this.model = model;
        this.baseUrl = baseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        if (!enabled) {
            throw new IllegalStateException("Ollama provider is disabled");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("prompt", request.getPrompt());
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                body.put("system", request.getSystemPrompt());
            }
            body.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            options.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.3);
            if (request.getMaxTokens() != null) {
                options.put("num_predict", request.getMaxTokens());
            }
            body.put("options", options);

            Map<String, Object> responseMap = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            String responseText = "";
            int promptTokens = 0;
            int completionTokens = 0;

            if (responseMap != null) {
                if (responseMap.containsKey("response")) {
                    responseText = (String) responseMap.get("response");
                }
                if (responseMap.containsKey("prompt_eval_count")) {
                    promptTokens = ((Number) responseMap.get("prompt_eval_count")).intValue();
                }
                if (responseMap.containsKey("eval_count")) {
                    completionTokens = ((Number) responseMap.get("eval_count")).intValue();
                }
            }

            int totalTokens = promptTokens + completionTokens;
            if (totalTokens == 0) {
                // fallback token approximation
                promptTokens = request.getPrompt().length() / 4;
                completionTokens = responseText.length() / 4;
                totalTokens = promptTokens + completionTokens;
            }

            return LlmResponse.builder()
                    .content(responseText)
                    .provider("OLLAMA")
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCostUsd(0.0) // Local models cost absolutely $0.0 USD
                    .cached(false)
                    .build();

        } catch (Exception e) {
            log.error("Ollama connection failed: {}", e.getMessage());
            throw new RuntimeException("Ollama connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            // Ping Ollama tags
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return response != null;
        } catch (Exception e) {
            log.debug("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.OLLAMA;
    }

    @Override
    public String getModelName() {
        return model;
    }
}
