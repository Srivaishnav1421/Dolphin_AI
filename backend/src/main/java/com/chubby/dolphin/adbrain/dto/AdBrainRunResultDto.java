package com.chubby.dolphin.adbrain.dto;

import com.chubby.dolphin.adbrain.AdBrainRunStatus;
import com.chubby.dolphin.adbrain.AdBrainRunSummary;

import java.time.LocalDateTime;

public record AdBrainRunResultDto(
        String runId,
        AdBrainRunStatus status,
        int campaignsEvaluated,
        int evaluationsCreated,
        int approvalItemsCreated,
        int duplicateApprovalsSkipped,
        int risksCreated,
        int opportunitiesCreated,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,
        String message
) {
    public static AdBrainRunResultDto from(AdBrainRunSummary run) {
        return new AdBrainRunResultDto(
                run.getId() == null ? null : run.getId().toString(),
                run.getStatus(),
                run.getCampaignsEvaluated(),
                run.getEvaluationsCreated(),
                run.getApprovalItemsCreated(),
                run.getDuplicateApprovalsSkipped(),
                run.getRisksCreated(),
                run.getOpportunitiesCreated(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getErrorMessage(),
                message(run)
        );
    }

    private static String message(AdBrainRunSummary run) {
        if (run.getStatus() == AdBrainRunStatus.FAILED) {
            return "Ad Brain failed. No actions were executed.";
        }
        if (run.getStatus() == AdBrainRunStatus.RUNNING) {
            return "Ad Brain is running.";
        }
        return "Ad Brain completed. " + run.getApprovalItemsCreated() + " actions require approval.";
    }
}
