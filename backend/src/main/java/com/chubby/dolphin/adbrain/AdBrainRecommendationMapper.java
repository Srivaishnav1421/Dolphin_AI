package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.approval.ApprovalActionType;
import com.chubby.dolphin.approval.ApprovalSeverity;
import com.chubby.dolphin.mathengine.MathActionType;
import com.chubby.dolphin.mathengine.MathSeverity;
import org.springframework.stereotype.Component;

@Component
public class AdBrainRecommendationMapper {

    public ApprovalActionType toApprovalAction(MathActionType actionType) {
        if (actionType == null) {
            return ApprovalActionType.OTHER;
        }
        return switch (actionType) {
            case PAUSE_ALL_REQUIRED, PAUSE_CAMPAIGN -> ApprovalActionType.PAUSE_CAMPAIGN;
            case KILL_CAMPAIGN -> ApprovalActionType.KILL_CAMPAIGN;
            case CHANGE_OBJECTIVE -> ApprovalActionType.CHANGE_OBJECTIVE;
            case CHANGE_BUDGET, INCREASE_BUDGET, SCALE_CAMPAIGN, REDUCE_BID -> ApprovalActionType.CHANGE_BUDGET;
            case REVIEW_CREATIVE, REVIEW_LANDING_PAGE -> ApprovalActionType.OTHER;
            default -> ApprovalActionType.OTHER;
        };
    }

    public ApprovalSeverity toApprovalSeverity(MathSeverity severity) {
        if (severity == null) {
            return ApprovalSeverity.MEDIUM;
        }
        return switch (severity) {
            case INFO, LOW -> ApprovalSeverity.LOW;
            case MEDIUM -> ApprovalSeverity.MEDIUM;
            case HIGH -> ApprovalSeverity.HIGH;
            case CRITICAL -> ApprovalSeverity.CRITICAL;
        };
    }

    public boolean isRisk(MathSeverity severity) {
        return severity == MathSeverity.HIGH || severity == MathSeverity.CRITICAL;
    }

    public boolean isOpportunity(String evaluationType, MathActionType actionType) {
        return "GROWTH_OPPORTUNITY".equals(evaluationType)
                || actionType == MathActionType.INCREASE_BUDGET
                || actionType == MathActionType.SCALE_CAMPAIGN;
    }
}
