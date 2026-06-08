package com.chubby.dolphin.controller.admin;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-infrastructure")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AdminAiInfrastructureController {

    private final AiProviderRouterService llmRouter;
    private final WorkspaceAiConfigRepository workspaceAiConfigRepo;
    private final AiWorkspaceBudgetRepository budgetRepo;
    private final AiUsageLogRepository usageLogRepo;
    private final AiResponseCacheRepository cacheRepo;
    private final SecurityUtils sec;

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        String workspaceId = sec.currentAccountId();
        LlmProvider activeProvider = LlmProvider.MOCK;

        try {
            AIService resolved = llmRouter.resolveProvider(workspaceId);
            if (resolved != null) {
                activeProvider = resolved.getProvider();
            }
        } catch (Exception e) {
            log.warn("Could not dynamically resolve active provider, using default: {}", e.getMessage());
        }

        Map<LlmProvider, AIService> providerMap = llmRouter.getProviderMap();
        List<Map<String, Object>> providersList = new ArrayList<>();

        for (LlmProvider provider : LlmProvider.values()) {
            AIService service = providerMap.get(provider);
            Map<String, Object> pInfo = new HashMap<>();
            pInfo.put("provider", provider.name());
            pInfo.put("enabled", service != null && service.isEnabled());
            pInfo.put("available", service != null && service.isAvailable());
            pInfo.put("model", service != null ? service.getModelName() : "Unknown");
            providersList.add(pInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("activeProvider", activeProvider.name());
        response.put("providers", providersList);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchProvider(@RequestBody Map<String, String> body) {
        String workspaceId = sec.currentAccountId();
        String providerStr = body.get("provider");

        if (providerStr == null || providerStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider name is required"));
        }

        LlmProvider newProvider;
        try {
            newProvider = LlmProvider.valueOf(providerStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid provider: " + providerStr));
        }

        WorkspaceAiConfig config = workspaceAiConfigRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceAiConfig.builder().workspaceId(workspaceId).build());

        config.setActiveProvider(newProvider);
        workspaceAiConfigRepo.save(config);

        log.info("🤖 Workspace {} switched active LLM provider to: {}", workspaceId, newProvider);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "activeProvider", newProvider.name()
        ));
    }

    @GetMapping("/usage-stats")
    public ResponseEntity<Map<String, Object>> getUsageStats() {
        String workspaceId = sec.currentAccountId();
        LocalDateTime firstDayOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        double monthlySpend = usageLogRepo.sumCostByWorkspaceIdSince(workspaceId, firstDayOfMonth);
        long monthlyTokens = usageLogRepo.sumTokensByWorkspaceIdSince(workspaceId, firstDayOfMonth);
        long totalRequests = usageLogRepo.count(); // global frequency for context

        long cacheCount = cacheRepo.count();
        double cacheHitRate = 0.0;
        if (cacheCount + totalRequests > 0) {
            cacheHitRate = ((double) cacheCount / (cacheCount + totalRequests)) * 100.0;
        }

        AiWorkspaceBudget budget = budgetRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> AiWorkspaceBudget.builder().workspaceId(workspaceId).build());

        Map<String, Object> stats = new HashMap<>();
        stats.put("workspaceId", workspaceId);
        stats.put("currentMonthlySpendUsd", monthlySpend);
        stats.put("currentMonthlyTokens", monthlyTokens);
        stats.put("totalRequests", totalRequests);
        stats.put("cacheHitRatePercent", Math.round(cacheHitRate * 10.0) / 10.0);
        stats.put("monthlyBudgetLimitUsd", budget.getMonthlyUsdBudget());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/budgets")
    public ResponseEntity<AiWorkspaceBudget> getBudget() {
        String workspaceId = sec.currentAccountId();
        AiWorkspaceBudget budget = budgetRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    AiWorkspaceBudget newBudget = AiWorkspaceBudget.builder().workspaceId(workspaceId).build();
                    return budgetRepo.save(newBudget);
                });
        return ResponseEntity.ok(budget);
    }

    @PostMapping("/budgets")
    public ResponseEntity<AiWorkspaceBudget> updateBudget(@RequestBody Map<String, Object> body) {
        String workspaceId = sec.currentAccountId();
        AiWorkspaceBudget budget = budgetRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> AiWorkspaceBudget.builder().workspaceId(workspaceId).build());

        if (body.containsKey("monthlyUsdBudget")) {
            budget.setMonthlyUsdBudget(Double.valueOf(body.get("monthlyUsdBudget").toString()));
        }
        if (body.containsKey("warningThresholdPercent")) {
            budget.setWarningThresholdPercent(Double.valueOf(body.get("warningThresholdPercent").toString()));
        }
        if (body.containsKey("hardStopEnabled")) {
            budget.setHardStopEnabled(Boolean.valueOf(body.get("hardStopEnabled").toString()));
        }

        AiWorkspaceBudget saved = budgetRepo.save(budget);
        log.info("💰 Updated AI budget limits for workspace: {}", workspaceId);
        return ResponseEntity.ok(saved);
    }
}
