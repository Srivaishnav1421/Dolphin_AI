package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GrowthOpportunityEngine {

    public static final String EVALUATION_TYPE = "GROWTH_OPPORTUNITY";
    public static final String FORMULA_VERSION = "growth-opportunity-v1";

    @Value("${dolphin.math.default-target-cpl-inr:500}")
    private double defaultTargetCpl;

    @Value("${dolphin.math.opportunity.conversion-rate-threshold:5.0}")
    private double conversionRateThreshold;

    @Value("${dolphin.math.opportunity.minimum-spend-inr:500}")
    private double minimumSpend;

    public List<MathSignal> evaluate(Campaign campaign, Double workspaceTargetCpl, Double performanceScore) {
        if (campaign == null) {
            return List.of();
        }
        double targetCpl = workspaceTargetCpl != null && workspaceTargetCpl > 0 ? workspaceTargetCpl : defaultTargetCpl;
        MathInputSnapshot input = MathInputSnapshot.forCampaign(campaign, null)
                .put("targetCpl", targetCpl)
                .put("performanceScore", performanceScore)
                .put("conversionRateThreshold", conversionRateThreshold)
                .put("minimumSpend", minimumSpend);

        boolean hasSourceData = campaign.getCpl() != null || campaign.getRoas() != null
                || campaign.getConversionRate() != null || performanceScore != null
                || campaign.getSpent() != null || campaign.getConversions() != null;
        if (!hasSourceData) {
            return List.of();
        }

        List<MathSignal> opportunities = new ArrayList<>();
        if (campaign.getCpl() != null && campaign.getCpl() > 0 && campaign.getCpl() <= targetCpl * 0.75) {
            opportunities.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                    MathActionType.SCALE_CAMPAIGN, null, "Low CPL opportunity",
                    "Actual CPL is at least 25% below target CPL.",
                    input.put("opportunityType", "LOW_CPL"), FORMULA_VERSION, false));
        }
        if (campaign.getConversionRate() != null && campaign.getConversionRate() >= conversionRateThreshold) {
            opportunities.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                    MathActionType.MONITOR, null, "Strong conversion rate",
                    "Conversion rate is above the configured opportunity threshold.",
                    input.put("opportunityType", "STRONG_CONVERSION_RATE"), FORMULA_VERSION, false));
        }
        if (campaign.getRoas() != null && campaign.getRoas() >= 2.5) {
            opportunities.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                    MathActionType.SCALE_CAMPAIGN, null, "Positive ROAS opportunity",
                    "ROAS is at or above 2.5x.",
                    input.put("opportunityType", "POSITIVE_ROAS"), FORMULA_VERSION, false));
        }
        if (performanceScore != null && performanceScore >= 75.0
                && campaign.getSpent() != null && campaign.getSpent() > minimumSpend
                && campaign.getConversions() != null && campaign.getConversions() > 0) {
            opportunities.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.MEDIUM,
                    MathActionType.INCREASE_BUDGET, performanceScore, "Campaign scale candidate",
                    "Performance score, spend, and conversions support a future budget-increase approval.",
                    input.put("opportunityType", "SCALE_CANDIDATE"), FORMULA_VERSION, true));
        }
        return opportunities;
    }
}
