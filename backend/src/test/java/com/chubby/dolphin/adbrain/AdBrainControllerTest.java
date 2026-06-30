package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.adbrain.dto.AdBrainRunResultDto;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.TenantAccessService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdBrainControllerTest {

    @Test
    void runRequiresAdBrainRunPermission() {
        AdBrainService service = mock(AdBrainService.class);
        AccessControlService access = mock(AccessControlService.class);
        AdBrainController controller = new AdBrainController(service, access);
        when(service.runCurrentWorkspace()).thenReturn(result());

        controller.run();

        verify(access).requireWorkspacePermission(Permission.AD_BRAIN_RUN);
        verify(service).runCurrentWorkspace();
    }

    @Test
    void viewerDeniedByPermissionCheckCannotRun() {
        AdBrainService service = mock(AdBrainService.class);
        AccessControlService access = mock(AccessControlService.class);
        AdBrainController controller = new AdBrainController(service, access);
        doThrow(new TenantAccessService.TenantAccessDeniedException("Permission denied: ad_brain_run"))
                .when(access).requireWorkspacePermission(Permission.AD_BRAIN_RUN);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class, controller::run);
        verify(service, never()).runCurrentWorkspace();
    }

    @Test
    void readEndpointsRequireReadPermissionAndDoNotExposeSecrets() {
        AdBrainService service = mock(AdBrainService.class);
        AccessControlService access = mock(AccessControlService.class);
        AdBrainController controller = new AdBrainController(service, access);
        when(service.recentRuns()).thenReturn(List.of(result()));

        Object body = controller.runs().getBody();

        verify(access).requireWorkspacePermission(Permission.AD_BRAIN_READ);
        assertNotNull(body);
        String serialized = body.toString().toLowerCase();
        assertFalse(serialized.contains("token"));
        assertFalse(serialized.contains("secret"));
        assertFalse(serialized.contains("password"));
    }

    private AdBrainRunResultDto result() {
        return new AdBrainRunResultDto(
                UUID.randomUUID().toString(),
                AdBrainRunStatus.COMPLETED,
                1,
                3,
                1,
                0,
                1,
                0,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                "Ad Brain completed. 1 actions require approval."
        );
    }
}
