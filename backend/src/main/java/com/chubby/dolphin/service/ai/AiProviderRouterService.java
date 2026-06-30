package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.AiResponseCache;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.entity.WorkspaceAiTaskRoute;
import com.chubby.dolphin.repository.IntegrationSettingRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.repository.WorkspaceAiTaskRouteRepository;
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
    private final IntegrationSettingRepository integrationSettingRepository;
    private final WorkspaceAiTaskRouteRepository workspaceAiTaskRouteRepository;
    private final PromptHashService promptHashService;
    private final AiCacheService aiCacheService;
    private final AiUsageAuditService aiUsageAuditService;
    private final AiBudgetService aiBudgetService;

    private final Map<LlmProvider, AIService> providerMap = new EnumMap<>(LlmProvider.class);

    public AiProviderRouterService(List<AIService> aiServices, 
                            WorkspaceAiConfigRepository workspaceAiConfigRepo,
                            IntegrationSettingRepository integrationSettingRepository,
                            WorkspaceAiTaskRouteRepository workspaceAiTaskRouteRepository,
                            PromptHashService promptHashService,
                            AiCacheService aiCacheService,
                            AiUsageAuditService aiUsageAuditService,
                            AiBudgetService aiBudgetService) {
        this.aiServices = aiServices;
        this.workspaceAiConfigRepo = workspaceAiConfigRepo;
        this.integrationSettingRepository = integrationSettingRepository;
        this.workspaceAiTaskRouteRepository = workspaceAiTaskRouteRepository;
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
        return resolveProvider(workspaceId, null);
    }

    public AIService resolveProvider(String workspaceId, String taskKey) {
        if (workspaceId != null && !workspaceId.isBlank() && taskKey != null && !taskKey.isBlank()) {
            Optional<WorkspaceAiTaskRoute> routeOpt = workspaceAiTaskRouteRepository.findByWorkspaceIdAndTaskKey(workspaceId, normalizeTaskKey(taskKey));
            if (routeOpt.isPresent()) {
                AIService service = providerMap.get(routeOpt.get().getProvider());
                if (isProviderUsableForWorkspace(service, workspaceId)) {
                    return service;
                }
                log.warn("Configured AI provider {} for task {} is not usable in workspace {}. Falling back to workspace default.",
                        routeOpt.get().getProvider(), taskKey, workspaceId);
            }
        }

        if (workspaceId != null && !workspaceId.isBlank()) {
            Optional<WorkspaceAiConfig> configOpt = workspaceAiConfigRepo.findByWorkspaceId(workspaceId);
            if (configOpt.isPresent()) {
                WorkspaceAiConfig config = configOpt.get();
                LlmProvider preferredProvider = config.getActiveProvider();
                AIService service = providerMap.get(preferredProvider);
                if (isProviderUsableForWorkspace(service, workspaceId)) {
                    return service;
                }
            }
        }

        for (LlmProvider provider : fallbackOrder()) {
            AIService service = providerMap.get(provider);
            if (isProviderUsableForWorkspace(service, workspaceId)) {
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
        AIService provider = resolveProvider(workspaceId, request != null ? request.getTaskKey() : null);

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
        LlmResponse response = executeWithFallback(workspaceId, request, provider, false);
        response.setCached(false);

        // 5. Save response cache
        if (cachingEnabled && promptHash != null && !promptHash.isEmpty()) {
            aiCacheService.put(promptHash, response, providerFromResponse(response, provider.getProvider()), Duration.ofHours(24));
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

        AIService provider = resolveProvider(workspaceId, request != null ? request.getTaskKey() : null);
        LlmResponse response = executeWithFallback(workspaceId, request, provider, true);
        response.setCached(false);

        if (cachingEnabled && promptHash != null && !promptHash.isEmpty()) {
            aiCacheService.put(promptHash, response, providerFromResponse(response, provider.getProvider()), Duration.ofHours(24));
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

    private LlmResponse executeWithFallback(String workspaceId, LlmRequest request, AIService primaryProvider, boolean chatMode) {
        try {
            return chatMode ? primaryProvider.chat(request) : primaryProvider.ask(request);
        } catch (Exception primaryError) {
            if (!isFallbackRoutingEnabled(workspaceId)) {
                throw primaryError;
            }

            log.warn("AI provider {} failed for task {} in workspace {}. Trying fallback chain: {}",
                    primaryProvider.getProvider(),
                    request != null ? request.getTaskKey() : "GENERAL",
                    workspaceId,
                    primaryError.getMessage());

            RuntimeException lastError = primaryError instanceof RuntimeException
                    ? (RuntimeException) primaryError
                    : new RuntimeException(primaryError);

            for (LlmProvider fallbackProvider : fallbackOrder()) {
                AIService fallbackService = providerMap.get(fallbackProvider);
                if (fallbackService == null || fallbackService.getProvider() == primaryProvider.getProvider()) {
                    continue;
                }
                if (!isProviderUsableForWorkspace(fallbackService, workspaceId)) {
                    continue;
                }
                try {
                    LlmResponse response = chatMode ? fallbackService.chat(request) : fallbackService.ask(request);
                    log.warn("AI fallback succeeded with provider {} for task {} in workspace {}",
                            fallbackService.getProvider(),
                            request != null ? request.getTaskKey() : "GENERAL",
                            workspaceId);
                    return response;
                } catch (Exception fallbackError) {
                    lastError = fallbackError instanceof RuntimeException
                            ? (RuntimeException) fallbackError
                            : new RuntimeException(fallbackError);
                    log.warn("AI fallback provider {} failed: {}", fallbackService.getProvider(), fallbackError.getMessage());
                }
            }
            throw lastError;
        }
    }

    private boolean isFallbackRoutingEnabled(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return true;
        }
        return workspaceAiConfigRepo.findByWorkspaceId(workspaceId)
                .map(WorkspaceAiConfig::getEnableFallbackRouting)
                .orElse(true);
    }

    private LlmProvider[] fallbackOrder() {
        return new LlmProvider[]{LlmProvider.OPENAI, LlmProvider.GEMINI, LlmProvider.ANTHROPIC, LlmProvider.OLLAMA, LlmProvider.HUGGINGFACE, LlmProvider.MOCK};
    }

    private LlmProvider providerFromResponse(LlmResponse response, LlmProvider defaultProvider) {
        if (response == null || response.getProvider() == null || response.getProvider().isBlank()) {
            return defaultProvider;
        }
        try {
            return LlmProvider.valueOf(response.getProvider().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultProvider;
        }
    }

    public boolean isProviderUsableForWorkspace(AIService service, String workspaceId) {
        if (service == null || !service.isEnabled()) {
            return false;
        }
        if (usesWorkspaceIntegrationCredentials(service.getProvider())) {
            return hasWorkspaceProviderCredentials(workspaceId, service.getProvider()) || service.isAvailable();
        }
        return service.isAvailable();
    }

    private boolean usesWorkspaceIntegrationCredentials(LlmProvider provider) {
        return provider == LlmProvider.OPENAI
                || provider == LlmProvider.GEMINI
                || provider == LlmProvider.ANTHROPIC
                || provider == LlmProvider.HUGGINGFACE;
    }

    private boolean hasWorkspaceProviderCredentials(String workspaceId, LlmProvider provider) {
        if (workspaceId == null || workspaceId.isBlank() || provider == null) {
            return false;
        }
        return integrationSettingRepository.existsByWorkspaceIdAndProviderId(workspaceId, provider.name().toLowerCase());
    }

    private String normalizeTaskKey(String taskKey) {
        return taskKey == null ? "" : taskKey.trim().toUpperCase();
    }

    public Map<LlmProvider, AIService> getProviderMap() {
        return providerMap;
    }
}
