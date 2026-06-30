package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class FortyEightHourKillRuleEngine {

    public static final String EVALUATION_TYPE = "FORTY_EIGHT_HOUR_KILL_RULE";
    public static final String FORMULA_VERSION = "48-hour-kill-rule-v1";

    @Value("${dolphin.math.kill-rule.minimum-spend-inr:500}")
    private double minimumSpend;

    @Value("${dolphin.math.kill-rule.hours:48}")
    private long hours;

    public MathSignal evaluate(Campaign campaign, LocalDateTime now) {
        MathInputSnapshot input = MathInputSnapshot.forCampaign(campaign, null)
                .put("minimumSpend", minimumSpend)
                .put("ruleHours", hours);

        if (campaign == null || campaign.getCreatedAt() == null) {
            return notEnough(input, "Campaign created time is required.");
        }
        if (campaign.getSpent() == null || campaign.getConversions() == null) {
            return notEnough(input, "Spend and conversions are required.");
        }

        long ageHours = Duration.between(campaign.getCreatedAt(), now != null ? now : LocalDateTime.now()).toHours();
        input.put("campaignAgeHours", ageHours);

        if (ageHours >= hours && campaign.getSpent() > minimumSpend && campaign.getConversions() == 0) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.CRITICAL,
                    MathActionType.KILL_CAMPAIGN, null, "48-hour kill rule triggered",
                    "Campaign is older than the configured window, has spent above the minimum, and has zero conversions.",
                    input, FORMULA_VERSION, true);
        }
        return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                MathActionType.MONITOR, null, "48-hour kill rule not triggered",
                "Campaign does not meet the deterministic kill-rule threshold.",
                input, FORMULA_VERSION, false);
    }

    private MathSignal notEnough(MathInputSnapshot input, String description) {
        return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.NOT_ENOUGH_DATA, MathSeverity.INFO,
                MathActionType.NONE, null, "Not enough kill-rule data", description,
                input, FORMULA_VERSION, false);
    }
}
