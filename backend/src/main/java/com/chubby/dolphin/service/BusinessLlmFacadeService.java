package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.chubby.dolphin.service.ai.AiProviderRouterService;

/**
 * LLM Router — Intelligent fallback chain for AI inference.
 *
 * Priority order:
 *   1. Ollama (local, free, fastest, private)
 *   2. Gemini Pro (cloud, paid, reliable)
 *   3. HuggingFace Inference API (cloud, free tier available)
 *
 * Each call tries the chain in order. If one fails, it falls through
 * to the next. Every response includes which provider was used.
 */
@Service
@Slf4j
public class BusinessLlmFacadeService {

    private final AiProviderRouterService enterpriseLlmRouter;
    private final BrainFeedbackService brainFeedbackService;

    @Value("${llm.router.temperature:0.3}")
    private double defaultTemperature;

    @Value("${llm.router.max-tokens:1024}")
    private int defaultMaxTokens;

    public BusinessLlmFacadeService(AiProviderRouterService enterpriseLlmRouter, 
                                    BrainFeedbackService brainFeedbackService) {
        this.enterpriseLlmRouter = enterpriseLlmRouter;
        this.brainFeedbackService = brainFeedbackService;
    }

    /**
     * Route a prompt through the LLM fallback chain.
     * Returns an LlmResponse containing the text and which provider was used.
     */
    public LlmResponse ask(String prompt) {
        return ask(prompt, defaultTemperature, defaultMaxTokens);
    }

    public LlmResponse ask(String prompt, double temperature, int maxTokens) {
        try {
            com.chubby.dolphin.dto.ai.LlmRequest request = com.chubby.dolphin.dto.ai.LlmRequest.builder()
                    .prompt(prompt)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .purpose(com.chubby.dolphin.entity.AiPurpose.GENERAL_ASSISTANT)
                    .workspaceId("default-workspace")
                    .build();

            com.chubby.dolphin.dto.ai.LlmResponse response = enterpriseLlmRouter.ask("default-workspace", request);
            return new LlmResponse(response.getContent(), response.getProvider(), response.getModel());
        } catch (Exception e) {
            log.error("Enterprise ask delegation failed, emergency fallback: {}", e.getMessage());
            return new LlmResponse("Analysis unavailable — " + e.getMessage(), "NONE", "none");
        }
    }

    /**
     * Score a lead using the LLM chain.
     * Delegates to the existing prompt templates in GeminiService but routes through the chain.
     */
    public LlmResponse scoreLead(String leadMessage, String source) {
        String prompt = """
                You are an expert lead qualifier for a digital marketing agency.
                Analyze this incoming lead message and respond ONLY with a JSON object.
                
                Lead source: %s
                Lead message: "%s"
                
                Respond with ONLY this JSON (no explanation, no markdown, no code fences):
                {
                  "score": 0.85,
                  "status": "HOT",
                  "budget_signal": "High - mentioned ₹50k+ budget",
                  "timeline_signal": "Urgent - wants to start this week",
                  "intent_signal": "Strong - specific product inquiry",
                  "location_signal": "Chennai, Tamil Nadu",
                  "summary": "One-line qualification summary"
                }
                
                Rules:
                - score: 0.0-1.0 (HOT >= 0.7, WARM 0.4-0.69, COLD 0.2-0.39, UNQUALIFIABLE < 0.2)
                - status: HOT | WARM | COLD | UNQUALIFIABLE
                - Use "—" for signals not mentioned in the message
                """.formatted(source, leadMessage);
        return ask(prompt);
    }

    /**
     * Evaluate a campaign using the LLM chain.
     */
    public LlmResponse evaluateCampaign(String name, double roas, double ctr,
                                         double cpl, double spent, double budget) {
        String prompt = """
                You are the Ad Brain of an autonomous marketing system.
                Evaluate this Meta ad campaign and recommend an action.
                
                Campaign: %s
                ROAS: %.2fx | CTR: %.2f%% | CPL: ₹%.0f | Spent: ₹%.0f / ₹%.0f budget
                
                Respond with ONLY this JSON (no explanation, no markdown, no code fences):
                {
                  "action": "CONTINUE",
                  "confidence": 0.87,
                  "reason": "Strong ROAS above 3x threshold with healthy CTR",
                  "suggested_budget_change": 0
                }
                
                action options: CONTINUE | PAUSE | SCALE_UP | SCALE_DOWN
                suggested_budget_change: percentage (-50 to +100)
                confidence: 0.0-1.0 (how certain the AI is about this recommendation)
                """.formatted(name, roas, ctr, cpl, spent, budget);
        return ask(prompt);
    }

    /**
     * Budget arbitrage across campaigns.
     */
    public LlmResponse runArbitrage(java.util.List<String> campaignSummaries, double totalBudget) {
        String prompt = """
                You are managing ad budget arbitrage for a marketing agency.
                Total available budget: ₹%.0f
                
                Campaign performance:
                %s
                
                Recommend budget reallocation. Respond with ONLY this JSON (no explanation, no markdown, no code fences):
                {
                  "recommendation": "Shift 20%% from Campaign B to Campaign A",
                  "expected_roas_lift": 0.4,
                  "actions": [
                    {"campaign": "Campaign A", "change": "+20%%"},
                    {"campaign": "Campaign B", "change": "-20%%"}
                  ],
                  "reasoning": "Campaign A has 3x better ROAS"
                }
                """.formatted(totalBudget, String.join("\n", campaignSummaries));
        return ask(prompt);
    }

