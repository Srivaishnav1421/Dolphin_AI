package com.chubby.dolphin.contentfactory.dto;

import com.chubby.dolphin.contentfactory.ContentFactoryContentType;
import com.chubby.dolphin.contentfactory.ContentFactoryItem;
import com.chubby.dolphin.contentfactory.ContentFactoryTone;
import com.chubby.dolphin.contentfactory.ContentGenerationMode;

import java.time.LocalDateTime;
import java.util.List;

public record ContentFactoryItemResponse(
        String id,
        String organizationId,
        String workspaceId,
        String accountId,
        String createdBy,
        ContentFactoryContentType contentType,
        String businessName,
        String productService,
        String targetAudience,
        String location,
        String offer,
        ContentFactoryTone tone,
        String language,
        String channel,
        String goal,
        String ctaStyle,
        ContentGenerationMode generationMode,
        String inputRequestJson,
        List<ContentFactoryVariantResponse> variants,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContentFactoryItemResponse from(ContentFactoryItem item, List<ContentFactoryVariantResponse> variants) {
        return new ContentFactoryItemResponse(
                id(item.getId()),
                id(item.getOrganizationId()),
                id(item.getWorkspaceId()),
                id(item.getAccountId()),
                id(item.getCreatedBy()),
                item.getContentType(),
                item.getBusinessName(),
                item.getProductService(),
                item.getTargetAudience(),
                item.getLocation(),
                item.getOffer(),
                item.getTone(),
                item.getLanguage(),
                item.getChannel(),
                item.getGoal(),
                item.getCtaStyle(),
                item.getGenerationMode(),
                item.getInputRequestJson(),
                variants,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private static String id(java.util.UUID value) {
        return value == null ? null : value.toString();
    }
}
