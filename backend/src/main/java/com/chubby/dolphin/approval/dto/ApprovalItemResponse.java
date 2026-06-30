package com.chubby.dolphin.approval.dto;

import com.chubby.dolphin.approval.*;

import java.time.LocalDateTime;

public record ApprovalItemResponse(
        String id,
        String organizationId,
        String workspaceId,
        String accountId,
        ApprovalSourceModule sourceModule,
        String sourceEntityType,
        String sourceEntityId,
        ApprovalActionType actionType,
        String title,
        String description,
        String recommendationJson,
        String mathSnapshotJson,
        ApprovalSeverity severity,
        ApprovalStatus status,
        Boolean requiresExecution,
        String executionStatus,
        String executionResultJson,
        String createdBy,
        String approvedBy,
        String rejectedBy,
        String executedBy,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt,
        LocalDateTime executedAt,
        LocalDateTime expiresAt,
        boolean executionAvailable
) {
    public static ApprovalItemResponse from(ApprovalItem item) {
        return new ApprovalItemResponse(
                id(item.getId()),
                id(item.getOrganizationId()),
                id(item.getWorkspaceId()),
                id(item.getAccountId()),
                item.getSourceModule(),
                item.getSourceEntityType(),
                id(item.getSourceEntityId()),
                item.getActionType(),
                item.getTitle(),
                item.getDescription(),
                redact(item.getRecommendationJson()),
                redact(item.getMathSnapshotJson()),
                item.getSeverity(),
                item.getStatus(),
                item.getRequiresExecution(),
                item.getExecutionStatus(),
                item.getExecutionResultJson(),
                id(item.getCreatedBy()),
                id(item.getApprovedBy()),
                id(item.getRejectedBy()),
                id(item.getExecutedBy()),
                item.getRejectionReason(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getApprovedAt(),
                item.getRejectedAt(),
                item.getExecutedAt(),
                item.getExpiresAt(),
                false
        );
    }

    private static String id(java.util.UUID value) {
        return value == null ? null : value.toString();
    }

    private static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replaceAll("(?i)(\"?(?:password|token|secret|api_key|apikey|authorization|credential|webhookSecret)\"?\\s*[:=]\\s*\")[^\"]+\"", "$1[REDACTED]\"")
                .replaceAll("(?i)((?:password|token|secret|api_key|apikey|authorization|credential|webhookSecret)\\s*[=:]\\s*)[^;,\\s}]+", "$1[REDACTED]");
    }
}
