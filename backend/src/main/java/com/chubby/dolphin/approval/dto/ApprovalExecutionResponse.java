package com.chubby.dolphin.approval.dto;

public record ApprovalExecutionResponse(
        ApprovalItemResponse approval,
        String message
) {}
