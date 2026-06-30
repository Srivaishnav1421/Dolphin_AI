package com.chubby.dolphin.controller;

import com.chubby.dolphin.approval.ApprovalItemRepository;
import com.chubby.dolphin.approval.ApprovalStatus;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.*;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.chubby.dolphin.service.MetaAdsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard Controller — Upgraded with Meta connection status,
 * brain decision stats, and LLM provider health.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CampaignRepository   campaignRepo;
    private final LeadRepository       leadRepo;
    private final BrainEventRepository brainEventRepo;
    private final WalletRepository     walletRepo;
    private final WorkflowExecutionRepository workflowExecutionRepo;
    private final ApprovalItemRepository approvalItemRepository;
    private final SecurityUtils        sec;
    private final MetaAdsService       metaAdsService;
    private final BusinessLlmFacadeService     llmRouter;
    private final AccessControlService access;

    public DashboardController(CampaignRepository campaignRepo,
                               LeadRepository leadRepo,
                               BrainEventRepository brainEventRepo,
                               WalletRepository walletRepo,
                               WorkflowExecutionRepository workflowExecutionRepo,
                               ApprovalItemRepository approvalItemRepository,
                               SecurityUtils sec,
                               MetaAdsService metaAdsService,
                               BusinessLlmFacadeService llmRouter,
                               AccessControlService access) {
        this.campaignRepo = campaignRepo;
        this.leadRepo = leadRepo;
        this.brainEventRepo = brainEventRepo;
        this.walletRepo = walletRepo;
        this.workflowExecutionRepo = workflowExecutionRepo;
        this.approvalItemRepository = approvalItemRepository;
        this.sec = sec;
        this.metaAdsService = metaAdsService;
        this.llmRouter = llmRouter;
        this.access = access;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        access.requireWorkspacePermission(Permission.ANALYTICS_READ);
        String accountId = sec.currentWorkspaceId();

        List<Campaign> campaigns = campaignRepo.findByAccountId(accountId);

        double totalSpend   = campaigns.stream()
                .mapToDouble(c -> c.getSpent() != null ? c.getSpent() : 0).sum();
        double totalCampaignBudget = campaigns.stream()
                .mapToDouble(c -> c.getBudget() != null ? c.getBudget() : 0).sum();
        double totalRevenue = campaigns.stream()
                .mapToDouble(c -> (c.getSpent() != null && c.getRoas() != null)
                        ? c.getSpent() * c.getRoas() : 0).sum();
        double blendedRoas  = totalSpend > 0 ? totalRevenue / totalSpend : 0;
        long activeCampaigns = campaigns.stream()
                .filter(c -> "ACTIVE".equals(c.getStatus())).count();

        long hotLeads  = leadRepo.findByAccountIdAndStatus(accountId, "HOT").size();
        long warmLeads = leadRepo.findByAccountIdAndStatus(accountId, "WARM").size();
        long coldLeads = leadRepo.findByAccountIdAndStatus(accountId, "COLD").size();

        double walletBalance = walletRepo.findFirstByAccountId(accountId)
                .map(w -> w.getBalance()).orElse(0.0);

        // Meta connection status
        boolean metaConnected = metaAdsService.getActiveConnection(accountId).isPresent();

        long pendingApprovals = approvalItemRepository.countByWorkspaceIdAndStatus(UUID.fromString(accountId), ApprovalStatus.PENDING);

        long activeAutomations = workflowExecutionRepo.countByWorkspaceIdAndStatus(accountId, "RUNNING")
                + workflowExecutionRepo.countByWorkspaceIdAndStatus(accountId, "WAITING_FOR_APPROVAL");
        long completedAutomations = workflowExecutionRepo.countByWorkspaceIdAndStatus(accountId, "COMPLETED");
        long failedAutomations = workflowExecutionRepo.countByWorkspaceIdAndStatus(accountId, "FAILED");
        Double avgAutomationMs = workflowExecutionRepo.getAverageDurationByWorkspaceId(accountId);

        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("total_spend", totalSpend),
            Map.entry("total_campaign_budget", totalCampaignBudget),
            Map.entry("total_revenue", totalRevenue),
            Map.entry("blended_roas", Math.round(blendedRoas * 100.0) / 100.0),
            Map.entry("active_campaigns", activeCampaigns),
            Map.entry("total_campaigns", (long) campaigns.size()),
            Map.entry("hot_leads", hotLeads),
            Map.entry("warm_leads", warmLeads),
            Map.entry("cold_leads", coldLeads),
            Map.entry("wallet_balance", walletBalance),
            Map.entry("meta_connected", metaConnected),
            Map.entry("pending_approvals", pendingApprovals),
            Map.entry("automation", Map.of(
                    "active", activeAutomations,
                    "completed", completedAutomations,
                    "failed", failedAutomations,
                    "average_duration_ms", avgAutomationMs != null ? avgAutomationMs.longValue() : 0L
            )),
            Map.entry("llm_status", llmRouter.getProviderStatus()),
            Map.entry("recent_events", brainEventRepo.findTop50ByAccountIdOrderByCreatedAtDesc(accountId)
                                          .stream().limit(6).toList())
        ));
    }
}
