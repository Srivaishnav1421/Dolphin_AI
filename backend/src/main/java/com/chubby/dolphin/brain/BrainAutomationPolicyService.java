package com.chubby.dolphin.brain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BrainAutomationPolicyService {

    public enum AutomationDecision {
        AUTO_APPROVE,
        MANUAL_APPROVAL,
        BLOCK
    }

    public AutomationDecision evaluatePolicy(
            double riskScore, 
            double confidenceScore, 
            double expectedRoi, 
            double governanceScore,
            boolean isWalletCritical,
            boolean isMetaDisconnected,
            boolean isBudgetExceeded) {

        // 1. BLOCK Triggers
        if (riskScore > 75.0) {
            log.warn("🛑 Policy Blocked: High execution risk score ({}) exceeded maximum allowable threshold.", riskScore);
            return AutomationDecision.BLOCK;
        }
        if (governanceScore < 40.0) {
            log.warn("🛑 Policy Blocked: Governance safety score ({}) is below minimum acceptable threshold.", governanceScore);
            return AutomationDecision.BLOCK;
        }
        if (isWalletCritical) {
            log.warn("🛑 Policy Blocked: Critical wallet balance detected.");
            return AutomationDecision.BLOCK;
        }
        if (isMetaDisconnected) {
            log.warn("🛑 Policy Blocked: Workspace Meta connection is disconnected/invalid.");
            return AutomationDecision.BLOCK;
        }
        if (isBudgetExceeded) {
            log.warn("🛑 Policy Blocked: Daily workspace optimization budget exceeded.");
            return AutomationDecision.BLOCK;
        }

        // 2. AUTO APPROVE Triggers
        if (riskScore < 20.0 && confidenceScore > 85.0 && expectedRoi > 10.0 && governanceScore > 80.0) {
            log.info("⚡ Policy Auto-Approved: Passed all safety and high-yield automation gates.");
            return AutomationDecision.AUTO_APPROVE;
        }

        // 3. MANUAL APPROVAL Fallback
        log.info("📋 Policy Manual-Approval Required: Reverting to operator approval.");
        return AutomationDecision.MANUAL_APPROVAL;
    }
}
