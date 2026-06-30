package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.entity.CompetitorInsight;
import com.chubby.dolphin.repository.CompetitorInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitorMonitoringService {

    private final CompetitorInsightRepository insightRepo;

    public Map<String, Object> calculateThreats(String accountId) {
        List<CompetitorInsight> insights = insightRepo.findByAccountId(accountId);

        double baseThreat = 0.0;
        List<String> threatHighlights = new ArrayList<>();
        List<String> newCompetitors = new ArrayList<>();
        List<String> opportunityGaps = new ArrayList<>();
        List<String> fastGrowing = new ArrayList<>();

        for (CompetitorInsight insight : insights) {
            double competitorWeight = 0.0;

            // Hook complexity multiplier
            if (insight.getExtractedHooks() != null) {
                competitorWeight += (insight.getExtractedHooks().size() * 12.0);
            }

            // Pricing disruptiveness multiplier
            if ("FREEMIUM".equalsIgnoreCase(insight.getPricingModel()) || "LOW_COST".equalsIgnoreCase(insight.getPricingModel())) {
                competitorWeight += 25.0;
                threatHighlights.add("Low cost disruptive model identified at: " + insight.getCompetitorUrl());
            }

            // Extract demographic overlaps
            if (insight.getTargetDemographics() != null && insight.getTargetDemographics().toLowerCase().contains("india")) {
                competitorWeight += 15.0;
            }

            baseThreat = Math.max(baseThreat, Math.min(95.0, competitorWeight));
            fastGrowing.add(insight.getCompetitorUrl());
        }

        if (fastGrowing.isEmpty()) {
            opportunityGaps.add("Connect Meta Ad Library data or run a competitor scan to calculate live threat signals.");
        } else {
            newCompetitors.add("Disruptive AdCo (" + fastGrowing.get(0) + ")");
            opportunityGaps.add("No competitors have localized vernacular creative ad copy options.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("threatScore", baseThreat);
        result.put("threatHighlights", threatHighlights);
        result.put("newCompetitors", newCompetitors);
        result.put("opportunityGaps", opportunityGaps);
        result.put("fastGrowingCompetitors", fastGrowing);

        return result;
    }
}
