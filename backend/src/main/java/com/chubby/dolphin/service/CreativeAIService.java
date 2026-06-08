package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Creative AI Service — Generates ad copy and creative variations.
 *
 * This replaces the human copywriter. Uses the LLM Router to generate:
 *   - Ad headlines + body text + CTA variations
 *   - A/B test recommendations
 *   - Platform-optimized copy (Facebook Feed vs Instagram Story vs Reels)
 *   - Localized variations (English, Hindi, Tamil)
 */
@Service
@Slf4j
public class CreativeAIService {

    private final BusinessLlmFacadeService llmRouter;
    private final AdCreativeRepository creativeRepo;
    private final ObjectMapper mapper;
    private final ImageGenService imageGenService;

    public CreativeAIService(BusinessLlmFacadeService llmRouter,
                             AdCreativeRepository creativeRepo,
                             ObjectMapper mapper,
                             ImageGenService imageGenService) {
        this.llmRouter = llmRouter;
        this.creativeRepo = creativeRepo;
        this.mapper = mapper;
        this.imageGenService = imageGenService;
    }

    /**
     * Generate ad copy variations and save to DB as DRAFT creatives.
     */
    public List<AdCreative> generateAdCopy(String accountId, String campaignId,
                                           String product, String audience,
                                           String tone, String platform) {
        return generateAdCopy(accountId, campaignId, product, audience, tone, platform, "en");
    }

