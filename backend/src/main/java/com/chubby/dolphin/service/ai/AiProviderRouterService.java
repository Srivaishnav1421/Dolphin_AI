package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.AiResponseCache;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.service.ai.cache.AiCacheService;
import com.chubby.dolphin.service.ai.cache.PromptHashService;
import com.chubby.dolphin.service.ai.audit.AiBudgetService;
import com.chubby.dolphin.service.ai.audit.AiUsageAuditService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service("enterpriseLlmRouterService")
@Slf4j
public class AiProviderRouterService {

    private final List<AIService> aiServices;
    private final WorkspaceAiConfigRepository workspaceAiConfigRepo;
    private final PromptHashService promptHashService;
    private final AiCacheService aiCacheService;
    private final AiUsageAuditService aiUsageAuditService;
    private final AiBudgetService aiBudgetService;

    private final Map<LlmProvider, AIService> providerMap = new EnumMap<>(LlmProvider.class);

    public AiProviderRouterService(List<AIService> aiServices, 
                            WorkspaceAiConfigRepository workspaceAiConfigRepo,
                            PromptHashService promptHashService,
                            AiCacheService aiCacheService,
                            AiUsageAuditService aiUsageAuditService,
                            AiBudgetService aiBudgetService) {
        this.aiServices = aiServices;
        this.workspaceAiConfigRepo = workspaceAiConfigRepo;
        this.promptHashService = promptHashService;
        this.aiCacheService = aiCacheService;
        this.aiUsageAuditService = aiUsageAuditService;
        this.aiBudgetService = aiBudgetService;
    }

    @PostConstruct
    public void init() {
        for (AIService service : aiServices) {
            providerMap.put(service.getProvider(), service);
        }

        log.info("==================================================");
        log.info("🧠 Chubby Dolphin Enterprise AI Router Discovered:");
        for (LlmProvider provider : LlmProvider.values()) {
            AIService service = providerMap.get(provider);
            boolean enabled = service != null && service.isEnabled();
            boolean available = service != null && service.isAvailable();
            log.info("   Provider: {} | Enabled: {} | Available: {}", provider, enabled, available);
        }
        log.info("==================================================");
    }

    /**
     * Resolves the appropriate AI provider for a workspace.
     */
    public AIService resolveProvider(String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            Optional<WorkspaceAiConfig> configOpt = workspaceAiConfigRepo.findByWorkspaceId(workspaceId);
            if (configOpt.isPresent()) {
                WorkspaceAiConfig config = configOpt.get();
                LlmProvider preferredProvider = config.getActiveProvider();
                AIService service = providerMap.get(preferredProvider);
                if (service != null && service.isEnabled() && service.isAvailable()) {
                    return service;
                }
            }
        }

        LlmProvider[] fallbacks = {LlmProvider.OLLAMA, LlmProvider.HUGGINGFACE, LlmProvider.MOCK};
        for (LlmProvider provider : fallbacks) {
            AIService service = providerMap.get(provider);
            if (service != null && service.isEnabled() && service.isAvailable()) {
                return service;
            }
        }

        throw new IllegalStateException("No AI provider available");
    }

    /**
     * Unified execution logic carrying out spend audits, budget gates, and caching operations.
     */
    public LlmResponse ask(String workspaceId, LlmRequest request) {
        // 1. Resolve active provider
        AIService provider = resolveProvider(workspaceId);

        // 2. Enforce limits and budget blocks
        aiBudgetService.withinBudget(workspaceId);

        // 3. Cache lookup
        boolean cachingEnabled = isCachingEnabled(workspaceId);
        String promptHash = "";
        if (cachingEnabled) {
            promptHash = promptHashService.hashRequest(request);
            Optional<AiResponseCache> cachedResponseOpt = aiCacheService.get(promptHash);
            if (cachedResponseOpt.isPresent()) {
                AiResponseCache cached = cachedResponseOpt.get();
                return LlmResponse.builder()
                        .content(cached.getCachedResponse())
                        .provider(cached.getProvider().name())
                        .model(cached.getModel())
                        .promptTokens(cached.getPromptTokens())
                        .completionTokens(cached.getCompletionTokens())
                        .totalTokens(cached.getTotalTokens())
                        .estimatedCostUsd(0.0) // Cached hits cost $0.0 USD
                        .cached(true)
                        .build();
            }
        }

        // 4. Cache Miss - execute provider
        LlmResponse response = provider.ask(request);
        response.setCached(false);

        // 5. Save response cache
        if (cachingEnabled && promptHash != null && !promptHash.isEmpty()) {
            aiCacheService.put(promptHash, response, provider.getProvider(), Duration.ofHours(24));
        }

        // 6. Persist auditing telemetry
        aiUsageAuditService.recordUsage(workspaceId, request, response);

        return response;
    }

    /**
     * Conversational user/system chat dispatch.
     */
    public LlmResponse chat(String workspaceId, LlmRequest request) {
        // Enforce budgets and checks identically
        aiBudgetService.withinBudget(workspaceId);

        boolean cachingEnabled = isCachingEnabled(workspaceId);
        String promptHash = "";
        if (cachingEnabled) {
            promptHash = promptHashService.hashRequest(request);
            Optional<AiResponseCache> cachedResponseOpt = aiCacheService.get(promptHash);
            if (cachedResponseOpt.isPresent()) {
                AiResponseCache cached = cachedResponseOpt.get();
                return LlmResponse.builder()
                        .content(cached.getCachedResponse())
                        .provider(cached.getProvider().name())
                        .model(cached.getModel())
                        .promptTokens(cached.getPromptTokens())
                        .completionTokens(cached.getCompletionTokens())
                        .totalTokens(cached.getTotalTokens())
                        .estimatedCostUsd(0.0)
                        .cached(true)
                        .build();
            }
        }

        AIService provider = resolveProvider(workspaceId);
        LlmResponse response = provider.chat(request);
        response.setCached(false);

        if (cachingEnabled && promptHash != null && !promptHash.isEmpty()) {
            aiCacheService.put(promptHash, response, provider.getProvider(), Duration.ofHours(24));
        }

        aiUsageAuditService.recordUsage(workspaceId, request, response);

        return response;
    }

    private boolean isCachingEnabled(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return true; // Default behavior
        }
        Optional<WorkspaceAiConfig> configOpt = workspaceAiConfigRepo.findByWorkspaceId(workspaceId);
        return configOpt.map(WorkspaceAiConfig::getEnableCaching).orElse(true);
    }

    public Map<LlmProvider, AIService> getProviderMap() {
        return providerMap;
    }
}
