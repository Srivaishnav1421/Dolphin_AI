package com.chubby.dolphin.brain.execution;

import com.chubby.dolphin.entity.BrainDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BrainOutcomeAnalyzer {

    public ExecutionScore analyze(BrainDecision decision, Double roasAfter, Double ctrAfter, Double cpcAfter, Double leadsAfter) {
        if (decision == null) {
            return new ExecutionScore(0.0, 0.0, 0.0, 0.0);
        }

        double roasBefore = decision.getRoasAtDecision() != null ? decision.getRoasAtDecision() : 0.0;
        double ctrBefore = decision.getCtrAtDecision() != null ? decision.getCtrAtDecision() : 0.0;
        double cpcBefore = decision.getCplAtDecision() != null ? decision.getCplAtDecision() : 0.0; // cpl/cpc
        double leadsBefore = 0.0; // Base baseline

        return analyze(roasBefore, roasAfter, ctrBefore, ctrAfter, cpcBefore, cpcAfter, leadsBefore, leadsAfter);
    }

    public ExecutionScore analyze(
            Double roasBefore, Double roasAfter,
            Double ctrBefore, Double ctrAfter,
            Double cpcBefore, Double cpcAfter,
            Double leadsBefore, Double leadsAfter) {

        double roasLift = calculateLift(roasBefore, roasAfter);
        double ctrLift = calculateLift(ctrBefore, ctrAfter);
        double cpcLift = -calculateLift(cpcBefore, cpcAfter); // Cost decrease is positive lift
        double leadsLift = calculateLift(leadsBefore, leadsAfter);

        // Weighted impact score calculation
        double impact = (roasLift * 0.40) + (ctrLift * 0.20) + (cpcLift * 0.20) + (leadsLift * 0.20);
        
        // Normalize impact to 0-100 score (clamped)
        double impactScore = Math.max(0.0, Math.min(100.0, 50.0 + (impact * 100.0)));
        
        boolean success = (roasAfter >= roasBefore && roasAfter > 0.0) || impactScore >= 50.0;
        double successRate = success ? 1.0 : 0.0;
        
        double confidenceAdj = success ? 5.0 : -10.0;
        double revenueImpact = (roasAfter - roasBefore) * 5000.0; // Estimated dynamic lift value
        if (revenueImpact < 0.0) {
            revenueImpact = 0.0;
        }

        log.info("📊 Closed-loop outcome evaluated: impactScore={}, success={}, revenueImpact=₹{}", 
                impactScore, success, revenueImpact);

        return ExecutionScore.builder()
                .successRate(successRate)
                .impactScore(impactScore)
                .revenueImpact(revenueImpact)
                .confidenceAdjustment(confidenceAdj)
                .build();
    }

    private double calculateLift(Double before, Double after) {
        if (before == null || after == null || before <= 0.0) {
            return 0.0;
        }
        return (after - before) / before;
    }
}
