package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class RiskEngine {

    public static final String EVALUATION_TYPE = "RISK";
    public static final String FORMULA_VERSION = "risk-engine-v1";

    @Value("${dolphin.math.default-target-cpl-inr:500}")
    private double defaultTargetCpl;

    @Value("${dolphin.math.wallet.low-threshold-inr:1000}")
    private double lowWalletThreshold;

    @Value("${dolphin.math.risk.no-recent-leads-days:7}")
    private long noRecentLeadsDays;

    public List<MathSignal> evaluate(Campaign campaign,
                                     Wallet wallet,
                                     List<Lead> leads,
                                     Double workspaceTargetCpl,
                                     Double ctrPercent,
                                     Double cpc) {
        List<MathSignal> risks = new ArrayList<>();
        double targetCpl = workspaceTargetCpl != null && workspaceTargetCpl > 0 ? workspaceTargetCpl : defaultTargetCpl;
        MathInputSnapshot input = MathInputSnapshot.forCampaign(campaign, null)
                .put("targetCpl", targetCpl)
                .put("walletBalance", wallet != null ? wallet.getBalance() : null)
                .put("leadCount", leads != null ? leads.size() : null)
                .put("ctrPercent", ctrPercent)
                .put("cpc", cpc);

        if (wallet != null && wallet.getBalance() != null && wallet.getBalance() <= lowWalletThreshold) {
            risks.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    MathActionType.ALERT_LOW_WALLET, null, "Low wallet risk",
                    "Wallet balance is below the configured safety threshold.",
                    input.put("riskType", "LOW_WALLET"), FORMULA_VERSION, false));
        }
        if (campaign != null) {
            if (campaign.getCpl() != null && campaign.getCpl() > targetCpl) {
                risks.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                        MathActionType.MONITOR, null, "High CPL risk",
                        "Campaign CPL is above target.",
                        input.put("riskType", "HIGH_CPL"), FORMULA_VERSION, false));
            }
            if (campaign.getSpent() != null && campaign.getSpent() > 500
                    && campaign.getConversions() != null && campaign.getConversions() == 0) {
                risks.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                        MathActionType.PAUSE_CAMPAIGN, null, "Spend with zero conversions",
                        "Campaign has spend above threshold and zero conversions.",
                        input.put("riskType", "ZERO_CONVERSION_SPEND"), FORMULA_VERSION, true));
            }
        }
        if (ctrPercent != null && ctrPercent < 0.5) {
            risks.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.MEDIUM,
                    MathActionType.REVIEW_CREATIVE, null, "Low CTR risk",
                    "CTR is below 0.5%.",
                    input.put("riskType", "LOW_CTR"), FORMULA_VERSION, false));
        }
        if (cpc != null && cpc > 125.0) {
            risks.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    MathActionType.REDUCE_BID, null, "High CPC risk",
                    "CPC is above the default high-CPC threshold.",
                    input.put("riskType", "HIGH_CPC"), FORMULA_VERSION, true));
        }
        if (leads != null && !leads.isEmpty()) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(noRecentLeadsDays);
            boolean recentLeadExists = leads.stream().anyMatch(l -> l.getCreatedAt() != null && l.getCreatedAt().isAfter(cutoff));
            if (!recentLeadExists) {
                risks.add(MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.MEDIUM,
                        MathActionType.MONITOR, null, "No recent leads",
                        "Lead data exists, but no leads were created within the configured recent window.",
                        input.put("riskType", "NO_RECENT_LEADS"), FORMULA_VERSION, false));
            }
        }
        return risks;
    }
}
