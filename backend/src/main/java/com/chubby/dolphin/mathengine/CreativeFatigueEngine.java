package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CreativeFatigueEngine {

    public static final String EVALUATION_TYPE = "CREATIVE_FATIGUE";
    public static final String FORMULA_VERSION = "creative-fatigue-v1";

    @Value("${dolphin.math.fatigue.low-ctr-percent:0.5}")
    private double lowCtrPercent;

    @Value("${dolphin.math.fatigue.high-cpc-inr:125}")
    private double highCpc;

    @Value("${dolphin.math.fatigue.clicks-no-conversion-threshold:100}")
    private long clicksNoConversionThreshold;

    public MathSignal evaluate(Campaign campaign, MetricSnapshot snapshot) {
        MathInputSnapshot input = MathInputSnapshot.forCampaign(campaign, snapshot)
                .put("lowCtrPercent", lowCtrPercent)
                .put("highCpc", highCpc)
                .put("clicksNoConversionThreshold", clicksNoConversionThreshold);

        Long clicks = snapshot != null ? snapshot.getClicks() : null;
        Long impressions = snapshot != null ? snapshot.getImpressions() : null;
        Double spend = snapshot != null && snapshot.getSpend() != null ? snapshot.getSpend() : campaign != null ? campaign.getSpent() : null;
        Long conversions = snapshot != null && snapshot.getConversions() != null
                ? snapshot.getConversions()
                : campaign != null && campaign.getConversions() != null ? campaign.getConversions().longValue() : null;
        Double ctr = MathEngineUtils.ctrPercent(campaign != null ? campaign.getCtr() : snapshot != null ? snapshot.getCtr() : null, clicks, impressions);
        Double cpc = MathEngineUtils.cpc(snapshot != null ? snapshot.getCpc() : null, spend, clicks);

        input.put("derivedCtrPercent", ctr).put("derivedCpc", cpc);
        if (ctr == null && cpc == null && (clicks == null || conversions == null)) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.NOT_ENOUGH_DATA, MathSeverity.INFO,
                    MathActionType.NONE, null, "Not enough creative fatigue data",
                    "Creative fatigue requires CTR, CPC, or click/conversion data.",
                    input, FORMULA_VERSION, false);
        }
        if (clicks != null && clicks > clicksNoConversionThreshold && conversions != null && conversions == 0) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    MathActionType.REVIEW_LANDING_PAGE, null, "Clicks without conversions",
                    "Clicks exceed the configured threshold while conversions remain zero.",
                    input, FORMULA_VERSION, false);
        }
        if (cpc != null && cpc > highCpc) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    MathActionType.REDUCE_BID, null, "High CPC detected",
                    "CPC is above the configured fatigue threshold.",
                    input, FORMULA_VERSION, true);
        }
        if (ctr != null && ctr < lowCtrPercent) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.MEDIUM,
                    MathActionType.REVIEW_CREATIVE, null, "Low CTR detected",
                    "CTR is below the configured creative review threshold.",
                    input, FORMULA_VERSION, false);
        }
        return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                MathActionType.NONE, null, "No creative fatigue detected",
                "Available creative metrics do not cross fatigue thresholds.",
                input, FORMULA_VERSION, false);
    }
}
