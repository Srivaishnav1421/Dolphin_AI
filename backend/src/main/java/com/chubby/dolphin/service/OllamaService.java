package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Ollama Service — Calls a local Ollama instance running Mistral 7B.
 * Ollama provides an OpenAI-compatible API at localhost:11434.
 * This is the PRIMARY LLM provider (zero-cost, fastest, private).
 */
@Service
@Slf4j
public class OllamaService {

    private final WebClient webClient;
    private final String model;
    private final boolean enabled;
    private final int timeoutSeconds;

    public OllamaService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3}") String model,
            @Value("${ollama.enabled:true}") boolean enabled,
            @Value("${ollama.timeout-seconds:30}") int timeoutSeconds) {
        this.model = model;
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public boolean isAvailable() {
        if (!enabled) return false;
        try {
            String response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            return response != null;
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send a prompt to Ollama and get the text response.
     * Uses the /api/generate endpoint with stream=false for simplicity.
     */
    public String ask(String prompt, double temperature, int maxTokens) {
        if (!enabled) {
            throw new RuntimeException("Ollama is disabled");
        }

        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                    "temperature", temperature,
                    "num_predict", maxTokens,
                    "top_p", 0.9,
                    "repeat_penalty", 1.1
                )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null && response.containsKey("response")) {
                String text = response.get("response").toString().trim();
                log.debug("Ollama response ({} tokens): {}", maxTokens, text.substring(0, Math.min(100, text.length())));
                return text;
            }
            throw new RuntimeException("Empty response from Ollama");
        } catch (Exception e) {
            log.error("Ollama API error: {}", e.getMessage());
            throw new RuntimeException("Ollama inference failed: " + e.getMessage(), e);
        }
    }

    /**
     * Chat-style API for instruction-tuned models.
     * Wraps the prompt in a system + user message pair.
     */
    public String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        if (!enabled) {
            throw new RuntimeException("Ollama is disabled");
        }

        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "stream", false,
                "options", Map.of(
                    "temperature", temperature,
                    "num_predict", maxTokens
                )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null && response.containsKey("message")) {
                @SuppressWarnings("unchecked")
                Map<String, String> message = (Map<String, String>) response.get("message");
                return message.get("content").trim();
            }
            throw new RuntimeException("Empty chat response from Ollama");
        } catch (Exception e) {
            log.error("Ollama chat error: {}", e.getMessage());
            throw new RuntimeException("Ollama chat failed: " + e.getMessage(), e);
        }
    }

    public String getModel() { return model; }
    public boolean isEnabled() { return enabled; }
}
