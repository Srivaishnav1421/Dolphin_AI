package com.chubby.dolphin.contentfactory.dto;

import com.chubby.dolphin.contentfactory.ContentApprovalStatus;
import com.chubby.dolphin.contentfactory.ContentFactoryVariant;
import com.chubby.dolphin.contentfactory.ContentGenerationMode;

import java.time.LocalDateTime;

public record ContentFactoryVariantResponse(
        String id,
        String itemId,
        int variantIndex,
        String headline,
        String description,
        String cta,
        String contentText,
        ContentGenerationMode generationMode,
        int score,
        String scoreBreakdownJson,
        ContentApprovalStatus approvalStatus,
        String approvalItemId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt
) {
    public static ContentFactoryVariantResponse from(ContentFactoryVariant variant) {
        return new ContentFactoryVariantResponse(
                id(variant.getId()),
                id(variant.getItemId()),
                variant.getVariantIndex() != null ? variant.getVariantIndex() : 0,
                variant.getHeadline(),
                variant.getDescription(),
                variant.getCta(),
                variant.getContentText(),
                variant.getGenerationMode(),
                variant.getScore() != null ? variant.getScore() : 0,
                variant.getScoreBreakdownJson(),
                variant.getApprovalStatus(),
                id(variant.getApprovalItemId()),
                variant.getCreatedAt(),
                variant.getUpdatedAt(),
                variant.getApprovedAt(),
                variant.getRejectedAt()
        );
    }

    private static String id(java.util.UUID value) {
        return value == null ? null : value.toString();
    }
}
