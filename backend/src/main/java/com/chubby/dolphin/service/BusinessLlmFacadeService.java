package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.chubby.dolphin.entity.AiPurpose;
import com.chubby.dolphin.security.SecurityUtils;
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
    private final ObjectProvider<SecurityUtils> securityUtilsProvider;

    @Value("${llm.router.temperature:0.3}")
    private double defaultTemperature;

    @Value("${llm.router.max-tokens:1024}")
    private int defaultMaxTokens;

    public BusinessLlmFacadeService(AiProviderRouterService enterpriseLlmRouter, 
                                    BrainFeedbackService brainFeedbackService,
                                    ObjectProvider<SecurityUtils> securityUtilsProvider) {
        this.enterpriseLlmRouter = enterpriseLlmRouter;
        this.brainFeedbackService = brainFeedbackService;
        this.securityUtilsProvider = securityUtilsProvider;
    }

    /**
     * Route a prompt through the LLM fallback chain.
     * Returns an LlmResponse containing the text and which provider was used.
     */
    public LlmResponse ask(String prompt) {
        return ask(prompt, defaultTemperature, defaultMaxTokens);
    }

    public LlmResponse ask(String prompt, double temperature, int maxTokens) {
        return askForTask(prompt, temperature, maxTokens, AiPurpose.GENERAL_ASSISTANT, "GENERAL_ASSISTANT");
    }

    public LlmResponse askForTask(String prompt, AiPurpose purpose, String taskKey) {
        return askForTask(prompt, defaultTemperature, defaultMaxTokens, purpose, taskKey);
    }

    public LlmResponse askForTask(String prompt, double temperature, int maxTokens, AiPurpose purpose, String taskKey) {
        try {
            String workspaceId = resolveWorkspaceId();
            com.chubby.dolphin.dto.ai.LlmRequest request = com.chubby.dolphin.dto.ai.LlmRequest.builder()
                    .prompt(prompt)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .purpose(purpose != null ? purpose : AiPurpose.GENERAL_ASSISTANT)
                    .taskKey(taskKey)
                    .workspaceId(workspaceId)
                    .build();

            com.chubby.dolphin.dto.ai.LlmResponse response = enterpriseLlmRouter.ask(workspaceId, request);
            return new LlmResponse(response.getContent(), response.getProvider(), response.getModel());
        } catch (Exception e) {
            log.error("Enterprise ask delegation failed, emergency fallback: {}", e.getMessage());
            return new LlmResponse("AI analysis is temporarily unavailable. Continue with the default workflow and retry after checking provider health.", "NONE", "none");
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
        return askForTask(prompt, AiPurpose.LEAD_SCORING, "CRM_LEAD_SCORING");
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
        return askForTask(prompt, AiPurpose.CAMPAIGN_ANALYSIS, "CAMPAIGN_ANALYSIS");
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
        return askForTask(prompt, AiPurpose.CAMPAIGN_ANALYSIS, "CAMPAIGN_ANALYSIS");
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
        return generateAdCopy(product, audience, tone, platform, languageCode, "BALANCED");
    }

    public LlmResponse generateAdCopy(String product, String audience,
                                       String tone, String platform, String languageCode,
                                       String qualityTier) {
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

        String qualityInstruction = switch ((qualityTier == null ? "BALANCED" : qualityTier).toUpperCase()) {
            case "FAST" -> "Prioritize concise usable drafts with clear CTA and minimal rationale.";
            case "PREMIUM" -> "Create more refined premium copy with sharper hooks, stronger buyer psychology, India-first context, and specific rationale.";
            default -> "Balance speed and quality with practical copy an Indian business can use immediately.";
        };

        String prompt = """
                You are an expert Meta Ads copywriter.
                Generate 3 ad copy variations in the %s language.
                
                Product/Service: %s
                Target Audience: %s
                Tone: %s
                Platform: %s
                Language/Style instructions: %s
                Quality level instructions: %s
                
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
                """.formatted(langName, product, audience, tone, platform, scriptNote, qualityInstruction, feedbackContext);
        return askForTask(prompt, AiPurpose.CREATIVE_GENERATION, "CREATIVE_STUDIO");
    }

    /**
     * Get the health status of all LLM providers.
     */
    public java.util.Map<String, Object> getProviderStatus() {
        java.util.Map<com.chubby.dolphin.entity.LlmProvider, com.chubby.dolphin.service.ai.AIService> providerMap = enterpriseLlmRouter.getProviderMap();

        com.chubby.dolphin.service.ai.AIService openai = providerMap.get(com.chubby.dolphin.entity.LlmProvider.OPENAI);
        com.chubby.dolphin.service.ai.AIService gemini = providerMap.get(com.chubby.dolphin.entity.LlmProvider.GEMINI);
        com.chubby.dolphin.service.ai.AIService anthropic = providerMap.get(com.chubby.dolphin.entity.LlmProvider.ANTHROPIC);
        com.chubby.dolphin.service.ai.AIService ollama = providerMap.get(com.chubby.dolphin.entity.LlmProvider.OLLAMA);
        com.chubby.dolphin.service.ai.AIService huggingFace = providerMap.get(com.chubby.dolphin.entity.LlmProvider.HUGGINGFACE);
        com.chubby.dolphin.service.ai.AIService mock = providerMap.get(com.chubby.dolphin.entity.LlmProvider.MOCK);

        boolean openaiUp = openai != null && openai.isEnabled() && openai.isAvailable();
        boolean geminiUp = gemini != null && gemini.isEnabled() && gemini.isAvailable();
        boolean anthropicUp = anthropic != null && anthropic.isEnabled() && anthropic.isAvailable();
        boolean ollamaUp = ollama != null && ollama.isEnabled() && ollama.isAvailable();
        boolean huggingFaceUp = huggingFace != null && huggingFace.isEnabled() && huggingFace.isAvailable();
        boolean mockUp = mock != null && mock.isEnabled() && mock.isAvailable();

        String activeProvider = "MOCK";
        if (openaiUp) {
            activeProvider = "OPENAI";
        } else if (geminiUp) {
            activeProvider = "GEMINI";
        } else if (anthropicUp) {
            activeProvider = "ANTHROPIC";
        } else if (ollamaUp) {
            activeProvider = "OLLAMA";
        } else if (huggingFaceUp) {
            activeProvider = "HUGGINGFACE";
        }

        return java.util.Map.of(
            "openai", java.util.Map.of(
                "enabled", openai != null && openai.isEnabled(),
                "available", openaiUp,
                "model", openai != null ? openai.getModelName() : "gpt-4.1-mini"
            ),
            "gemini", java.util.Map.of(
                "enabled", gemini != null && gemini.isEnabled(),
                "available", geminiUp,
                "model", gemini != null ? gemini.getModelName() : "gemini-1.5-flash"
            ),
            "anthropic", java.util.Map.of(
                "enabled", anthropic != null && anthropic.isEnabled(),
                "available", anthropicUp,
                "model", anthropic != null ? anthropic.getModelName() : "claude-3-5-sonnet-latest"
            ),
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

    private String resolveWorkspaceId() {
        try {
            SecurityUtils securityUtils = securityUtilsProvider.getIfAvailable();
            if (securityUtils != null) {
                String workspaceId = securityUtils.currentWorkspaceId();
                if (workspaceId != null && !workspaceId.isBlank()) {
                    return workspaceId;
                }
            }
        } catch (Exception ignored) {
            // Background jobs and tests may run without a request security context.
        }
        return "system-workspace";
    }
}
