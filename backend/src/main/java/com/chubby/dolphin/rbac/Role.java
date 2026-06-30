package com.chubby.dolphin.rbac;

public enum Role {
    SYSTEM_ADMIN,
    PLATFORM_ADMIN,
    OWNER,
    ORG_OWNER,
    ORG_ADMIN,
    WORKSPACE_MANAGER,
    MARKETER,
    SALES_AGENT,
    CLIENT_VIEWER,
    ADMIN,
    MANAGER,
    EMPLOYEE,
    AGENT,
    CLIENT,
    VIEWER;

    public static Role from(String value) {
        if (value == null || value.isBlank()) {
            return VIEWER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return VIEWER;
        }
    }
}
