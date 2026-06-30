package com.chubby.dolphin.controller.ai;

import com.chubby.dolphin.controller.admin.AdminAiInfrastructureController;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.AiWorkspaceBudget;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.repository.AiResponseCacheRepository;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import com.chubby.dolphin.repository.AiWorkspaceBudgetRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.repository.WorkspaceAiTaskRouteRepository;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.ai.AIService;
import com.chubby.dolphin.service.ai.AiProviderRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AiProviderControllerTest {

    @Mock private AiProviderRouterService llmRouter;
    @Mock private WorkspaceAiConfigRepository workspaceAiConfigRepo;
    @Mock private AiWorkspaceBudgetRepository budgetRepo;
    @Mock private AiUsageLogRepository usageLogRepo;
    @Mock private AiResponseCacheRepository cacheRepo;
    @Mock private WorkspaceAiTaskRouteRepository taskRouteRepo;
    @Mock private SecurityUtils sec;
    @Mock private AccessControlService access;
    @Mock private AuditLogService auditLogService;

    @Mock private AIService openAiService;
    @Mock private AIService ollamaService;
    @Mock private AIService huggingFaceService;
    @Mock private AIService geminiService;
    @Mock private AIService anthropicService;
    @Mock private AIService mockService;

    private AdminAiInfrastructureController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new AdminAiInfrastructureController(llmRouter, workspaceAiConfigRepo, budgetRepo, usageLogRepo, cacheRepo, taskRouteRepo, sec, access, auditLogService);

        when(sec.currentAccountId()).thenReturn("ws-123");
        User user = new User();
        user.setId("user-1");
        user.setEmail("owner@dolphin.test");
        user.setRole("OWNER");
        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org");
        org.setPlan("AGENCY");
        user.setOrganization(org);
        when(access.currentUser()).thenReturn(user);

        // Set up router mocks
        Map<LlmProvider, AIService> providerMap = new EnumMap<>(LlmProvider.class);
        providerMap.put(LlmProvider.OPENAI, openAiService);
        providerMap.put(LlmProvider.GEMINI, geminiService);
        providerMap.put(LlmProvider.ANTHROPIC, anthropicService);
        providerMap.put(LlmProvider.OLLAMA, ollamaService);
        providerMap.put(LlmProvider.HUGGINGFACE, huggingFaceService);
        providerMap.put(LlmProvider.MOCK, mockService);
        when(llmRouter.getProviderMap()).thenReturn(providerMap);

        when(openAiService.getProvider()).thenReturn(LlmProvider.OPENAI);
        when(openAiService.isEnabled()).thenReturn(true);
        when(openAiService.isAvailable()).thenReturn(false);
        when(openAiService.getModelName()).thenReturn("gpt-4.1-mini");

        when(geminiService.getProvider()).thenReturn(LlmProvider.GEMINI);
        when(geminiService.isEnabled()).thenReturn(true);
        when(geminiService.isAvailable()).thenReturn(false);
        when(geminiService.getModelName()).thenReturn("gemini-1.5-flash");

        when(anthropicService.getProvider()).thenReturn(LlmProvider.ANTHROPIC);
        when(anthropicService.isEnabled()).thenReturn(true);
        when(anthropicService.isAvailable()).thenReturn(false);
        when(anthropicService.getModelName()).thenReturn("claude-3-5-sonnet-latest");

        when(ollamaService.getProvider()).thenReturn(LlmProvider.OLLAMA);
        when(ollamaService.isEnabled()).thenReturn(true);
        when(ollamaService.isAvailable()).thenReturn(true);
        when(ollamaService.getModelName()).thenReturn("llama3");

        when(huggingFaceService.getProvider()).thenReturn(LlmProvider.HUGGINGFACE);
        when(huggingFaceService.isEnabled()).thenReturn(true);
        when(huggingFaceService.isAvailable()).thenReturn(false);
        when(huggingFaceService.getModelName()).thenReturn("meta-llama");

        when(mockService.getProvider()).thenReturn(LlmProvider.MOCK);
        when(mockService.isEnabled()).thenReturn(true);
        when(mockService.isAvailable()).thenReturn(true);
        when(mockService.getModelName()).thenReturn("mock");
    }

    @Test
    public void testGetProviders() {
        when(llmRouter.resolveProvider("ws-123")).thenReturn(ollamaService);

        ResponseEntity<Map<String, Object>> response = controller.getProviders();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("OLLAMA", body.get("activeProvider"));

        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("providers");
        assertEquals(6, list.size());

        Map<String, Object> ollamaInfo = list.stream()
                .filter(m -> "OLLAMA".equals(m.get("provider")))
                .findFirst().orElseThrow();
        assertEquals(true, ollamaInfo.get("enabled"));
        assertEquals(true, ollamaInfo.get("available"));
        assertEquals("llama3", ollamaInfo.get("model"));
    }

    @Test
    public void testSwitchProviderSuccess() {
        WorkspaceAiConfig config = WorkspaceAiConfig.builder().workspaceId("ws-123").build();
        when(workspaceAiConfigRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(config));
        when(llmRouter.isProviderUsableForWorkspace(huggingFaceService, "ws-123")).thenReturn(true);

        Map<String, String> body = Map.of("provider", "HUGGINGFACE");
        ResponseEntity<Map<String, Object>> response = controller.switchProvider(body);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("HUGGINGFACE", response.getBody().get("activeProvider"));
        assertEquals(LlmProvider.HUGGINGFACE, config.getActiveProvider());

        verify(workspaceAiConfigRepo, times(1)).save(config);
    }

    @Test
    public void testSwitchProviderInvalid() {
        Map<String, String> body = Map.of("provider", "INVALID_LLM");
        ResponseEntity<Map<String, Object>> response = controller.switchProvider(body);

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
        verify(workspaceAiConfigRepo, never()).save(any());
    }

    @Test
    public void testGetUsageStats() {
        when(usageLogRepo.sumCostByWorkspaceIdSince(eq("ws-123"), any())).thenReturn(1.23);
        when(usageLogRepo.sumTokensByWorkspaceIdSince(eq("ws-123"), any())).thenReturn(50000L);
        when(usageLogRepo.countByAccountIdAndCreatedAtGreaterThanEqual(eq("ws-123"), any())).thenReturn(100L);

        AiWorkspaceBudget budget = AiWorkspaceBudget.builder().workspaceId("ws-123").monthlyUsdBudget(150.0).build();
        when(budgetRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(budget));

        ResponseEntity<Map<String, Object>> response = controller.getUsageStats();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(1.23, body.get("currentMonthlySpendUsd"));
        assertEquals(50000L, body.get("currentMonthlyTokens"));
        assertEquals(150.0, body.get("monthlyBudgetLimitUsd"));
        assertEquals(100L, body.get("workspaceRequests"));
        assertNull(body.get("cacheHitRatePercent"));
    }

    @Test
    public void testGetBudget() {
        AiWorkspaceBudget budget = AiWorkspaceBudget.builder().workspaceId("ws-123").monthlyUsdBudget(50.0).build();
        when(budgetRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(budget));

        ResponseEntity<AiWorkspaceBudget> response = controller.getBudget();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(50.0, response.getBody().getMonthlyUsdBudget());
    }

    @Test
    public void testUpdateBudget() {
        AiWorkspaceBudget budget = AiWorkspaceBudget.builder().workspaceId("ws-123").monthlyUsdBudget(50.0).build();
        when(budgetRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(budget));
        when(budgetRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of(
            "monthlyUsdBudget", 75.0,
            "warningThresholdPercent", 90.0,
            "hardStopEnabled", false
        );

        ResponseEntity<AiWorkspaceBudget> response = controller.updateBudget(body);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(75.0, response.getBody().getMonthlyUsdBudget());
        assertEquals(90.0, response.getBody().getWarningThresholdPercent());
        assertEquals(false, response.getBody().getHardStopEnabled());
    }
}
