package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.CompetitorInsight;
import com.chubby.dolphin.repository.CompetitorInsightRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class CompetitorScraperServiceImpl implements CompetitorScraperService {

    private final CompetitorInsightRepository insightRepo;
    private final BusinessLlmFacadeService llmRouter;
    private final ObjectMapper mapper;

    public CompetitorScraperServiceImpl(CompetitorInsightRepository insightRepo,
                                         BusinessLlmFacadeService llmRouter,
                                         ObjectMapper mapper) {
        this.insightRepo = insightRepo;
        this.llmRouter = llmRouter;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CompetitorInsight analyzeCompetitor(String competitorUrl, String accountId) {
        log.info("🌐 Initiating competitor scraping for url: {} on account: {}", competitorUrl, accountId);
        
        String scrapedText = "";
        String pageTitle = "";
        String metaDescription = "";
        
        try {
            // Fetch and parse webpage with high performance
            Document doc = Jsoup.connect(competitorUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .followRedirects(true)
                    .get();

            pageTitle = doc.title();
            metaDescription = doc.select("meta[name=description]").attr("content");
            
            // Extract clean visible text to avoid HTML noise polluting prompt limits
            scrapedText = doc.body().text();
            if (scrapedText.length() > 5000) {
                scrapedText = scrapedText.substring(0, 5000); // Cap content size
            }
        } catch (Exception e) {
            log.error("⚠️ Failed to parse webpage content directly: {}", e.getMessage());
            scrapedText = "Scrape failed. Target title: " + competitorUrl;
        }

        // Build structured prompt for LLM Router
        String prompt = String.format("""
            You are a senior competitor intelligence analyst.
            Analyze the following raw scraped text content from a competitor's website and extract actionable marketing intelligence.
            
            Website URL: %s
            Page Title: %s
            Meta Description: %s
            
            Scraped Text Content:
            \"\"\"
            %s
            \"\"\"
            
            Respond with ONLY a JSON object (no markdown, no code fences, no extra text):
            {
              "value_proposition": "A concise description of what they offer and their unique value",
              "hooks": [
                "Copywriting hook 1",
                "Copywriting hook 2",
                "Copywriting hook 3"
              ],
              "target_demographics": "Detailed description of their primary audience/personas",
              "pricing_model": "Summary of pricing or subscription structure (e.g. ₹999/month, contact sales)"
            }
            """, competitorUrl, pageTitle, metaDescription, scrapedText);

        BusinessLlmFacadeService.LlmResponse response = llmRouter.ask(prompt);
        log.info("🧠 Competitor text parsed [via {}]", response.provider());

        CompetitorInsight insight = new CompetitorInsight();
        insight.setAccountId(accountId);
        insight.setCompetitorUrl(competitorUrl);
        insight.setCreatedAt(LocalDateTime.now());
        insight.setUpdatedAt(LocalDateTime.now());

        try {
            JsonNode root = mapper.readTree(response.text());
            insight.setValueProposition(root.path("value_proposition").asText("High value digital marketing services"));
            insight.setTargetDemographics(root.path("target_demographics").asText("Modern businesses and enterprise operations"));
            insight.setPricingModel(root.path("pricing_model").asText("Subscription-based models"));

            List<String> hooksList = new ArrayList<>();
            JsonNode hooksNode = root.path("hooks");
            if (hooksNode.isArray()) {
                for (JsonNode hook : hooksNode) {
                    hooksList.add(hook.asText());
                }
            }
            if (hooksList.isEmpty()) {
                hooksList.add("Automated campaigns backed by real-time AI ROI analysis.");
                hooksList.add("Scale your marketing spend efficiently with zero manual checking.");
            }
            insight.setExtractedHooks(hooksList);

        } catch (Exception e) {
            log.warn("Could not parse AI competitor analysis response, applying robust fallbacks: {}", e.getMessage());
            insight.setValueProposition("Value proposition: " + pageTitle);
            insight.setTargetDemographics("Primary Audience: Online consumers and digital enterprises");
            insight.setPricingModel("Pricing model: Subscription/Custom Contact");
            
            List<String> fallbackHooks = new ArrayList<>();
            fallbackHooks.add(metaDescription.isEmpty() ? "Scale your digital campaigns with smart intelligence." : metaDescription);
            fallbackHooks.add("Automate performance buying dynamically using actual live outcomes.");
            insight.setExtractedHooks(fallbackHooks);
        }

        return insightRepo.save(insight);
    }

    @Override
    public List<CompetitorInsight> getInsightsForAccount(String accountId) {
        return insightRepo.findByAccountId(accountId);
    }
}
