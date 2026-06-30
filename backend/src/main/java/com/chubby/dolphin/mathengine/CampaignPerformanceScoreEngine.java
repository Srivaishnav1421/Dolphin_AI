package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CampaignPerformanceScoreEngine {

    public static final String EVALUATION_TYPE = "CAMPAIGN_PERFORMANCE_SCORE";
    public static final String FORMULA_VERSION = "campaign-performance-score-v1";

    @Value("${dolphin.math.default-target-cpl-inr:500}")
    private double defaultTargetCpl;

    public MathSignal evaluate(Campaign campaign, MetricSnapshot snapshot, Double workspaceTargetCpl) {
        double targetCpl = workspaceTargetCpl != null && workspaceTargetCpl > 0 ? workspaceTargetCpl : defaultTargetCpl;
        MathInputSnapshot input = MathInputSnapshot.forCampaign(campaign, snapshot).put("targetCpl", targetCpl);

        Double spend = firstPositive(snapshot != null ? snapshot.getSpend() : null, campaign != null ? campaign.getSpent() : null);
        Long conversions = firstLong(snapshot != null ? snapshot.getConversions() : null,
                campaign != null && campaign.getConversions() != null ? campaign.getConversions().longValue() : null);
        Long leads = snapshot != null ? snapshot.getLeads() : null;
        Long clicks = snapshot != null ? snapshot.getClicks() : null;
        Long impressions = snapshot != null ? snapshot.getImpressions() : null;
        Double ctr = MathEngineUtils.ctrPercent(
                firstPositive(campaign != null ? campaign.getCtr() : null, snapshot != null ? snapshot.getCtr() : null),
                clicks,
                impressions
        );
        Double actualCpl = MathEngineUtils.cpl(
                firstPositive(campaign != null ? campaign.getCpl() : null, snapshot != null ? snapshot.getCpl() : null),
                spend,
                leads != null ? leads : conversions
        );

        boolean hasAnyData = ctr != null || actualCpl != null
                || (spend != null && spend > 0)
                || (conversions != null && conversions > 0)
                || (leads != null && leads > 0)
                || (clicks != null && clicks > 0)
                || (impressions != null && impressions > 0);
        if (!hasAnyData) {
            input.put("notEnoughData", true)
                    .put("explanation", List.of("Campaign performance score needs CTR/click data, spend, CPL, or conversions."));
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.NOT_ENOUGH_DATA, MathSeverity.INFO,
                    MathActionType.NONE, null, "Not enough campaign data",
                    "Campaign performance score needs CTR/click data, spend, CPL, or conversions.",
                    input, FORMULA_VERSION, false);
        }

        List<String> explanation = new ArrayList<>();
        double ctrScore = ctr != null ? Math.min(ctr / 2.0, 1.0) * 25.0 : 0.0;
        if (ctr != null) {
            explanation.add("CTR contributed " + round(ctrScore) + " points from " + round(ctr) + "% CTR.");
        } else {
            explanation.add("CTR missing, so CTR score is 0.");
        }
        double cplScore = actualCpl != null && actualCpl > 0 ? Math.min(targetCpl / actualCpl, 2.0) * 17.5 : 0.0;
        if (workspaceTargetCpl == null || workspaceTargetCpl <= 0) {
            explanation.add("Target CPL missing, using configured default target CPL " + round(targetCpl) + ".");
        }
        if (actualCpl != null && actualCpl > 0) {
            explanation.add("CPL contributed " + round(cplScore) + " points from actual CPL " + round(actualCpl) + " versus target " + round(targetCpl) + ".");
        } else {
            explanation.add("Actual CPL missing or zero, so CPL score is 0.");
        }
        long conversionCount = conversions != null ? Math.max(0L, conversions) : 0L;
        double conversionScore = Math.min(conversionCount * 4.0, 40.0);
        if (conversions == null) {
            explanation.add("Conversions missing, treated as 0.");
        } else {
            explanation.add("Conversions contributed " + round(conversionScore) + " points from " + conversionCount + " conversions.");
        }
        double spendValue = spend != null ? spend : 0.0;
        double penalty = spendValue > 500.0 && conversionCount == 0 ? 30.0 : 0.0;
        if (penalty > 0) {
            explanation.add("Penalty applied because spend is above threshold with zero conversions.");
        }
        double score = MathEngineUtils.clamp(50.0 + ctrScore + cplScore + conversionScore - penalty, 0.0, 100.0);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("base", 50.0);
        breakdown.put("ctrScore", round(ctrScore));
        breakdown.put("cplScore", round(cplScore));
        breakdown.put("conversionScore", round(conversionScore));
        breakdown.put("penalty", round(penalty));
        breakdown.put("targetCpl", targetCpl);
        breakdown.put("actualCpl", actualCpl);
        breakdown.put("ctrPercent", ctr);
        breakdown.put("leads", leads);
        input.put("scoreBreakdown", breakdown)
                .put("formula", "clamp(50 + ctrScore + cplScore + conversionScore - penalty, 0, 100)")
                .put("explanation", explanation)
                .put("notEnoughData", false);

        MathSeverity severity = severity(score);
        MathActionType action = score < 25.0 ? MathActionType.PAUSE_CAMPAIGN
                : score < 50.0 ? MathActionType.REVIEW_CREATIVE
                : score < 75.0 ? MathActionType.MONITOR
                : MathActionType.NONE;

        return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, severity, action, round(score),
                "Campaign performance score",
                "Deterministic score calculated from CTR, CPL, conversions, and spend penalty.",
                input, FORMULA_VERSION, action == MathActionType.PAUSE_CAMPAIGN);
    }

    private MathSeverity severity(double score) {
        if (score >= 75.0) return MathSeverity.LOW;
        if (score >= 50.0) return MathSeverity.MEDIUM;
        if (score >= 25.0) return MathSeverity.HIGH;
        return MathSeverity.CRITICAL;
    }

    private Double firstPositive(Double first, Double second) {
        if (first != null && first > 0) return first;
        if (second != null && second > 0) return second;
        return first != null ? first : second;
    }

    private Long firstLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
