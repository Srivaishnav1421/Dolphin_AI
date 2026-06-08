package com.chubby.dolphin.controller.ai;

import com.chubby.dolphin.controller.admin.AdminAiInfrastructureController;
import com.chubby.dolphin.entity.AiWorkspaceBudget;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.repository.AiResponseCacheRepository;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import com.chubby.dolphin.repository.AiWorkspaceBudgetRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
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
    @Mock private SecurityUtils sec;

    @Mock private AIService ollamaService;
    @Mock private AIService huggingFaceService;
    @Mock private AIService mockService;

    private AdminAiInfrastructureController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new AdminAiInfrastructureController(llmRouter, workspaceAiConfigRepo, budgetRepo, usageLogRepo, cacheRepo, sec);

        when(sec.currentAccountId()).thenReturn("ws-123");

        // Set up router mocks
        Map<LlmProvider, AIService> providerMap = new EnumMap<>(LlmProvider.class);
        providerMap.put(LlmProvider.OLLAMA, ollamaService);
        providerMap.put(LlmProvider.HUGGINGFACE, huggingFaceService);
        providerMap.put(LlmProvider.MOCK, mockService);
        when(llmRouter.getProviderMap()).thenReturn(providerMap);

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
        assertEquals(3, list.size());

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
        when(usageLogRepo.count()).thenReturn(100L);
        when(cacheRepo.count()).thenReturn(25L);

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
        assertEquals(20.0, body.get("cacheHitRatePercent")); // 25 / (25 + 100) = 25/125 = 0.20 = 20%
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
