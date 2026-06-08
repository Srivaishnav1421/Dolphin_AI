package com.chubby.dolphin.controller;

import com.chubby.dolphin.brain.BrainGovernanceService;
import com.chubby.dolphin.brain.strategy.*;
import com.chubby.dolphin.brain.strategy.dto.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cmo")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@AllArgsConstructor
@Slf4j
public class CmoController {

    private final StrategicGoalEngine strategicGoalEngine;
    private final RevenueForecastEngine revenueForecastEngine;
    private final BudgetOptimizationEngine budgetOptimizationEngine;
    private final CompetitorMonitoringService competitorMonitoringService;
    private final CmoMemoryService cmoMemoryService;
    private final BrainGovernanceService governanceService;
    private final com.chubby.dolphin.security.SecurityUtils sec;

    private String resolveWorkspaceId(String requestedWorkspaceId) {
        String active = sec.currentWorkspaceId();
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return requestedWorkspaceId != null && !requestedWorkspaceId.isBlank() ? requestedWorkspaceId : active;
        }
        return active;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<CmoDashboardDto> getCmoDashboard(
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        
        String targetWorkspace = resolveWorkspaceId(workspaceId);
        log.info("🧠 Fetching AI Chief Marketing Officer (CMO) cockpit for workspace: {}", targetWorkspace);

        StrategicPlan plan = strategicGoalEngine.generatePlan(targetWorkspace);
        RevenueForecast forecast = revenueForecastEngine.generateForecast(targetWorkspace);
        BudgetRecommendation budget = budgetOptimizationEngine.optimizeBudgets(targetWorkspace);
        Map<String, Object> competitorThreats = competitorMonitoringService.calculateThreats(targetWorkspace);
        Map<String, Object> learningMetrics = cmoMemoryService.getMemory(targetWorkspace);
        double govScore = governanceService.evaluateGovernance(targetWorkspace, null, "SCALE_UP", 1000.0, 1200.0);

        CmoDashboardDto dto = CmoDashboardDto.builder()
                .strategicPlan(plan)
                .revenueForecast(forecast)
                .budgetOptimization(budget)
                .competitorThreats(competitorThreats)
                .learningMetrics(learningMetrics)
                .governanceScore(govScore)
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/forecast")
    public ResponseEntity<RevenueForecast> getForecast(
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ResponseEntity.ok(revenueForecastEngine.generateForecast(resolveWorkspaceId(workspaceId)));
    }

    @GetMapping("/strategy")
    public ResponseEntity<StrategicPlan> getStrategy(
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ResponseEntity.ok(strategicGoalEngine.generatePlan(resolveWorkspaceId(workspaceId)));
    }

    @GetMapping("/competitors")
    public ResponseEntity<Map<String, Object>> getCompetitors(
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        return ResponseEntity.ok(competitorMonitoringService.calculateThreats(resolveWorkspaceId(workspaceId)));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CmoDashboardDto {
        private StrategicPlan strategicPlan;
        private RevenueForecast revenueForecast;
        private BudgetRecommendation budgetOptimization;
        private Map<String, Object> competitorThreats;
        private Map<String, Object> learningMetrics;
        private double governanceScore;
    }
}