    /**
     * Generate ad copy variations.
     */
    public LlmResponse generateAdCopy(String product, String audience,
                                       String tone, String platform) {
        return generateAdCopy(product, audience, tone, platform, "en");
    }

    public LlmResponse generateAdCopy(String product, String audience,
                                       String tone, String platform, String languageCode) {
        String feedbackContext = brainFeedbackService.getBrainOptimizationContext(product);

        String langName = "English";
        String scriptNote = "Write in natural English with compelling copy hooks.";
        if ("hi".equalsIgnoreCase(languageCode)) {
            langName = "Hindi";
            scriptNote = "Create high-converting copy in Devanagari script (Hindi), with local Indian cultural nuances, Hinglish accents where appropriate, and warm tone.";
        } else if ("ta".equalsIgnoreCase(languageCode)) {
            langName = "Tamil";
            scriptNote = "Create high-converting copy in Tamil script, incorporating local high-engagement phrasing and expressions.";
        } else if ("te".equalsIgnoreCase(languageCode)) {
            langName = "Telugu";
            scriptNote = "Create high-converting copy in Telugu script, with premium brand phrasing suitable for AP & Telangana audiences.";
        } else if ("kn".equalsIgnoreCase(languageCode)) {
            langName = "Kannada";
            scriptNote = "Create high-converting copy in Kannada script, optimized for Bangalore and Karnataka tech/consumer audiences.";
        } else if ("bn".equalsIgnoreCase(languageCode)) {
            langName = "Bengali";
            scriptNote = "Create high-converting copy in Bengali script, incorporating local cultural emotional hooks.";
        } else if ("mr".equalsIgnoreCase(languageCode)) {
            langName = "Marathi";
            scriptNote = "Create high-converting copy in Marathi script, with local business-oriented appeal.";
        }

        String prompt = """
                You are an expert Meta Ads copywriter.
                Generate 3 ad copy variations in the %s language.
                
                Product/Service: %s
                Target Audience: %s
                Tone: %s
                Platform: %s
                Language/Style instructions: %s
                
                %s
                
                Respond with ONLY this JSON (no explanation, no markdown, no code fences):
                {
                  "variations": [
                    {
                      "headline": "Compelling headline here (max 40 chars)",
                      "body": "Engaging ad body text (max 125 chars for feed)",
                      "cta": "LEARN_MORE",
                      "predicted_ctr": 2.1,
                      "rationale": "Why this variation works"
                    }
                  ]
                }
                
                CTA options: LEARN_MORE | SHOP_NOW | SIGN_UP | BOOK_NOW | CONTACT_US | GET_OFFER
                Platform constraints:
                - FACEBOOK_FEED: headline 40 chars, body 125 chars
                - INSTAGRAM_FEED: body 2200 chars (but first 125 visible)
                - INSTAGRAM_STORY: headline 25 chars, body 90 chars
                - REELS: headline 30 chars, body 72 chars
                """.formatted(langName, product, audience, tone, platform, scriptNote, feedbackContext);
        return ask(prompt);
    }

    /**
     * Get the health status of all LLM providers.
     */
    public java.util.Map<String, Object> getProviderStatus() {
        java.util.Map<com.chubby.dolphin.entity.LlmProvider, com.chubby.dolphin.service.ai.AIService> providerMap = enterpriseLlmRouter.getProviderMap();

        com.chubby.dolphin.service.ai.AIService ollama = providerMap.get(com.chubby.dolphin.entity.LlmProvider.OLLAMA);
        com.chubby.dolphin.service.ai.AIService huggingFace = providerMap.get(com.chubby.dolphin.entity.LlmProvider.HUGGINGFACE);
        com.chubby.dolphin.service.ai.AIService mock = providerMap.get(com.chubby.dolphin.entity.LlmProvider.MOCK);

        boolean ollamaUp = ollama != null && ollama.isEnabled() && ollama.isAvailable();
        boolean huggingFaceUp = huggingFace != null && huggingFace.isEnabled() && huggingFace.isAvailable();
        boolean mockUp = mock != null && mock.isEnabled() && mock.isAvailable();

        String activeProvider = "MOCK";
        if (ollamaUp) {
            activeProvider = "OLLAMA";
        } else if (huggingFaceUp) {
            activeProvider = "HUGGINGFACE";
        }

        return java.util.Map.of(
            "ollama", java.util.Map.of(
                "enabled", ollama != null && ollama.isEnabled(),
                "available", ollamaUp,
                "model", ollama != null ? ollama.getModelName() : "llama3"
            ),
            "huggingface", java.util.Map.of(
                "enabled", huggingFace != null && huggingFace.isEnabled(),
                "available", huggingFaceUp,
                "model", huggingFace != null ? huggingFace.getModelName() : "meta-llama"
            ),
            "mock", java.util.Map.of(
                "enabled", mock != null && mock.isEnabled(),
                "available", mockUp,
                "model", mock != null ? mock.getModelName() : "mock"
            ),
            "active_provider", activeProvider
        );
    }

    // ── Response DTO ─────────────────────────────────────────────────

    /**
     * Wraps the LLM response with metadata about which provider was used.
     */
    public record LlmResponse(String text, String provider, String model) {
        public boolean isAvailable() {
            return !"NONE".equals(provider);
        }
    }
}
