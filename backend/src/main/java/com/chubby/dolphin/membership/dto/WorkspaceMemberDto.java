package com.chubby.dolphin.membership.dto;

public record WorkspaceMemberDto(
        String id,
        String email,
        String name,
        String role,
        String status,
        String organizationId,
        String workspaceId
) {}
