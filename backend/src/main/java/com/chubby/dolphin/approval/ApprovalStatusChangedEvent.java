package com.chubby.dolphin.approval;

public record ApprovalStatusChangedEvent(
        String approvalItemId,
        ApprovalSourceModule sourceModule,
        String sourceEntityType,
        String sourceEntityId,
        ApprovalStatus status
) {}
