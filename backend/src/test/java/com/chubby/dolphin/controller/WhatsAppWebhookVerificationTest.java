package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.RateLimiterService;
import com.chubby.dolphin.service.WhatsAppService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WhatsAppWebhookVerificationTest {

    @Mock private WhatsAppService whatsAppService;
    @Mock private WorkspaceConfigRepository configRepo;
    @Mock private AuditLogRepository auditRepo;
    @Mock private RateLimiterService rateLimiter;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter mockCounter;
    @Mock private HttpServletRequest request;
    @Mock private SecurityUtils sec;

    private WhatsAppController whatsAppController;
    private WhatsAppSettingsController settingsController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock Micrometer metrics counter builder
        when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(mockCounter);

        whatsAppController = new WhatsAppController(whatsAppService, configRepo, auditRepo, rateLimiter, meterRegistry);
        settingsController = new WhatsAppSettingsController(configRepo, sec, rateLimiter);
    }

    @Test
    public void testVerifyWebhookSuccess() {
        String token = "CD_verify_correct_token_123456";
        String challenge = "ch-1000";

        WorkspaceConfig config = new WorkspaceConfig();
        config.setWorkspaceId("ws-1");
        config.setWhatsappVerifyToken(token);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimiter.isAllowed("127.0.0.1", RateLimiterService.LimitType.GENERAL)).thenReturn(true);
        when(configRepo.findByVerifyToken(token)).thenReturn(Optional.of(config));

        ResponseEntity<String> response = whatsAppController.verifyWebhook("subscribe", token, challenge, request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(challenge, response.getBody());
        verify(auditRepo, times(1)).save(any(AuditLog.class));
        verify(mockCounter, times(1)).increment();
    }

    @Test
    public void testVerifyWebhookFailureMismatchedToken() {
        String token = "CD_verify_incorrect_token";

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimiter.isAllowed("127.0.0.1", RateLimiterService.LimitType.GENERAL)).thenReturn(true);
        when(configRepo.findByVerifyToken(token)).thenReturn(Optional.empty());

        ResponseEntity<String> response = whatsAppController.verifyWebhook("subscribe", token, "challenge", request);

        assertNotNull(response);
        assertEquals(403, response.getStatusCode().value());
        verify(auditRepo, times(1)).save(any(AuditLog.class));
        verify(mockCounter, times(1)).increment();
    }

    @Test
    public void testVerifyWebhookRateLimited() {
        String token = "CD_verify_token";

        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimiter.isAllowed("192.168.1.1", RateLimiterService.LimitType.GENERAL)).thenReturn(false);

        ResponseEntity<String> response = whatsAppController.verifyWebhook("subscribe", token, "challenge", request);

        assertNotNull(response);
        assertEquals(429, response.getStatusCode().value());
        verify(mockCounter, times(1)).increment();
    }

    @Test
    public void testGenerateWebhookTokenSuccess() {
        when(sec.currentEmail()).thenReturn("owner@chubby.dolphin");
        when(rateLimiter.isAllowed("owner@chubby.dolphin", RateLimiterService.LimitType.GENERAL)).thenReturn(true);
        when(sec.currentAccountId()).thenReturn("ws-999");

        WorkspaceConfig existing = new WorkspaceConfig();
        existing.setWorkspaceId("ws-999");
        when(configRepo.findByWorkspaceId("ws-999")).thenReturn(Optional.of(existing));
        when(configRepo.save(any(WorkspaceConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("workspaceId", "ws-999");
        body.put("generate", true);

        ResponseEntity<?> response = settingsController.generateWebhookToken(body);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<?, ?> resBody = (Map<?, ?>) response.getBody();
        assertNotNull(resBody);
        assertEquals("ws-999", resBody.get("workspaceId"));
        assertTrue((Boolean) resBody.get("webhookEnabled"));
        assertTrue(((String) resBody.get("maskedToken")).startsWith("CD_verify_"));
        assertTrue(((String) resBody.get("unmaskedToken")).startsWith("CD_verify_"));
    }

    @Test
    public void testGenerateWebhookTokenAccessDenied() {
        when(sec.currentEmail()).thenReturn("owner@chubby.dolphin");
        when(rateLimiter.isAllowed("owner@chubby.dolphin", RateLimiterService.LimitType.GENERAL)).thenReturn(true);
        when(sec.currentAccountId()).thenReturn("ws-999"); // Active workspace

        Map<String, Object> body = new HashMap<>();
        body.put("workspaceId", "ws-different"); // Request for unauthorized workspace
        body.put("generate", true);

        ResponseEntity<?> response = settingsController.generateWebhookToken(body);

        assertNotNull(response);
        assertEquals(403, response.getStatusCode().value());
    }
}
