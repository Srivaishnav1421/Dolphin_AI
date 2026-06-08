package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class BrainGovernanceService {

    private final BrainDecisionRepository decisionRepo;

    public BrainGovernanceService(BrainDecisionRepository decisionRepo) {
        this.decisionRepo = decisionRepo;
    }

    public double evaluateGovernance(
            String workspaceId, 
            String campaignId, 
            String decisionType, 
            Double budgetBefore, 
            Double budgetAfter) {
        
        double score = 100.0;

        // 1. Detect excessive budget scaling pattern
        if ("SCALE_UP".equalsIgnoreCase(decisionType) && budgetBefore != null && budgetAfter != null && budgetBefore > 0.0) {
            double percentIncrease = (budgetAfter - budgetBefore) / budgetBefore;
            if (percentIncrease > 0.40) {
                score -= 25.0;
                log.warn("⚠️ Governance penalty: Excessive budget scaling detected (+{}%)", (percentIncrease * 100));
            }
        }

        // Fetch recent decisions for loop and failure checking
        List<BrainDecision> recents = decisionRepo.findTop50ByAccountIdOrderByCreatedAtDesc(workspaceId);
        if (recents == null || recents.isEmpty()) {
            return score;
        }

        // 2. Detect decision loop / oscillation pattern
        if (campaignId != null) {
            long toggleCount = recents.stream()
                    .filter(d -> campaignId.equals(d.getCampaignId()))
                    .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                    .filter(d -> "PAUSE".equalsIgnoreCase(d.getDecisionType()) || "RESUME".equalsIgnoreCase(d.getDecisionType()))
                    .count();
            if (toggleCount >= 2 && ("PAUSE".equalsIgnoreCase(decisionType) || "RESUME".equalsIgnoreCase(decisionType))) {
                score -= 40.0;
                log.warn("⚠️ Governance penalty: Campaign oscillation loops detected for campaign ID: {}", campaignId);
            }
        }

        // 3. Block repeated failures
        long recentFailures = recents.stream()
                .filter(d -> decisionType.equalsIgnoreCase(d.getDecisionType()))
                .filter(d -> "FAILED".equalsIgnoreCase(d.getStatus()))
                .count();
        if (recentFailures > 0) {
            score -= (recentFailures * 15.0);
            log.warn("⚠️ Governance penalty: Repeated execution failures found for decision type: {}", decisionType);
        }

        // 4. Penalize low-performing historical types
        long rejectedCount = recents.stream()
                .filter(d -> decisionType.equalsIgnoreCase(d.getDecisionType()))
                .filter(d -> "REJECTED".equalsIgnoreCase(d.getStatus()))
                .count();
        if (rejectedCount >= 3) {
            score -= 10.0;
            log.warn("⚠️ Governance penalty: High disapproval rate for decision type: {}", decisionType);
        }

        double finalScore = Math.max(0.0, Math.min(100.0, score));
        log.info("🛡️ Safety Governance evaluation completed - Final Score: {}", finalScore);
        return finalScore;
    }
}
