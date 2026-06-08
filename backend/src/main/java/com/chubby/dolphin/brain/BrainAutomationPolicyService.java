package com.chubby.dolphin.brain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BrainAutomationPolicyService {

    public enum ActionRisk {
        SAFE_AUTO_EXECUTE,
        REQUIRES_APPROVAL,
        BLOCKED
    }

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

    /**
     * Business-level autonomy policy for assistant, automation, and controlled autonomous modes.
     * Safe actions can run only when triggered by configured rules. Risky/destructive actions require approval.
     */
    public ActionRisk classifyAction(String actionType, boolean configuredRuleExists) {
        if (actionType == null || actionType.isBlank()) {
            return ActionRisk.REQUIRES_APPROVAL;
        }

        String action = actionType.toUpperCase().replace(' ', '_');
        return switch (action) {
            case "SEND_SCHEDULED_WHATSAPP", "SEND_SCHEDULED_EMAIL", "ASSIGN_LEAD", "CREATE_REMINDER",
                    "UPDATE_LEAD_SCORE", "TAG_LEAD", "GENERATE_CONTENT_DRAFT", "NOTIFY_TEAM",
                    "LOG_ACTIVITY", "SUGGEST_FOLLOW_UP", "SUMMARIZE_LEAD", "GENERATE_EMAIL_DRAFT",
                    "GENERATE_WHATSAPP_DRAFT" -> configuredRuleExists ? ActionRisk.SAFE_AUTO_EXECUTE : ActionRisk.REQUIRES_APPROVAL;

            case "DELETE_DATA", "DISABLE_USER", "CHANGE_SUBSCRIPTION", "SEND_BULK_CAMPAIGN",
                    "MODIFY_WALLET", "MODIFY_BILLING", "EXPORT_SENSITIVE_DATA", "CHANGE_COMPANY_SETTINGS",
                    "MODIFY_INTEGRATION", "DELETE_AUTOMATION", "PUBLISH_LANDING_PAGE" -> ActionRisk.REQUIRES_APPROVAL;

            case "ACCESS_OTHER_TENANT_DATA", "SEND_WITHOUT_RULE", "HARDCODE_API_KEY" -> ActionRisk.BLOCKED;

            default -> ActionRisk.REQUIRES_APPROVAL;
        };
    }
}
