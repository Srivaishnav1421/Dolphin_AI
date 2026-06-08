package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.CompetitorInsight;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BrainDecisionEngine {

    private final BusinessLlmFacadeService llmRouter;
    private final ObjectMapper mapper;
    private final BrainLearningEngine learningEngine;
    private final BrainExperimentEngine experimentEngine;
    private final BrainMemoryService memoryService;
    private final BrainGovernanceService governanceService;

    @Autowired
    public BrainDecisionEngine(
            BusinessLlmFacadeService llmRouter, 
            ObjectMapper mapper,
            BrainLearningEngine learningEngine,
            BrainExperimentEngine experimentEngine,
            BrainMemoryService memoryService,
            BrainGovernanceService governanceService) {
        this.llmRouter = llmRouter;
        this.mapper = mapper;
        this.learningEngine = learningEngine;
        this.experimentEngine = experimentEngine;
        this.memoryService = memoryService;
        this.governanceService = governanceService;
    }

    public List<BrainDecision> generateDecisions(BrainContext context) {
        List<BrainDecision> decisions = new ArrayList<>();
        if (context == null || context.getWorkspaceId() == null) {
            return decisions;
        }

        // 1. Try to generate recommendations using LLM Router with reinforced learning context
        try {
            String prompt = buildPrompt(context);
            BusinessLlmFacadeService.LlmResponse response = llmRouter.ask(prompt);

            if (response != null && response.isAvailable() && response.text() != null && !response.text().isBlank()) {
                String cleanedJson = cleanLlmResponse(response.text());
                List<LlmRecommendationDto> list = mapper.readValue(cleanedJson, new TypeReference<List<LlmRecommendationDto>>() {});
                if (list != null && !list.isEmpty()) {
                    for (LlmRecommendationDto dto : list) {
                        BrainDecision decision = mapDtoToDecision(dto, context, response.provider());
                        decisions.add(decision);
                    }
                    return decisions;
                }
            }
        } catch (Exception e) {
            log.warn("🧠 AI Recommendation Engine failed, falling back to rule-based engine: {}", e.getMessage());
        }

        // 2. Heuristics Fallback Engine
        return generateRuleBasedDecisions(context);
    }

    private String buildPrompt(BrainContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the autonomous CMO AI engine of DolphinAI.\n");
        sb.append("Analyze the following workspace state and provide actionable recommendations for budget scaling, campaign pausing/resuming, SDR leads pipeline, and wallet safety.\n\n");

        sb.append("Workspace ID: ").append(context.getWorkspaceId()).append("\n");

        // CLOSED LOOP FEEDBACK & REINFORCEMENT LEARNING CONTEXT
        sb.append("\n============================================\n");
        sb.append("🧠 REINFORCEMENT LEARNING FEEDBACK:\n");
        
        // Multi-Armed Bandit recommendation selection
        List<String> banditOptions = List.of("SCALE_UP", "PAUSE", "CHANGE_CREATIVE", "CHANGE_AUDIENCE");
        String recommendedBanditAction = experimentEngine.selectAction(context.getWorkspaceId(), banditOptions, learningEngine);
        sb.append("- Multi-Armed Bandit (UCB1) recommended exploration focus: ").append(recommendedBanditAction).append("\n");

        // Learning stats
        for (String actionType : banditOptions) {
            BrainLearningEngine.LearningStats stats = learningEngine.getLearningStats(context.getWorkspaceId(), actionType);
            sb.append(String.format("  * %s -> Success Rate: %.1f%%, ROI Lift: %.1f%%, Failure Rate: %.1f%%, Risk Score: %.1f%%\n",
                    actionType, stats.getSuccessRate() * 100.0, stats.getRoiRate(), stats.getFailureRate() * 100.0, stats.getRiskRate()));
        }

        // Memory logs
        List<BrainMemoryService.BrainMemoryRecord> memories = memoryService.getMemorySummaries(context.getWorkspaceId());
        sb.append("\n💾 CONSOLIDATED HISTORICAL MEMORY PATTERNS:\n");
        if (memories != null && !memories.isEmpty()) {
            int count = 0;
            for (BrainMemoryService.BrainMemoryRecord m : memories) {
                sb.append(String.format("  * [%s] -> %s\n", m.getType(), m.getRawSummaryJson()));
                if (++count >= 5) break; // limit to top 5
            }
        } else {
            sb.append("  * No historical memory logs indexed yet. Defaulting to general baseline optimization bounds.\n");
            sb.append("  * [Safety Fail-safe Boundary]: Daily campaign budget scaling must remain below 40% to avoid ad-delivery shock.\n");
        }

        sb.append("============================================\n\n");

        // Wallet
        if (context.getWallet() != null) {
            sb.append("Wallet Balance: ₹").append(context.getWallet().getBalance()).append("\n");
        } else {
            sb.append("Wallet: Not configured\n");
        }

        // Campaigns
        sb.append("\nActive Campaigns:\n");
        if (context.getCampaigns() != null && !context.getCampaigns().isEmpty()) {
            for (Campaign c : context.getCampaigns()) {
                sb.append(String.format("- ID: %s, Name: %s, Status: %s, Objective: %s, Budget: ₹%.2f, Spent: ₹%.2f, ROAS: %.2f, CTR: %.2f%%, CPL: ₹%.2f, conversions: %d\n",
                        c.getId(), c.getName(), c.getStatus(), c.getObjective(), c.getBudget(), c.getSpent(), c.getRoas(), c.getCtr(), c.getCpl(), c.getConversions()));
            }
        } else {
            sb.append("No active campaigns.\n");
        }

        // Leads
        sb.append("\nSDR Leads Funnel:\n");
        if (context.getLeads() != null && !context.getLeads().isEmpty()) {
            sb.append("Total Leads count: ").append(context.getLeads().size()).append("\n");
            long hotLeads = context.getLeads().stream().filter(l -> "HOT".equals(l.getStatus())).count();
            long coldLeads = context.getLeads().stream().filter(l -> "COLD".equals(l.getStatus())).count();
            sb.append("- Hot Leads: ").append(hotLeads).append(", Cold Leads: ").append(coldLeads).append("\n");
        } else {
            sb.append("No active leads.\n");
        }

        // Competitors
        sb.append("\nCompetitor Opportunities:\n");
        if (context.getCompetitorInsights() != null && !context.getCompetitorInsights().isEmpty()) {
            for (CompetitorInsight insight : context.getCompetitorInsights()) {
                sb.append("- URL: ").append(insight.getCompetitorUrl())
                        .append(", Hooks: ").append(insight.getExtractedHooks())
                        .append(", Angle: ").append(insight.getValueProposition()).append("\n");
            }
        } else {
            sb.append("No competitor intelligence gathered yet.\n");
        }

        sb.append("\n============================================\n");
        sb.append("INSTRUCTIONS:\n");
        sb.append("Generate a list of 2 to 4 actionable recommendations. Return the recommendations as a VALID JSON array. DO NOT include any markdown code blocks, backticks, or extra conversational text. Return ONLY the raw JSON string.\n");
        sb.append("JSON Template:\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"title\": \"SCALE_UP | SCALE_DOWN | PAUSE | RESUME | WALLET_FUND | SDR_FOLLOWUP | CREATIVE_REFRESH\",\n");
        sb.append("    \"description\": \"Specific action details, such as increasing campaign budget X from ₹1000 to ₹1200 or follow up with lead Y.\",\n");
        sb.append("    \"impact\": \"Expected performance lift or safety reasoning.\",\n");
        sb.append("    \"priority\": \"HIGH | MEDIUM | LOW\"\n");
        sb.append("  }\n");
        sb.append("]\n");

        return sb.toString();
    }

    private String cleanLlmResponse(String raw) {
        if (raw == null) return "[]";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int startIdx = cleaned.indexOf('[');
            int endIdx = cleaned.lastIndexOf(']');
            if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                cleaned = cleaned.substring(startIdx, endIdx + 1);
            }
        }
        return cleaned;
    }

    private BrainDecision mapDtoToDecision(LlmRecommendationDto dto, BrainContext context, String provider) {
        BrainDecision d = new BrainDecision();
        d.setAccountId(context.getWorkspaceId());
        d.setLlmProvider(provider);
        d.setStatus("PENDING_APPROVAL");

        String title = dto.getTitle() != null ? dto.getTitle().toUpperCase() : "CONTINUE";
        String desc = dto.getDescription() != null ? dto.getDescription() : "";
        String impact = dto.getImpact() != null ? dto.getImpact() : "";
        String priority = dto.getPriority() != null ? dto.getPriority().toUpperCase() : "MEDIUM";

        // Extract decision type
        String type = "CONTINUE";
        if (title.contains("PAUSE")) type = "PAUSE";
        else if (title.contains("RESUME")) type = "RESUME";
        else if (title.contains("SCALE_UP") || title.contains("SCALEUP")) type = "SCALE_UP";
        else if (title.contains("SCALE_DOWN") || title.contains("SCALEDOWN")) type = "SCALE_DOWN";
        else if (title.contains("WALLET") || title.contains("FUND")) type = "BUDGET_REALLOCATE";
        else if (title.contains("SDR") || title.contains("FOLLOWUP")) type = "CHANGE_AUDIENCE";
        else if (title.contains("CREATIVE") || title.contains("REFRESH")) type = "CHANGE_CREATIVE";

        d.setDecisionType(type);
        d.setAction(desc);
        d.setReason(impact);

        double confidence = 75.0;
        if ("HIGH".equals(priority)) confidence = 90.0;
        else if ("LOW".equals(priority)) confidence = 50.0;
        d.setConfidenceScore(confidence);
        d.setConfidence(confidence / 100.0);

        if (context.getCampaigns() != null && !context.getCampaigns().isEmpty()) {
            Campaign first = context.getCampaigns().get(0);
            d.setCampaignId(first.getId());
            d.setCampaignName(first.getName());
            d.setRoasAtDecision(first.getRoas());
            d.setCtrAtDecision(first.getCtr());
            d.setCplAtDecision(first.getCpl());
            d.setSpentAtDecision(first.getSpent());

            if ("SCALE_UP".equals(type)) {
                d.setBudgetBefore(first.getBudget());
                d.setBudgetAfter(first.getBudget() * 1.20);
            } else if ("SCALE_DOWN".equals(type)) {
                d.setBudgetBefore(first.getBudget());
                d.setBudgetAfter(first.getBudget() * 0.80);
            }
        }
        return d;
    }

    private List<BrainDecision> generateRuleBasedDecisions(BrainContext context) {
        List<BrainDecision> list = new ArrayList<>();
        if (context.getCampaigns() == null || context.getCampaigns().isEmpty()) {
            return list;
        }

        // Wallet reallocate check
        if (context.getWallet() != null && context.getWallet().getBalance() < 100.0) {
            BrainDecision d = new BrainDecision();
            d.setAccountId(context.getWorkspaceId());
            d.setLlmProvider("HEURISTICS_ENGINE");
            d.setDecisionType("BUDGET_REALLOCATE");
            d.setAction("Alert: Deposit funds to secure active campaign running cycles.");
            d.setReason("Wallet balance is very low: ₹" + context.getWallet().getBalance());
            d.setConfidenceScore(90.0);
            d.setConfidence(0.90);
            list.add(d);
        }

        for (Campaign c : context.getCampaigns()) {
            if (c.getRoas() != null && c.getRoas() > 3.0 && "ACTIVE".equals(c.getStatus())) {
                BrainDecision d = new BrainDecision();
                d.setAccountId(context.getWorkspaceId());
                d.setLlmProvider("HEURISTICS_ENGINE");
                d.setCampaignId(c.getId());
                d.setCampaignName(c.getName());
                d.setDecisionType("SCALE_UP");
                d.setAction("Scale up budget for high performing campaign: " + c.getName());
                d.setReason("ROAS is " + c.getRoas() + ", exceeding safety target bounds.");
                d.setConfidenceScore(95.0);
                d.setConfidence(0.95);
                d.setBudgetBefore(c.getBudget());
                d.setBudgetAfter(c.getBudget() * 1.15);
                d.setRoasAtDecision(c.getRoas());
                d.setCtrAtDecision(c.getCtr());
                d.setCplAtDecision(c.getCpl());
                d.setSpentAtDecision(c.getSpent());
                list.add(d);
            } else if (c.getRoas() != null && c.getRoas() < 1.5 && "ACTIVE".equals(c.getStatus())) {
                BrainDecision d = new BrainDecision();
                d.setAccountId(context.getWorkspaceId());
                d.setLlmProvider("HEURISTICS_ENGINE");
                d.setCampaignId(c.getId());
                d.setCampaignName(c.getName());
                d.setDecisionType("SCALE_DOWN");
                d.setAction("Pause budget leakage for low performing campaign: " + c.getName());
                d.setReason("ROAS is " + c.getRoas() + ", which leads to wallet depletion.");
                d.setConfidenceScore(88.0);
                d.setConfidence(0.88);
                d.setBudgetBefore(c.getBudget());
                d.setBudgetAfter(c.getBudget() * 0.80);
                d.setRoasAtDecision(c.getRoas());
                d.setCtrAtDecision(c.getCtr());
                d.setCplAtDecision(c.getCpl());
                d.setSpentAtDecision(c.getSpent());
                list.add(d);
            }
        }
        return list;
    }

    @Data
    public static class LlmRecommendationDto {
        private String title;
        private String description;
        private String impact;
        private String priority;
    }
}
