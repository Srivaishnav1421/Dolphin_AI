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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class HuggingFaceAiService implements AIService {

    private final WebClient webClient;
    private final WorkspaceAiCredentialService credentialService;
    private final boolean enabled;
    private final String envApiKey;
    private final String model;
    private final String baseUrl;

    public HuggingFaceAiService(WorkspaceAiCredentialService credentialService,
            @Value("${ai.huggingface.enabled:true}") boolean enabled,
            @Value("${ai.huggingface.api-key:}") String apiKey,
            @Value("${ai.huggingface.model:meta-llama/Llama-3.1-8B-Instruct}") String model,
            @Value("${ai.huggingface.base-url:https://api-inference.huggingface.co/models}") String baseUrl) {
        this.credentialService = credentialService;
        this.enabled = enabled;
        this.envApiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        String prompt = request.getPrompt();
        String systemPrompt = request.getSystemPrompt();
        String apiKey = resolveApiKey(request.getWorkspaceId()).orElse("");

        // 1. Construct prompt
        String fullPrompt = prompt;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            fullPrompt = "System: " + systemPrompt + "\nUser: " + prompt;
        }

        try {
            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri("/" + model)
                    .contentType(MediaType.APPLICATION_JSON);

            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            // Post structure: { "inputs": "<prompt>" }
            Map<String, String> body = Map.of("inputs", fullPrompt);

            // HuggingFace returns a JSON Array: [{"generated_text": "..."}]
            List<Map<String, Object>> responseList = requestSpec
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            String responseText = "";
            if (responseList != null && !responseList.isEmpty()) {
                Map<String, Object> responseMap = responseList.get(0);
                if (responseMap.containsKey("generated_text")) {
                    responseText = (String) responseMap.get("generated_text");
                    // Clean up Hugging Face output which often echoes the prompt
                    if (responseText.startsWith(fullPrompt)) {
                        responseText = responseText.substring(fullPrompt.length()).trim();
                    }
                }
            }

            int promptTokens = prompt.length() / 4;
            int completionTokens = responseText.length() / 4;
            int totalTokens = promptTokens + completionTokens;

            return LlmResponse.builder()
                    .content(responseText)
                    .provider("HUGGINGFACE")
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCostUsd((promptTokens * 3.50 + completionTokens * 10.50) / 1_000_000.0) // Match Gemini rates
                    .cached(false)
                    .build();

        } catch (Exception e) {
            log.error("Hugging Face API call failed: {}", e.getMessage());
            throw new RuntimeException("HuggingFace provider failed: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    @Override
    public boolean isAvailable() {
        return enabled && envApiKey != null && !envApiKey.isBlank();
    }

    public boolean validateWorkspaceConnection(String workspaceId) {
        String apiKey = resolveApiKey(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Hugging Face API key is not configured for this workspace"));
        try {
            WebClient client = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build();
            String response = client.post()
                    .uri("/" + model)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("inputs", "Reply with exactly: ok"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
            return response != null;
        } catch (Exception e) {
            throw new RuntimeException("HuggingFace provider failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.HUGGINGFACE;
    }

    @Override
    public String getModelName() {
        return model;
    }

    private Optional<String> resolveApiKey(String workspaceId) {
        Optional<String> workspaceKey = credentialService.apiKey(workspaceId, "huggingface");
        if (workspaceKey.isPresent()) {
            return workspaceKey;
        }
        return envApiKey == null || envApiKey.isBlank() ? Optional.empty() : Optional.of(envApiKey.trim());
    }
}
