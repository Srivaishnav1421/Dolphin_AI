package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.repository.IntegrationSettingRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.repository.WorkspaceAiTaskRouteRepository;
import com.chubby.dolphin.service.ai.cache.AiCacheService;
import com.chubby.dolphin.service.ai.cache.PromptHashService;
import com.chubby.dolphin.service.ai.audit.AiBudgetService;
import com.chubby.dolphin.service.ai.audit.AiUsageAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AiProviderRouterServiceTest {

    @Mock
    private WorkspaceAiConfigRepository workspaceAiConfigRepo;

    @Mock
    private IntegrationSettingRepository integrationSettingRepository;

    @Mock
    private WorkspaceAiTaskRouteRepository workspaceAiTaskRouteRepository;

    @Mock
    private PromptHashService promptHashService;

    @Mock
    private AiCacheService aiCacheService;

    @Mock
    private AiUsageAuditService aiUsageAuditService;

    @Mock
    private AiBudgetService aiBudgetService;

    @Mock
    private OllamaAiService ollamaService;

    @Mock
    private HuggingFaceAiService huggingFaceService;

    @Mock
    private MockAiService mockService;

    private AiProviderRouterService llmRouterService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Configure getProvider() on services
        when(ollamaService.getProvider()).thenReturn(LlmProvider.OLLAMA);
        when(huggingFaceService.getProvider()).thenReturn(LlmProvider.HUGGINGFACE);
        when(mockService.getProvider()).thenReturn(LlmProvider.MOCK);

        List<AIService> services = new ArrayList<>();
        services.add(ollamaService);
        services.add(huggingFaceService);
        services.add(mockService);

        // Configure default mock configurations to prevent null pointer exceptions
        when(promptHashService.hashRequest(any())).thenReturn("abc123hash");
        when(aiBudgetService.withinBudget(any())).thenReturn(true);

        llmRouterService = new AiProviderRouterService(
                services, 
                workspaceAiConfigRepo,
                integrationSettingRepository,
                workspaceAiTaskRouteRepository,
                promptHashService,
                aiCacheService,
                aiUsageAuditService,
                aiBudgetService
        );
    }

    @Test
    public void testProviderMapInitializationWorks() {
        llmRouterService.init();
        assertEquals(3, llmRouterService.getProviderMap().size());
        assertEquals(ollamaService, llmRouterService.getProviderMap().get(LlmProvider.OLLAMA));
        assertEquals(huggingFaceService, llmRouterService.getProviderMap().get(LlmProvider.HUGGINGFACE));
        assertEquals(mockService, llmRouterService.getProviderMap().get(LlmProvider.MOCK));
    }

    @Test
    public void testWorkspaceProviderSelectedCorrectly() {
        llmRouterService.init();

        // Workspace config exists with MOCK active
        WorkspaceAiConfig config = WorkspaceAiConfig.builder()
                .workspaceId("ws-1")
                .activeProvider(LlmProvider.MOCK)
                .build();
        when(workspaceAiConfigRepo.findByWorkspaceId("ws-1")).thenReturn(Optional.of(config));

        // MOCK is enabled and available
        when(mockService.isEnabled()).thenReturn(true);
        when(mockService.isAvailable()).thenReturn(true);

        AIService resolved = llmRouterService.resolveProvider("ws-1");
        assertNotNull(resolved);
        assertEquals(LlmProvider.MOCK, resolved.getProvider());
    }

    @Test
    public void testFallbackChainWorksWhenWorkspaceProviderUnavailable() {
        llmRouterService.init();

        // Workspace config specifies HUGGINGFACE
        WorkspaceAiConfig config = WorkspaceAiConfig.builder()
                .workspaceId("ws-1")
                .activeProvider(LlmProvider.HUGGINGFACE)
                .build();
        when(workspaceAiConfigRepo.findByWorkspaceId("ws-1")).thenReturn(Optional.of(config));

        // HUGGINGFACE is unavailable
        when(huggingFaceService.isEnabled()).thenReturn(false);
        when(huggingFaceService.isAvailable()).thenReturn(false);

        // OLLAMA (first fallback choice) is enabled and available
        when(ollamaService.isEnabled()).thenReturn(true);
        when(ollamaService.isAvailable()).thenReturn(true);

        AIService resolved = llmRouterService.resolveProvider("ws-1");
        assertNotNull(resolved);
        assertEquals(LlmProvider.OLLAMA, resolved.getProvider());
    }

    @Test
    public void testExceptionThrownWhenNoProviderAvailable() {
        llmRouterService.init();

        // All providers disabled/unavailable
        when(ollamaService.isEnabled()).thenReturn(false);
        when(ollamaService.isAvailable()).thenReturn(false);
        when(huggingFaceService.isEnabled()).thenReturn(false);
        when(huggingFaceService.isAvailable()).thenReturn(false);
        when(mockService.isEnabled()).thenReturn(false);
        when(mockService.isAvailable()).thenReturn(false);

        when(workspaceAiConfigRepo.findByWorkspaceId(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> {
            llmRouterService.resolveProvider("any-ws");
        });
    }

    @Test
    public void testAskDelegationWorks() {
        llmRouterService.init();

        // Ollama available
        when(ollamaService.isEnabled()).thenReturn(true);
        when(ollamaService.isAvailable()).thenReturn(true);
        when(workspaceAiConfigRepo.findByWorkspaceId(anyString())).thenReturn(Optional.empty());

        LlmRequest request = LlmRequest.builder()
                .prompt("Hello")
                .build();
        LlmResponse expectedResponse = LlmResponse.builder()
                .content("[OLLAMA STUB]")
                .provider("OLLAMA")
                .build();

        when(ollamaService.ask(request)).thenReturn(expectedResponse);

        // Setup mock configurations
        when(aiBudgetService.withinBudget(anyString())).thenReturn(true);
        when(aiCacheService.get(anyString())).thenReturn(Optional.empty());

        LlmResponse actual = llmRouterService.ask("ws-1", request);
        assertNotNull(actual);
        assertEquals("[OLLAMA STUB]", actual.getContent());
    }

    @Test
    public void testAskFallsBackWhenResolvedProviderFails() {
        llmRouterService.init();

        when(ollamaService.isEnabled()).thenReturn(true);
        when(ollamaService.isAvailable()).thenReturn(true);
        when(mockService.isEnabled()).thenReturn(true);
        when(mockService.isAvailable()).thenReturn(true);
        when(workspaceAiConfigRepo.findByWorkspaceId("ws-1")).thenReturn(Optional.empty());
        when(aiCacheService.get(anyString())).thenReturn(Optional.empty());

        LlmRequest request = LlmRequest.builder()
                .workspaceId("ws-1")
                .taskKey("CREATIVE_STUDIO")
                .prompt("Generate ad copy")
                .build();

        when(ollamaService.ask(request)).thenThrow(new RuntimeException("Ollama offline"));
        when(mockService.ask(request)).thenReturn(LlmResponse.builder()
                .content("fallback response")
                .provider("MOCK")
                .model("mock")
                .build());

        LlmResponse actual = llmRouterService.ask("ws-1", request);

        assertNotNull(actual);
        assertEquals("fallback response", actual.getContent());
        assertEquals("MOCK", actual.getProvider());
        verify(mockService, times(1)).ask(request);
    }
}
