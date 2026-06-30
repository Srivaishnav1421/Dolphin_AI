package com.chubby.dolphin.controller;

import com.chubby.dolphin.brain.BrainGovernanceService;
import com.chubby.dolphin.brain.BrainLearningEngine;
import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import com.chubby.dolphin.repository.LeadRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brain/analytics")
@Slf4j
public class BrainAnalyticsController {

    private final BrainDecisionRepository decisionRepo;
    private final LeadRepository leadRepository;
    private final BrainLearningEngine learningEngine;
    private final BrainGovernanceService governanceService;
    private final com.chubby.dolphin.security.SecurityUtils sec;
    private final com.chubby.dolphin.security.TenantAccessService tenantAccessService;
    private final AccessControlService access;

    @Autowired
    public BrainAnalyticsController(
            BrainDecisionRepository decisionRepo,
            LeadRepository leadRepository,
            BrainLearningEngine learningEngine,
            BrainGovernanceService governanceService,
            com.chubby.dolphin.security.SecurityUtils sec,
            com.chubby.dolphin.security.TenantAccessService tenantAccessService,
            AccessControlService access) {
        this.decisionRepo = decisionRepo;
        this.leadRepository = leadRepository;
        this.learningEngine = learningEngine;
        this.governanceService = governanceService;
        this.sec = sec;
        this.tenantAccessService = tenantAccessService;
        this.access = access;
    }

    @GetMapping
    public ResponseEntity<BrainAnalyticsDto> getBrainAnalytics(
            @RequestParam(value = "workspaceId", required = false) String workspaceId) {
        access.requireWorkspacePermission(Permission.ANALYTICS_READ);
        
        String activeWorkspaceId = sec.currentWorkspaceId();
        org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String targetWorkspaceId = activeWorkspaceId;
        if (isAdmin && workspaceId != null && !workspaceId.isBlank()) {
            tenantAccessService.requireWorkspaceAccess(auth.getName(), workspaceId);
            targetWorkspaceId = workspaceId;
        }

        log.info("📊 Fetching closing-loop Brain CMO Analytics for workspace: {}", targetWorkspaceId);

        List<BrainDecision> decisions = decisionRepo.findByAccountIdOrderByCreatedAtDesc(targetWorkspaceId);
        
        long generated = decisions.size();
        long approved = decisions.stream().filter(d -> "APPROVED".equals(d.getStatus()) || "EXECUTED".equals(d.getStatus())).count();
        long executed = decisions.stream().filter(d -> "EXECUTED".equals(d.getStatus()) || "EXECUTED_LOCAL_ONLY".equals(d.getStatus())).count();

        long successful = decisions.stream().filter(d -> d.getOutcomePositive() != null && d.getOutcomePositive()).count();
        double successRate = generated > 0 ? ((double) successful / generated) * 100.0 : 0.0;
        double failureRate = 100.0 - successRate;

        // Dynamic Revenue Lift
        double revenueImpact = decisions.stream()
                .filter(d -> d.getRoasAfterExecution() != null && d.getRoasAtDecision() != null)
                .mapToDouble(d -> (d.getRoasAfterExecution() - d.getRoasAtDecision()) * 5000.0)
                .filter(val -> val > 0.0)
                .sum();

        // Spend saved from Pauses / Scale-downs
        double spendSaved = decisions.stream()
                .filter(d -> "PAUSE".equals(d.getDecisionType()) || "SCALE_DOWN".equals(d.getDecisionType()))
                .filter(d -> d.getBudgetBefore() != null && d.getBudgetAfter() != null)
                .mapToDouble(d -> d.getBudgetBefore() - d.getBudgetAfter())
                .sum();

        long leadsGenerated = leadRepository.findByAccountId(targetWorkspaceId).size();

        double experimentWinRate = generated > 0 ? successRate : 0.0;
        double learningConfidence = generated > 0 ? Math.min(95.0, 50.0 + (executed * 5.0)) : 0.0;
        double govScore = governanceService.evaluateGovernance(targetWorkspaceId, null, "SCALE_UP", 1000.0, 1200.0);

        BrainAnalyticsDto dto = BrainAnalyticsDto.builder()
                .recommendationsGenerated(generated)
                .recommendationsApproved(approved)
                .recommendationsExecuted(executed)
                .successRate(successRate)
                .failureRate(failureRate)
                .revenueImpact(revenueImpact)
                .spendSaved(spendSaved)
                .leadsGenerated(leadsGenerated)
                .experimentWinRate(experimentWinRate)
                .learningConfidence(learningConfidence)
                .governanceScore(govScore)
                .build();

        return ResponseEntity.ok(dto);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrainAnalyticsDto {
        private long recommendationsGenerated;
        private long recommendationsApproved;
        private long recommendationsExecuted;
        private double successRate;
        private double failureRate;
        private double revenueImpact;
        private double spendSaved;
        private long leadsGenerated;
        private double experimentWinRate;
        private double learningConfidence;
        private double governanceScore;
    }
}
