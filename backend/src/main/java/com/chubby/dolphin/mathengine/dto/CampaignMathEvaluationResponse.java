package com.chubby.dolphin.mathengine.dto;

import com.chubby.dolphin.mathengine.*;

import java.time.LocalDateTime;

public record CampaignMathEvaluationResponse(
        String id,
        String organizationId,
        String workspaceId,
        String accountId,
        String campaignId,
        String runId,
        String evaluationType,
        MathEvaluationStatus status,
        MathSeverity severity,
        MathActionType actionType,
        Double score,
        String title,
        String description,
        String inputSnapshotJson,
        String formulaVersion,
        Boolean requiresApproval,
        LocalDateTime createdAt
) {
    public static CampaignMathEvaluationResponse from(CampaignMathEvaluation evaluation) {
        return new CampaignMathEvaluationResponse(
                id(evaluation.getId()),
                id(evaluation.getOrganizationId()),
                id(evaluation.getWorkspaceId()),
                id(evaluation.getAccountId()),
                id(evaluation.getCampaignId()),
                id(evaluation.getRunId()),
                evaluation.getEvaluationType(),
                evaluation.getStatus(),
                evaluation.getSeverity(),
                evaluation.getActionType(),
                evaluation.getScore(),
                evaluation.getTitle(),
                evaluation.getDescription(),
                evaluation.getInputSnapshotJson(),
                evaluation.getFormulaVersion(),
                evaluation.getRequiresApproval(),
                evaluation.getCreatedAt()
        );
    }

    private static String id(java.util.UUID id) {
        return id == null ? null : id.toString();
    }
}
