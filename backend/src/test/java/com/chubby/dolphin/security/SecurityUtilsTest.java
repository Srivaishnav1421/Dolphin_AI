package com.chubby.dolphin.security;

import com.chubby.dolphin.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void currentWorkspaceIdUsesBoundTenantContext() {
        TenantContext.setCurrentTenant("ws-live");
        SecurityUtils securityUtils = new SecurityUtils(mock(UserRepository.class));

        assertEquals("ws-live", securityUtils.currentWorkspaceId());
    }

    @Test
    void currentWorkspaceIdFailsClosedWhenTenantContextMissing() {
        SecurityUtils securityUtils = new SecurityUtils(mock(UserRepository.class));

        assertThrows(TenantAccessService.TenantAccessDeniedException.class, securityUtils::currentWorkspaceId);
    }
}
