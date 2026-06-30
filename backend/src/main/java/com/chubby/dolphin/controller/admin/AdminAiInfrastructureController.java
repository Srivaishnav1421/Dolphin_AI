package com.chubby.dolphin.controller.admin;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.AiWorkspaceBudget;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.entity.WorkspaceAiTaskRoute;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.repository.AiResponseCacheRepository;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import com.chubby.dolphin.repository.AiWorkspaceBudgetRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.repository.WorkspaceAiTaskRouteRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.ai.AIService;
import com.chubby.dolphin.service.ai.AiProviderRouterService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
public class AdminAiInfrastructureController {

    private final AiProviderRouterService llmRouter;
    private final WorkspaceAiConfigRepository workspaceAiConfigRepo;
    private final AiWorkspaceBudgetRepository budgetRepo;
    private final AiUsageLogRepository usageLogRepo;
    private final AiResponseCacheRepository cacheRepo;
    private final WorkspaceAiTaskRouteRepository taskRouteRepo;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final AuditLogService auditLogService;

    private static final List<Map<String, String>> AI_TASKS = List.of(
            Map.of("key", "GROWTH_HOME", "label", "Growth Home"),
            Map.of("key", "CRM_LEAD_SCORING", "label", "CRM lead scoring"),
            Map.of("key", "CAMPAIGN_ANALYSIS", "label", "Campaign analysis"),
            Map.of("key", "CREATIVE_STUDIO", "label", "Creative Studio"),
            Map.of("key", "AI_INSIGHTS", "label", "AI Insights"),
            Map.of("key", "AUTOMATION", "label", "Automation"),
            Map.of("key", "ANALYTICS", "label", "Analytics"),
            Map.of("key", "GENERAL_ASSISTANT", "label", "General assistant")
    );

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        access.requireWorkspacePermission(Permission.AI_PROVIDER_READ);
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
            pInfo.put("usable", llmRouter.isProviderUsableForWorkspace(service, workspaceId));
            pInfo.put("model", service != null ? service.getModelName() : "Unknown");
            providersList.add(pInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("activeProvider", activeProvider.name());
        response.put("providers", providersList);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/routing")
    public ResponseEntity<Map<String, Object>> getRouting() {
        access.requireWorkspacePermission(Permission.AI_ROUTE_READ);
        String workspaceId = sec.currentAccountId();
        WorkspaceAiConfig config = workspaceAiConfigRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceAiConfig.builder().workspaceId(workspaceId).build());

        Map<String, String> routes = new HashMap<>();
        for (WorkspaceAiTaskRoute route : taskRouteRepo.findAllByWorkspaceId(workspaceId)) {
            routes.put(route.getTaskKey(), route.getProvider().name());
        }

        List<Map<String, Object>> providers = new ArrayList<>();
        Map<LlmProvider, AIService> providerMap = llmRouter.getProviderMap();
        for (LlmProvider provider : LlmProvider.values()) {
            AIService service = providerMap.get(provider);
            providers.add(Map.of(
                    "provider", provider.name(),
                    "model", service != null ? service.getModelName() : "Unknown",
                    "enabled", service != null && service.isEnabled(),
                    "available", service != null && service.isAvailable(),
                    "usable", llmRouter.isProviderUsableForWorkspace(service, workspaceId)
            ));
        }

        return ResponseEntity.ok(Map.of(
                "workspaceId", workspaceId,
                "defaultProvider", config.getActiveProvider().name(),
                "taskRoutes", routes,
                "tasks", AI_TASKS,
                "providers", providers
        ));
    }

