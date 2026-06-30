package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.CompetitorAd;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.repository.CompetitorAdRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@Transactional
public class CompetitorSpyService {

    private final CompetitorAdRepository adRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final BusinessLlmFacadeService llmRouter;
    private final WebClient webClient;
    private final ObjectMapper mapper;

    public CompetitorSpyService(CompetitorAdRepository adRepo,
                                MetaConnectionRepository metaConnRepo,
                                BusinessLlmFacadeService llmRouter,
                                ObjectMapper mapper) {
        this.adRepo = adRepo;
        this.metaConnRepo = metaConnRepo;
        this.llmRouter = llmRouter;
        this.mapper = mapper;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Search and analyze active competitor ad copywriting structures.
     */
    public List<CompetitorAd> spyOnCompetitor(String workspaceId, String keyword) {
        log.info("🕵️‍♂️ Initiating Competitor Spy query for keyword '{}' in workspace: {}", keyword, workspaceId);

        MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID").orElse(null);
        if (conn == null || conn.getAccessToken() == null) {
            log.warn("No active Meta connection for Ads Archive. Competitor intelligence requires live Meta access.");
            return List.of();
        }

        try {
            // Meta Graph Ads Archive API endpoint
            String url = "https://graph.facebook.com/v21.0/ads_archive" +
                    "?search_terms=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8) +
                    "&ad_reached_countries=['IN']" +
                    "&ad_active_status=ACTIVE" +
                    "&fields=id,ad_creative_bodies,page_id,page_name,funding_entity" +
                    "&limit=5";

            String response = webClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(conn.getAccessToken()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = mapper.readTree(response);
            JsonNode data = root.path("data");

            List<CompetitorAd> ads = new ArrayList<>();
            if (data.isArray() && data.size() > 0) {
                for (JsonNode adNode : data) {
                    String adText = "";
                    JsonNode bodies = adNode.path("ad_creative_bodies");
                    if (bodies.isArray() && bodies.size() > 0) {
                        adText = bodies.get(0).asText();
                    } else if (!adNode.path("ad_creative_bodies").isMissingNode()) {
                        adText = adNode.path("ad_creative_bodies").asText();
                    }

                    if (adText.isBlank()) continue;

                    CompetitorAd competitorAd = analyzeAdText(adText);
                    competitorAd.setWorkspaceId(workspaceId);
                    competitorAd.setKeyword(keyword);
                    competitorAd.setPageId(adNode.path("page_id").asText("mock-page"));
                    competitorAd.setPageName(adNode.path("page_name").asText("Competitor Brand"));
                    competitorAd.setDeliveryStartDate(LocalDate.now().minusDays(5));
                    competitorAd.setSnapshotUrl("https://www.facebook.com/ads/library/?id=" + adNode.path("id").asText());

                    ads.add(adRepo.save(competitorAd));
                }
            }

            if (ads.isEmpty()) {
                log.info("No active Meta Ad Library records found for keyword '{}'.", keyword);
                return List.of();
            }

            return ads;

        } catch (Exception e) {
            log.error("❌ Failed to query Meta Ads Archive API: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Leverages local LLM router to evaluate competitor copy emotion, quality score, format, hook, and offer type.
     */
    public CompetitorAd analyzeAdText(String adText) {
        String prompt = String.format("""
                You are a premium Meta Ads copywriting analyst.
                Analyze the following competitor's active ad copywriting and extract its structural properties.
                
                Ad Text:
                "%s"
                
                Respond with ONLY this JSON (no explanation, no markdown, no code fences):
                {
                  "format": "VIDEO",
                  "hook_type": "PAIN_POINT",
                  "offer_type": "DISCOUNT",
                  "emotion": "CURIOSITY",
                  "quality_score": 8
                }
                
                Constraints:
                - format: VIDEO | IMAGE | CAROUSEL
                - hook_type: QUESTION | STATEMENT | OFFER | URGENCY | PAIN_POINT
                - offer_type: DISCOUNT | FREE_TRIAL | LEAD_GEN | DEMO | INFORMATION
                - emotion: FEAR | JOY | CURIOSITY | TRUST | URGENCY
                - quality_score: integer from 1 to 10 evaluating copy hook strength and clarity
                """, adText);

        try {
            BusinessLlmFacadeService.LlmResponse response = llmRouter.ask(prompt);
            JsonNode root = mapper.readTree(response.text());

            CompetitorAd ad = new CompetitorAd();
            ad.setAdText(adText);
            ad.setFormat(root.path("format").asText("IMAGE"));
            ad.setHookType(root.path("hook_type").asText("STATEMENT"));
            ad.setOfferType(root.path("offer_type").asText("INFORMATION"));
            ad.setEmotion(root.path("emotion").asText("CURIOSITY"));
            ad.setQualityScore(root.path("quality_score").asInt(7));
            return ad;

        } catch (Exception e) {
            log.warn("Failed to parse ad copywriting analysis: {}", e.getMessage());
            CompetitorAd ad = new CompetitorAd();
            ad.setAdText(adText);
            ad.setFormat("IMAGE");
            ad.setHookType("STATEMENT");
            ad.setOfferType("INFORMATION");
            ad.setEmotion("CURIOSITY");
            ad.setQualityScore(7);
            return ad;
        }
    }

}
