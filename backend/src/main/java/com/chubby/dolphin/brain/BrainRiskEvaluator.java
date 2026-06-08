package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Wallet;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BrainRiskEvaluator {

    public double calculateRisk(BrainContext context, BrainDecision recommendation) {
        if (context == null || recommendation == null) {
            return 0.0;
        }

        double riskScore = 10.0; // Base baseline risk

        // 1. Wallet Risk
        Wallet wallet = context.getWallet();
        if (wallet == null) {
            riskScore += 25.0; // High risk if no wallet exists
        } else {
            double balance = wallet.getBalance() != null ? wallet.getBalance() : 0.0;
            if (balance <= 0.0) {
                riskScore += 45.0; // Critical: bankrupt wallet
            } else if (balance < 1000.0) {
                riskScore += 25.0; // High: low wallet
            } else if (balance < 5000.0) {
                riskScore += 10.0; // Medium: minor wallet strain
            }
        }

        // 2. Campaign Risk & Budget impact
        String type = recommendation.getDecisionType() != null ? recommendation.getDecisionType() : "CONTINUE";
        double budgetBefore = recommendation.getBudgetBefore() != null ? recommendation.getBudgetBefore() : 0.0;
        double budgetAfter = recommendation.getBudgetAfter() != null ? recommendation.getBudgetAfter() : 0.0;

        if ("SCALE_UP".equals(type) || "BUDGET_REALLOCATE".equals(type)) {
            if (budgetBefore > 0.0) {
                double pct = (budgetAfter - budgetBefore) / budgetBefore;
                if (pct > 0.50) {
                    riskScore += 30.0; // High risk to scale up by more than 50%
                } else if (pct > 0.20) {
                    riskScore += 15.0;
                }
            } else if (budgetAfter > 10000.0) {
                riskScore += 20.0;
            }
        } else if ("PAUSE".equals(type)) {
            // Pausing a campaign is risky if the campaign was highly profitable
            String cId = recommendation.getCampaignId();
            if (cId != null && context.getCampaigns() != null) {
                Optional<Campaign> campOpt = context.getCampaigns().stream()
                        .filter(c -> cId.equals(c.getId()))
                        .findFirst();
                if (campOpt.isPresent()) {
                    Campaign c = campOpt.get();
                    double roas = c.getRoas() != null ? c.getRoas() : 0.0;
                    if (roas > 2.5) {
                        riskScore += 35.0; // High risk to pause highly profitable campaign
                    }
                }
            }
        }

        // 3. Lead / Pipeline Risk
        if (context.getLeads() != null) {
            long totalLeads = context.getLeads().size();
            if (totalLeads < 5 && "PAUSE".equals(type)) {
                riskScore += 15.0; // High lead pipeline impact if pausing with sparse pipeline
            }
        }

        // 4. Historical Failure Risk
        if (context.getRecentDecisions() != null) {
            long failures = context.getRecentDecisions().stream()
                    .filter(d -> Boolean.FALSE.equals(d.getOutcomePositive()) || "FAILED".equals(d.getStatus()))
                    .count();
            if (failures > 0) {
                riskScore += Math.min(failures * 5.0, 15.0);
            }
        }

        // Clamp between 0.0 and 100.0
        return Math.max(0.0, Math.min(riskScore, 100.0));
    }
}
