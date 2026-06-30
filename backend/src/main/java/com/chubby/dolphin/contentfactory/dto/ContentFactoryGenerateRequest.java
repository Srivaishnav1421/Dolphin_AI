package com.chubby.dolphin.contentfactory.dto;

public record ContentFactoryGenerateRequest(
        String businessName,
        String productService,
        String targetAudience,
        String location,
        String offer,
        String tone,
        String language,
        String channel,
        String goal,
        String ctaStyle,
        String contentType
) {}
