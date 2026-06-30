package com.chubby.dolphin.organization.dto;

public record OrganizationDto(
        String id,
        String name,
        String plan,
        String billingEmail,
        boolean active
) {}