    @PostMapping("/routing/default")
    public ResponseEntity<Map<String, Object>> updateDefaultRoute(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.AI_ROUTE_MANAGE);
        String workspaceId = sec.currentAccountId();
        LlmProvider provider;
        try {
            provider = parseProvider(body.get("provider"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        AIService service = llmRouter.getProviderMap().get(provider);
        if (!llmRouter.isProviderUsableForWorkspace(service, workspaceId)) {
            return ResponseEntity.badRequest().body(Map.of("error", provider.name() + " is not connected or available for this workspace"));
        }

        WorkspaceAiConfig config = workspaceAiConfigRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceAiConfig.builder().workspaceId(workspaceId).build());
        config.setActiveProvider(provider);
        workspaceAiConfigRepo.save(config);
        auditAi("AI_DEFAULT_ROUTE_CHANGED", workspaceId, "Default AI provider changed to " + provider.name());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "defaultProvider", provider.name()
        ));
    }

    @PostMapping("/routing/tasks/{taskKey}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateTaskRoute(@PathVariable String taskKey,
                                                               @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.AI_ROUTE_MANAGE);
        String workspaceId = sec.currentAccountId();
        String normalizedTask = normalizeTaskKey(taskKey);
        String providerValue = body.get("provider");
        if (providerValue == null || providerValue.isBlank() || "DEFAULT".equalsIgnoreCase(providerValue)) {
            taskRouteRepo.deleteByWorkspaceIdAndTaskKey(workspaceId, normalizedTask);
            auditAi("AI_TASK_ROUTE_CLEARED", workspaceId, "Task route cleared for " + normalizedTask);
            return ResponseEntity.ok(Map.of("success", true, "taskKey", normalizedTask, "provider", "DEFAULT"));
        }

        LlmProvider provider;
        try {
            provider = parseProvider(providerValue);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        AIService service = llmRouter.getProviderMap().get(provider);
        if (!llmRouter.isProviderUsableForWorkspace(service, workspaceId)) {
            return ResponseEntity.badRequest().body(Map.of("error", provider.name() + " is not connected or available for this workspace"));
        }

        WorkspaceAiTaskRoute route = taskRouteRepo.findByWorkspaceIdAndTaskKey(workspaceId, normalizedTask)
                .orElseGet(() -> WorkspaceAiTaskRoute.builder()
                        .workspaceId(workspaceId)
                        .taskKey(normalizedTask)
                        .build());
        route.setProvider(provider);
        taskRouteRepo.save(route);
        auditAi("AI_TASK_ROUTE_CHANGED", workspaceId, "Task route " + normalizedTask + " changed to " + provider.name());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "taskKey", normalizedTask,
                "provider", provider.name()
        ));
    }

    @DeleteMapping("/routing/tasks/{taskKey}")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearTaskRoute(@PathVariable String taskKey) {
        access.requireWorkspacePermission(Permission.AI_ROUTE_MANAGE);
        String workspaceId = sec.currentAccountId();
        String normalizedTask = normalizeTaskKey(taskKey);
        taskRouteRepo.deleteByWorkspaceIdAndTaskKey(workspaceId, normalizedTask);
        auditAi("AI_TASK_ROUTE_CLEARED", workspaceId, "Task route cleared for " + normalizedTask);
        return ResponseEntity.ok(Map.of("success", true, "taskKey", normalizedTask, "provider", "DEFAULT"));
    }

    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchProvider(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.AI_PROVIDER_MANAGE);
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

        AIService service = llmRouter.getProviderMap().get(newProvider);
        if (!llmRouter.isProviderUsableForWorkspace(service, workspaceId)) {
            return ResponseEntity.badRequest().body(Map.of("error", newProvider.name() + " is not connected or available for this workspace"));
        }

        WorkspaceAiConfig config = workspaceAiConfigRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceAiConfig.builder().workspaceId(workspaceId).build());

        config.setActiveProvider(newProvider);
        workspaceAiConfigRepo.save(config);
        auditAi("AI_PROVIDER_SWITCHED", workspaceId, "Active AI provider switched to " + newProvider.name());

        log.info("🤖 Workspace {} switched active LLM provider to: {}", workspaceId, newProvider);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "activeProvider", newProvider.name()
        ));
    }

    private LlmProvider parseProvider(String providerStr) {
        if (providerStr == null || providerStr.isBlank()) {
            throw new IllegalArgumentException("Provider name is required");
        }
        return LlmProvider.valueOf(providerStr.trim().toUpperCase());
    }

    private String normalizeTaskKey(String taskKey) {
        return taskKey == null ? "" : taskKey.trim().toUpperCase();
    }

    @GetMapping("/usage-stats")
    public ResponseEntity<Map<String, Object>> getUsageStats() {
        access.requireWorkspacePermission(Permission.AI_PROVIDER_READ);
        String workspaceId = sec.currentAccountId();
        LocalDateTime firstDayOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        double monthlySpend = usageLogRepo.sumCostByWorkspaceIdSince(workspaceId, firstDayOfMonth);
        long monthlyTokens = usageLogRepo.sumTokensByWorkspaceIdSince(workspaceId, firstDayOfMonth);
        long workspaceRequests = usageLogRepo.countByAccountIdAndCreatedAtGreaterThanEqual(workspaceId, firstDayOfMonth);

        AiWorkspaceBudget budget = budgetRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> AiWorkspaceBudget.builder().workspaceId(workspaceId).build());

        Map<String, Object> stats = new HashMap<>();
        stats.put("workspaceId", workspaceId);
        stats.put("currentMonthlySpendUsd", monthlySpend);
        stats.put("currentMonthlyTokens", monthlyTokens);
        stats.put("workspaceRequests", workspaceRequests);
        stats.put("cacheHitRatePercent", null);
        stats.put("monthlyBudgetLimitUsd", budget.getMonthlyUsdBudget());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/budgets")
    public ResponseEntity<AiWorkspaceBudget> getBudget() {
        access.requireWorkspacePermission(Permission.AI_PROVIDER_READ);
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
        access.requireWorkspacePermission(Permission.AI_PROVIDER_MANAGE);
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
        auditAi("AI_BUDGET_UPDATED", workspaceId, "AI budget updated");
        log.info("💰 Updated AI budget limits for workspace: {}", workspaceId);
        return ResponseEntity.ok(saved);
    }

    private void auditAi(String action, String workspaceId, String details) {
        User actor = access.currentUser();
        auditLogService.record(actor, actor.getOrganization(), workspaceId, action, "AIInfrastructure", workspaceId, details);
    }
}
