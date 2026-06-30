package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.IntegrationSetting;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.IntegrationSettingRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.service.ai.AnthropicAiService;
import com.chubby.dolphin.service.ai.GeminiAiService;
import com.chubby.dolphin.service.ai.HuggingFaceAiService;
import com.chubby.dolphin.service.ai.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationsControllerTest {

    @Mock private IntegrationSettingRepository repository;
    @Mock private MetaConnectionRepository metaConnectionRepository;
    @Mock private WorkspaceConfigRepository workspaceConfigRepository;
    @Mock private WorkspaceAiConfigRepository workspaceAiConfigRepository;
    @Mock private OpenAiService openAiService;
    @Mock private GeminiAiService geminiAiService;
    @Mock private AnthropicAiService anthropicAiService;
    @Mock private HuggingFaceAiService huggingFaceAiService;
    @Mock private SecurityUtils sec;
    @Mock private AccessControlService access;
    @Mock private AuditLogService auditLogService;
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private IntegrationsController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(sec.currentAccountId()).thenReturn("ws-1");
        when(localApprovalSafetyService.shouldRequireApprovalOnly(anyString())).thenReturn(false);
        controller = new IntegrationsController(
                repository,
                metaConnectionRepository,
                workspaceConfigRepository,
                workspaceAiConfigRepository,
                openAiService,
                geminiAiService,
                anthropicAiService,
                huggingFaceAiService,
                sec,
                access,
                auditLogService,
                localApprovalSafetyService
        );
    }

    @Test
    void statusUsesRealMetaAndWhatsappSourcesWithoutSecrets() {
        MetaConnection metaConnection = new MetaConnection();
        metaConnection.setMetaAdAccountId("act_123456789");
        metaConnection.setTokenStatus("VALID");
        WorkspaceConfig workspaceConfig = new WorkspaceConfig();
        workspaceConfig.setWhatsappPhoneId("phone-12345");
        workspaceConfig.setWhatsappToken("secret-token");

        when(repository.findAllByWorkspaceId("ws-1")).thenReturn(List.of());
        when(metaConnectionRepository.findFirstByWorkspaceIdAndTokenStatus("ws-1", "VALID"))
                .thenReturn(Optional.of(metaConnection));
        when(workspaceConfigRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(workspaceConfig));

        ResponseEntity<Map<String, Object>> response = controller.getStatus();

        assertEquals(200, response.getStatusCode().value());
        verify(access).requireWorkspacePermission(Permission.INTEGRATION_READ);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsapp = (Map<String, Object>) response.getBody().get("whatsapp");
        assertNotNull(meta);
        assertNotNull(whatsapp);
        assertEquals(true, meta.get("connected"));
        assertEquals("VALIDATED", meta.get("validationStatus"));
        assertEquals(true, whatsapp.get("configured"));
        assertEquals(false, whatsapp.get("connected"));
        assertFalse(response.getBody().toString().contains("secret-token"));
    }

    @Test
    void liveAiProviderValidationIsBlockedInLocalModeBeforeExternalCall() {
        IntegrationSetting setting = new IntegrationSetting("ws-1", "openai", "{\"api_key\":\"sk-test\"}");
        when(repository.findByWorkspaceIdAndProviderId("ws-1", "openai")).thenReturn(Optional.of(setting));
        when(localApprovalSafetyService.shouldRequireApprovalOnly("INTEGRATION_TEST_OPENAI")).thenReturn(true);
        when(localApprovalSafetyService.blockedMessage("OpenAI connection test"))
                .thenReturn("OpenAI connection test is disabled in local approval-first mode. No external execution was performed.");

        ResponseEntity<Map<String, Object>> response = controller.test("openai");

        assertEquals(403, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals(true, body.get("approval_required"));
        assertEquals(false, body.get("external_execution_allowed"));
        assertEquals("blocked", body.get("status"));
        assertTrue(body.get("message").toString().contains("local approval-first mode"));
        verify(openAiService, never()).validateWorkspaceConnection(anyString());
        verify(localApprovalSafetyService).auditBlockedExecution(
                eq("ws-1"),
                eq("INTEGRATION_TEST_OPENAI"),
                eq("Integration"),
                eq("openai"),
                contains("/api/integrations/openai/test")
        );
    }

    @Test
    void missingProviderCredentialsReturnsNeedsSetupInsteadOfCallingExternalProvider() {
        when(repository.findByWorkspaceIdAndProviderId("ws-1", "gemini")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.test("gemini");

        assertEquals(404, response.getStatusCode().value());
        verify(geminiAiService, never()).validateWorkspaceConnection(anyString());
        verify(localApprovalSafetyService, never()).auditBlockedExecution(anyString(), anyString(), anyString(), isNull(), anyString());
    }
}
