package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class CplThresholdEngine {

    public static final String EVALUATION_TYPE = "CPL_THRESHOLD";
    public static final String FORMULA_VERSION = "cpl-threshold-v1";

    private static final List<String> OBJECTIVE_LADDER = List.of("AWARENESS", "TRAFFIC", "LEADS", "CONVERSIONS", "SALES");
    private static final Set<MathActionType> BREACH_ACTIONS = Set.of(MathActionType.MONITOR, MathActionType.CHANGE_OBJECTIVE, MathActionType.PAUSE_CAMPAIGN);

    @Value("${dolphin.math.default-target-cpl-inr:500}")
    private double defaultTargetCpl;

    @Value("${dolphin.math.cpl.no-lead-spend-threshold-inr:500}")
    private double noLeadSpendThreshold;

    public MathSignal evaluate(Campaign campaign,
                               MetricSnapshot snapshot,
                               Double workspaceTargetCpl,
                               List<CampaignMathEvaluation> previousEvaluations) {
        double targetCpl = workspaceTargetCpl != null && workspaceTargetCpl > 0 ? workspaceTargetCpl : defaultTargetCpl;
        MathInputSnapshot input = MathInputSnapshot.forCampaign(campaign, snapshot).put("targetCpl", targetCpl);
        Double spend = snapshot != null && snapshot.getSpend() != null ? snapshot.getSpend() : campaign != null ? campaign.getSpent() : null;
        Number conversions = null;
        if (snapshot != null && snapshot.getConversions() != null) {
            conversions = snapshot.getConversions();
        } else if (campaign != null && campaign.getConversions() != null) {
            conversions = campaign.getConversions();
        }
        Number leadDenominator = snapshot != null && snapshot.getLeads() != null ? snapshot.getLeads() : conversions;
        Double actualCpl = MathEngineUtils.cpl(
                campaign != null && campaign.getCpl() != null ? campaign.getCpl() : snapshot != null ? snapshot.getCpl() : null,
                spend,
                leadDenominator
        );
        input.put("actualCpl", actualCpl)
                .put("leadDenominator", leadDenominator)
                .put("noLeadSpendThreshold", noLeadSpendThreshold);

        if (actualCpl == null || actualCpl <= 0) {
            boolean noLeadRisk = spend != null && spend > noLeadSpendThreshold
                    && leadDenominator != null && leadDenominator.doubleValue() == 0.0;
            if (!noLeadRisk) {
                return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.NOT_ENOUGH_DATA, MathSeverity.INFO,
                        MathActionType.NONE, null, "Not enough CPL data",
                        "CPL requires an actual CPL value or spend with leads.",
                        input, FORMULA_VERSION, false);
            }
            input.put("noLeadRisk", true);
        }
        if (actualCpl != null && actualCpl <= targetCpl) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                    MathActionType.NONE, null, "CPL within target",
                    "Actual CPL is at or below the configured target.",
                    input.put("consecutiveBreaches", 0), FORMULA_VERSION, false);
        }

        int previousBreaches = consecutivePreviousBreaches(previousEvaluations);
        int currentStreak = previousBreaches + 1;
        input.put("consecutiveBreaches", currentStreak);

        if (currentStreak >= 3) {
            MathActionType action = canDropObjective(campaign != null ? campaign.getObjective() : null)
                    ? MathActionType.CHANGE_OBJECTIVE
                    : MathActionType.PAUSE_CAMPAIGN;
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    action, null, "CPL threshold breached three times",
                    cplBreachDescription(actualCpl, targetCpl, true),
                    input, FORMULA_VERSION, true);
        }

        return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.INFO,
                MathActionType.MONITOR, null, "CPL above target",
                cplBreachDescription(actualCpl, targetCpl, false),
                input, FORMULA_VERSION, false);
    }

    private String cplBreachDescription(Double actualCpl, double targetCpl, boolean finalAction) {
        String suffix = finalAction
                ? " Three consecutive evaluation cycles now require approval before any objective or pause change."
                : " Fewer than three consecutive evaluation cycles exist, so this remains watching.";
        if (actualCpl == null) {
            return "Spend is above the no-lead threshold with zero leads, so this is classified as a high CPL/no-lead risk." + suffix;
        }
        return "Actual CPL " + round(actualCpl) + " is above target CPL " + round(targetCpl) + "." + suffix;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int consecutivePreviousBreaches(List<CampaignMathEvaluation> previousEvaluations) {
        if (previousEvaluations == null || previousEvaluations.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (CampaignMathEvaluation previous : previousEvaluations) {
            if (previous.getActionType() != null && BREACH_ACTIONS.contains(previous.getActionType())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private boolean canDropObjective(String objective) {
        if (objective == null || objective.isBlank()) {
            return false;
        }
        return OBJECTIVE_LADDER.contains(objective.trim().toUpperCase());
    }
}
