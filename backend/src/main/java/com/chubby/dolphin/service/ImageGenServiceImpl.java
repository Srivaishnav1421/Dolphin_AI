package com.chubby.dolphin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@Slf4j
public class ImageGenServiceImpl implements ImageGenService {

    private final WebClient webClient;
    private final ObjectMapper mapper;

    @Value("${openai.api.key:}")
    private String openAiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/images/generations}")
    private String openAiUrl;

    public ImageGenServiceImpl(ObjectMapper mapper) {
        this.webClient = WebClient.builder().build();
        this.mapper = mapper;
    }

    @Override
    public String generateAdImage(String visualPrompt, String theme) {
        log.info("🎨 Generating visual asset with prompt: [Theme: {}]", theme);

        if (openAiKey != null && !openAiKey.isBlank()) {
            try {
                log.info("📡 Contacting DALL-E 3 API for active visual generation...");
                String responseJson = webClient.post()
                        .uri(openAiUrl)
                        .header("Authorization", "Bearer " + openAiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "model", "dall-e-3",
                                "prompt", visualPrompt,
                                "n", 1,
                                "size", "1024x1024"
                        ))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = mapper.readTree(responseJson);
                String dallEUrl = root.path("data").path(0).path("url").asText();
                if (dallEUrl != null && !dallEUrl.isBlank()) {
                    log.info("✅ DALL-E 3 visual asset created successfully: {}", dallEUrl);
                    return dallEUrl;
                }
            } catch (Exception e) {
                log.warn("⚠️ DALL-E API invocation failed, falling back to context repository: {}", e.getMessage());
            }
        } else {
            log.info("ℹ️ No OpenAI API Key found, routing request to context curated image repository.");
        }

        // Return context-appropriate premium unsplash fallback image matching domain context
        return getPremiumContextFallbackUrl(theme);
    }

    private String getPremiumContextFallbackUrl(String theme) {
        if (theme == null) return "https://images.unsplash.com/photo-1542744094-3a31f103e35f?auto=format&fit=crop&w=800&q=80"; // Premium workspace

        String cleanTheme = theme.toUpperCase().trim();
        if (cleanTheme.contains("ECOMMERCE") || cleanTheme.contains("RETAIL") || cleanTheme.contains("PRODUCT")) {
            return "https://images.unsplash.com/photo-1523474253046-8cd2748b5fd2?auto=format&fit=crop&w=800&q=80"; // Premium commerce display
        } else if (cleanTheme.contains("REAL_ESTATE") || cleanTheme.contains("CONDO") || cleanTheme.contains("VILLA")) {
            return "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?auto=format&fit=crop&w=800&q=80"; // Modern villa luxury
        } else if (cleanTheme.contains("LEADS") || cleanTheme.contains("SAAS") || cleanTheme.contains("DIGITAL")) {
            return "https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=800&q=80"; // Dashboard/Analytics charts
        }

        return "https://images.unsplash.com/photo-1542744094-3a31f103e35f?auto=format&fit=crop&w=800&q=80";
    }
}
