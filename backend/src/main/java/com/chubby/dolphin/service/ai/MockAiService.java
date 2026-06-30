package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.LlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MockAiService implements AIService {

    @Value("${ai.mock.enabled:true}")
    private boolean enabled;

    @Override
    public LlmResponse ask(LlmRequest request) {
        String content = mockContentFor(request);
        return LlmResponse.builder()
                .content(content)
                .provider("MOCK")
                .model(getModelName())
                .promptTokens(estimateTokens(request != null ? request.getPrompt() : ""))
                .completionTokens(estimateTokens(content))
                .totalTokens(estimateTokens(request != null ? request.getPrompt() : "") + estimateTokens(content))
                .estimatedCostUsd(0.0)
                .cached(false)
                .build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.MOCK;
    }

    @Override
    public String getModelName() {
        return "mock-llama3-ultra";
    }

    private String mockContentFor(LlmRequest request) {
        String prompt = request != null && request.getPrompt() != null ? request.getPrompt().toLowerCase() : "";
        if (prompt.contains("sales development representative") || prompt.contains("sdr")) {
            return "Absolutely, Sunday can work. I have noted your interest and will help the team confirm the nearest available consultation slot.";
        }
        if (prompt.contains("meta ads copywriter") || prompt.contains("ad copy")) {
            return """
                    1. Headline: Book Your Premium Consultation
                    Body: Get expert guidance, clear pricing, and a same-week appointment plan built around your needs.
                    CTA: Book Now

                    2. Headline: Confident Decisions Start Here
                    Body: Speak with a specialist, understand your options, and move forward with a practical plan.
                    CTA: Get Consultation

                    3. Headline: Limited Slots This Week
                    Body: Reserve a convenient appointment and get personalized recommendations from our team.
                    CTA: Enquire Today
                    """;
        }
        if (prompt.contains("lead") && prompt.contains("score")) {
            return "{\"score\":0.64,\"status\":\"WARM\",\"reason\":\"The lead shows clear interest but needs one more qualification step.\"}";
        }
        return "AI draft ready. Review the recommendation, adjust it for your brand voice, and approve it before publishing.";
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.trim().split("\\s+").length);
    }
}