    public List<AdCreative> generateAdCopy(String accountId, String campaignId,
                                           String product, String audience,
                                           String tone, String platform, String languageCode) {
        log.info("✍️ Generating ad copy for: {} | audience: {} | platform: {} | language: {}", 
                product, audience, platform, languageCode);

        BusinessLlmFacadeService.LlmResponse response = llmRouter.generateAdCopy(product, audience, tone, platform, languageCode);
        List<AdCreative> creatives = new ArrayList<>();

        String abTestId = "ab-" + System.currentTimeMillis();
        char groupLetter = 'A';

        try {
            JsonNode root = mapper.readTree(response.text());
            JsonNode variations = root.path("variations");

            if (variations.isArray()) {
                for (JsonNode v : variations) {
                    AdCreative creative = new AdCreative();
                    creative.setAccountId(accountId);
                    creative.setCampaignId(campaignId);
                    creative.setHeadline(v.path("headline").asText(""));
                    creative.setBody(v.path("body").asText(""));
                    creative.setCallToAction(v.path("cta").asText("LEARN_MORE"));
                    creative.setPlatform(platform != null ? platform : "FACEBOOK_FEED");
                    creative.setStatus("DRAFT");
                    creative.setGeneratedBy("AI_GENERATED");
                    creative.setGenerationPrompt(
                        String.format("Product: %s | Audience: %s | Tone: %s | Platform: %s | Language: %s",
                                      product, audience, tone, platform, languageCode));
                    creative.setPredictedCtr(v.path("predicted_ctr").asDouble(0));
                    creative.setAbTestGroup(String.valueOf(groupLetter++));
                    creative.setAbTestId(abTestId);
                    creative.setCreatedAt(LocalDateTime.now());
                    creative.setUpdatedAt(LocalDateTime.now());

                    // Dynamic image asset generation
                    String visualPrompt = String.format("Professional premium digital ad design. Topic: %s. Intended audience: %s. Theme: %s. High conversion design.", 
                            product, audience, tone);
                    String imgUrl = imageGenService.generateAdImage(visualPrompt, product);
                    creative.setImageUrl(imgUrl);

                    creatives.add(creativeRepo.save(creative));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse ad copy response — creating single creative: {}", e.getMessage());
            AdCreative fallback = new AdCreative();
            fallback.setAccountId(accountId);
            fallback.setCampaignId(campaignId);
            fallback.setBody(response.text());
            fallback.setStatus("DRAFT");
            fallback.setGeneratedBy("AI_GENERATED");
            fallback.setPlatform(platform != null ? platform : "FACEBOOK_FEED");
            fallback.setCreatedAt(LocalDateTime.now());
            fallback.setUpdatedAt(LocalDateTime.now());

            String visualPrompt = String.format("Minimalist promotional branding graphic for: %s", product);
            String imgUrl = imageGenService.generateAdImage(visualPrompt, product);
            fallback.setImageUrl(imgUrl);

            creatives.add(creativeRepo.save(fallback));
        }

        log.info("✅ Generated {} ad copy variations (A/B test: {})", creatives.size(), abTestId);
        return creatives;
    }

    /**
     * Suggest A/B test variations for an existing creative.
     */
    public BusinessLlmFacadeService.LlmResponse suggestABTests(String campaignId) {
        List<AdCreative> existing = creativeRepo.findByCampaignIdAndStatus(campaignId, "ACTIVE");
        if (existing.isEmpty()) {
            return new BusinessLlmFacadeService.LlmResponse(
                "{\"suggestions\": [\"No active creatives found. Generate some first.\"]}", "NONE", "none");
        }

        StringBuilder context = new StringBuilder("Current active creatives:\n");
        for (AdCreative c : existing) {
            context.append(String.format("- Headline: \"%s\" | Body: \"%s\" | CTA: %s | CTR: %.2f%%\n",
                c.getHeadline(), c.getBody(), c.getCallToAction(),
                c.getActualCtr() != null ? c.getActualCtr() : 0));
        }

        String prompt = """
                You are an expert Meta Ads optimizer.
                Analyze the current ad creatives and suggest A/B test variations.
                
                %s
                
                Respond with ONLY this JSON (no explanation):
                {
                  "analysis": "What's working and what isn't",
                  "suggestions": [
                    {
                      "type": "HEADLINE_TEST",
                      "description": "Test emotional vs logical headline",
                      "variation_a": "Current headline",
                      "variation_b": "Suggested alternative",
                      "expected_lift": "15-20%% CTR improvement"
                    }
                  ]
                }
                """.formatted(context);

        return llmRouter.ask(prompt);
    }

    /**
     * Rewrite ad copy for a different platform.
     */
    public AdCreative rewriteForPlatform(String creativeId, String targetPlatform) {
        AdCreative original = creativeRepo.findById(creativeId)
                .orElseThrow(() -> new RuntimeException("Creative not found: " + creativeId));

        String prompt = """
                Rewrite this ad for %s platform. Adjust length and tone accordingly.
                
                Original headline: "%s"
                Original body: "%s"
                Original CTA: %s
                
                Platform constraints:
                - FACEBOOK_FEED: headline 40 chars, body 125 chars
                - INSTAGRAM_FEED: body 2200 chars (first 125 visible)
                - INSTAGRAM_STORY: headline 25 chars, body 90 chars
                - REELS: headline 30 chars, body 72 chars
                
                Respond with ONLY this JSON:
                {"headline": "...", "body": "...", "cta": "LEARN_MORE"}
                """.formatted(targetPlatform, original.getHeadline(), original.getBody(), original.getCallToAction());

        BusinessLlmFacadeService.LlmResponse response = llmRouter.ask(prompt);

        AdCreative rewritten = new AdCreative();
        rewritten.setAccountId(original.getAccountId());
        rewritten.setCampaignId(original.getCampaignId());
        rewritten.setPlatform(targetPlatform);
        rewritten.setStatus("DRAFT");
        rewritten.setGeneratedBy("AI_GENERATED");
        rewritten.setGenerationPrompt("Rewrite for " + targetPlatform + " from creative " + creativeId);
        rewritten.setCreatedAt(LocalDateTime.now());
        rewritten.setUpdatedAt(LocalDateTime.now());

        try {
            JsonNode parsed = mapper.readTree(response.text());
            rewritten.setHeadline(parsed.path("headline").asText(""));
            rewritten.setBody(parsed.path("body").asText(""));
            rewritten.setCallToAction(parsed.path("cta").asText("LEARN_MORE"));
        } catch (Exception e) {
            rewritten.setBody(response.text());
        }

        return creativeRepo.save(rewritten);
    }

    /**
     * Get all creatives for an account, optionally filtered by status.
     */
    public List<AdCreative> getCreatives(String accountId, String status) {
        if (status != null && !status.isBlank()) {
            return creativeRepo.findByAccountIdAndStatus(accountId, status);
        }
        return creativeRepo.findByAccountId(accountId);
    }

    /**
     * Get creatives for a specific campaign.
     */
    public List<AdCreative> getCampaignCreatives(String campaignId) {
        return creativeRepo.findByCampaignId(campaignId);
    }

    /**
     * Update creative status (DRAFT → REVIEW → APPROVED → ACTIVE).
     */
    public AdCreative updateStatus(String creativeId, String newStatus) {
        AdCreative creative = creativeRepo.findById(creativeId)
                .orElseThrow(() -> new RuntimeException("Creative not found"));
        creative.setStatus(newStatus);
        creative.setUpdatedAt(LocalDateTime.now());
        return creativeRepo.save(creative);
    }
}
