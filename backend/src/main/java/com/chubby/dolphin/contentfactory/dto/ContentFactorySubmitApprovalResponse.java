package com.chubby.dolphin.contentfactory.dto;

import com.chubby.dolphin.approval.dto.ApprovalItemResponse;

public record ContentFactorySubmitApprovalResponse(
        ContentFactoryVariantResponse variant,
        ApprovalItemResponse approval
) {}
