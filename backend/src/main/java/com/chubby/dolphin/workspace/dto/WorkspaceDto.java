package com.chubby.dolphin.workspace.dto;

public record WorkspaceDto(
        String id,
        String name,
        String organizationId,
        String role,
        boolean active,
        String createdAt
) {}
