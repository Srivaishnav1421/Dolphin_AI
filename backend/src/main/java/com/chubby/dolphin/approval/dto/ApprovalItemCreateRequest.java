package com.chubby.dolphin.approval.dto;

import com.chubby.dolphin.approval.ApprovalActionType;
import com.chubby.dolphin.approval.ApprovalSeverity;
import com.chubby.dolphin.approval.ApprovalSourceModule;

import java.time.LocalDateTime;

public record ApprovalItemCreateRequest(
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
        Boolean requiresExecution,
        LocalDateTime expiresAt
) {}
