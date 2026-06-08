package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.BrainEvent;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.brain.BrainActionAuditService;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.WalletRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.BrainDecisionService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.chubby.dolphin.service.SafetyRulesEngine;
import com.chubby.dolphin.service.CompetitorScraperService;
import com.chubby.dolphin.entity.CompetitorInsight;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Brain Controller — Upgraded to use BrainDecisionService, LlmRouter,
 * and SafetyRulesEngine for enterprise-grade autonomous ad management.
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class BrainController {

    private final CampaignRepository   campaignRepo;
    private final BrainEventRepository brainEventRepo;
    private final WalletRepository     walletRepo;
    private final BusinessLlmFacadeService     llmRouter;
    private final BrainDecisionService brainDecisionService;
    private final SafetyRulesEngine    safetyEngine;
    private final SecurityUtils        sec;
    private final ObjectMapper         mapper;
    private final CompetitorScraperService competitorScraper;
    private final com.chubby.dolphin.service.AdvantageExperimentService experimentService;
    private final BrainActionAuditService actionAuditService;

    public BrainController(CampaignRepository campaignRepo,
                           BrainEventRepository brainEventRepo,
                           WalletRepository walletRepo,
                           BusinessLlmFacadeService llmRouter,
                           BrainDecisionService brainDecisionService,
                           SafetyRulesEngine safetyEngine,
                           SecurityUtils sec,
                           ObjectMapper mapper,
                           CompetitorScraperService competitorScraper,
                           com.chubby.dolphin.service.AdvantageExperimentService experimentService,
                           BrainActionAuditService actionAuditService) {
        this.campaignRepo = campaignRepo;
        this.brainEventRepo = brainEventRepo;
        this.walletRepo = walletRepo;
        this.llmRouter = llmRouter;
        this.brainDecisionService = brainDecisionService;
        this.safetyEngine = safetyEngine;
        this.sec = sec;
        this.mapper = mapper;
        this.competitorScraper = competitorScraper;
        this.experimentService = experimentService;
        this.actionAuditService = actionAuditService;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Brain Events
    // ══════════════════════════════════════════════════════════════════

    /** Recent brain events — authenticated user's events only */
    @GetMapping("/brain/events/recent")
    public ResponseEntity<?> recentEvents() {
        return ResponseEntity.ok(
            brainEventRepo.findTop50ByAccountIdOrderByCreatedAtDesc(sec.currentAccountId())
        );
    }

    @GetMapping("/brain/events")
    public ResponseEntity<?> allEvents() {
        return ResponseEntity.ok(
            brainEventRepo.findByAccountIdOrderByCreatedAtDesc(sec.currentAccountId())
        );
    }

    /** Business-facing AI action trail for the current workspace. No model/provider/token details are returned. */
    @GetMapping("/brain/actions")
    public ResponseEntity<?> recentAiActions() {
        return ResponseEntity.ok(
                actionAuditService.recentAiActions(sec.currentAccountId()).stream()
                        .map(a -> Map.of(
                                "id", a.getId(),
                                "action", a.getAction(),
                                "entity_type", a.getEntityType() != null ? a.getEntityType() : (a.getResourceType() != null ? a.getResourceType() : "BusinessRecord"),
                                "entity_id", a.getEntityId() != null ? a.getEntityId() : (a.getResourceId() != null ? a.getResourceId() : ""),
                                "details", a.getDetails() != null ? a.getDetails() : "",
                                "status", a.getEventType() != null ? a.getEventType().replace("AI_ACTION_", "") : "LOGGED",
                                "created_at", a.getTimestamp()
                        ))
                        .collect(Collectors.toList())
        );
    }

    // ══════════════════════════════════════════════════════════════════
    //  Campaign Evaluation (Now with Decision Engine)
    // ══════════════════════════════════════════════════════════════════

    /** Evaluate a campaign with AI Brain — creates a BrainDecision record */
    @PostMapping("/campaigns/{id}/evaluate")
    public ResponseEntity<?> evaluate(@PathVariable String id) {
        Optional<Campaign> opt = campaignRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Campaign campaign = opt.get();
        BrainDecision decision = brainDecisionService.evaluateCampaign(campaign);

        return ResponseEntity.ok(Map.of(
            "decision_id", decision.getId(),
            "action", decision.getDecisionType(),
            "confidence", decision.getConfidence(),
            "status", decision.getStatus(),
            "reason", decision.getReason() != null ? decision.getReason() : "",
            "llm_provider", decision.getLlmProvider() != null ? decision.getLlmProvider() : "",
            "budget_before", decision.getBudgetBefore() != null ? decision.getBudgetBefore() : 0,
            "budget_after", decision.getBudgetAfter() != null ? decision.getBudgetAfter() : 0
        ));
    }

    // ══════════════════════════════════════════════════════════════════
    //  Brain Decisions (Approval Workflow)
    // ══════════════════════════════════════════════════════════════════

    /** Get recent brain decisions */
    @GetMapping("/brain/decisions")
    public ResponseEntity<?> decisions() {
        return ResponseEntity.ok(
            brainDecisionService.getRecentDecisions(sec.currentAccountId())
        );
    }

    /** Get pending approvals */
    @GetMapping("/brain/decisions/pending")
    public ResponseEntity<?> pendingApprovals() {
        String accountId = sec.currentAccountId();
        return ResponseEntity.ok(Map.of(
            "pending", brainDecisionService.getPendingApprovals(accountId),
            "count", brainDecisionService.getPendingApprovalCount(accountId)
        ));
    }

    /** Approve a pending decision — OWNER or ADMIN only */
    @PostMapping("/brain/decisions/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> approveDecision(@PathVariable String id) {
        try {
            BrainDecision decision = brainDecisionService.approveDecision(id, sec.currentEmail());
            return ResponseEntity.ok(decision);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Reject a pending decision — OWNER or ADMIN only */
    @PostMapping("/brain/decisions/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> rejectDecision(@PathVariable String id) {
        try {
            BrainDecision decision = brainDecisionService.rejectDecision(id, sec.currentEmail());
            return ResponseEntity.ok(decision);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Budget Arbitrage
    // ══════════════════════════════════════════════════════════════════

    /** Budget arbitrage with AI — uses authenticated account's campaigns */
    @PostMapping("/brain/arbitrage/{accountId}")
    public ResponseEntity<?> runArbitrage(@PathVariable String accountId) {
        String activeWorkspaceId = sec.currentWorkspaceId();
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String targetAccountId = (isAdmin && accountId != null && !accountId.isBlank()) ? accountId : activeWorkspaceId;

        List<Campaign> campaigns = campaignRepo.findByAccountId(targetAccountId);
        if (campaigns.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "recommendation", "No campaigns found. Add campaigns to run arbitrage.",
                "expected_roas_lift", 0.0,
                "actions", List.of(),
                "reasoning", "No data available."
            ));
        }

        double totalBudget = campaigns.stream().mapToDouble(c -> safe(c.getBudget())).sum();
        List<String> summaries = campaigns.stream()
            .map(c -> String.format("- %s [%s]: ROAS %.2fx, CTR %.2f%%, CPL ₹%.0f, Spent ₹%.0f/₹%.0f",
                c.getName(), c.getStatus(), safe(c.getRoas()), safe(c.getCtr()),
                safe(c.getCpl()), safe(c.getSpent()), safe(c.getBudget())))
            .collect(Collectors.toList());

        BusinessLlmFacadeService.LlmResponse aiResponse = llmRouter.runArbitrage(summaries, totalBudget);
        log.info("⚡ Arbitrage for {} [via {}]: {}", targetAccountId, aiResponse.provider(), aiResponse.text());

        saveEvent(targetAccountId, "ARBITRAGE_RUN",
            "⚡ Arbitrage [" + aiResponse.provider() + "]: " +
            extractField(aiResponse.text(), "recommendation"), "SUCCESS");

        try {
            Map<String, Object> parsed = mapper.readValue(aiResponse.text(), Map.class);
            parsed.put("llm_provider", aiResponse.provider());
            return ResponseEntity.ok(parsed);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "recommendation", aiResponse.text(),
                "expected_roas_lift", 0.0,
                "actions", List.of(),
                "reasoning", "Raw AI response",
                "llm_provider", aiResponse.provider()
            ));
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  EMAS Analytics
    // ══════════════════════════════════════════════════════════════════

    /** EMAS analytics — computed from real campaign data */
    @GetMapping("/emas/{accountId}")
    public ResponseEntity<?> emas(@PathVariable String accountId) {
        String activeWorkspaceId = sec.currentWorkspaceId();
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String targetAccountId = (isAdmin && accountId != null && !accountId.isBlank()) ? accountId : activeWorkspaceId;

        List<Campaign> campaigns = campaignRepo.findByAccountId(targetAccountId);
        double totalSpend   = campaigns.stream().mapToDouble(c -> safe(c.getSpent())).sum();
        double totalRevenue = campaigns.stream()
                .mapToDouble(c -> safe(c.getSpent()) * safe(c.getRoas())).sum();
        double blendedRoas  = totalSpend > 0 ? totalRevenue / totalSpend : 0;
        int    customers    = (int)(totalRevenue / 8500); // avg ticket size ₹8500
        double cac          = customers > 0 ? totalSpend / customers : 0;

        return ResponseEntity.ok(Map.of(
            "blended_roas",    Math.round(blendedRoas * 100.0) / 100.0,
            "cac",             Math.round(cac),
            "mer",             Math.round(blendedRoas * 100.0) / 100.0,
            "total_spend",     totalSpend,
            "total_revenue",   totalRevenue,
            "total_customers", customers
        ));
    }

    // ══════════════════════════════════════════════════════════════════
    //  LLM Provider Status
    // ══════════════════════════════════════════════════════════════════

    /** Get LLM provider health status */
    @GetMapping("/brain/llm-status")
    public ResponseEntity<?> llmStatus() {
        return ResponseEntity.ok(llmRouter.getProviderStatus());
    }

    // ══════════════════════════════════════════════════════════════════
    //  Competitor Intelligence
    // ══════════════════════════════════════════════════════════════════

    /** Crawl and analyze a competitor's website for digital marketing hooks */
    @PostMapping("/brain/competitor/analyze")
    public ResponseEntity<?> analyzeCompetitor(@RequestBody Map<String, String> body) {
        String url = body.get("competitor_url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "competitor_url is required"));
        }
        String accountId = sec.currentAccountId();
        CompetitorInsight insight = competitorScraper.analyzeCompetitor(url, accountId);
        
        saveEvent(accountId, "COMPETITOR_SCRAPED", 
            "🌐 Extracted intelligence for competitor: " + url, "INFO");
            
        return ResponseEntity.ok(insight);
    }

    /** List all stored competitor insights for the current account */
    @GetMapping("/brain/competitor/insights")
    public ResponseEntity<?> getCompetitorInsights() {
        return ResponseEntity.ok(competitorScraper.getInsightsForAccount(sec.currentAccountId()));
    }

    // ══════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════

    private void saveEvent(String accountId, String type, String message, String severity) {
        BrainEvent e = new BrainEvent();
        e.setAccountId(accountId);
        e.setEventType(type);
        e.setMessage(message);
        e.setSeverity(severity);
        e.setCreatedAt(LocalDateTime.now());
        brainEventRepo.save(e);
    }

    private double safe(Double v) { return v != null ? v : 0.0; }

    private String extractField(String json, String field) {
        try {
            Map<?, ?> m = mapper.readValue(json, Map.class);
            Object val = m.get(field);
            return val != null ? val.toString() : json.substring(0, Math.min(json.length(), 100));
        } catch (Exception e) {
            return json.substring(0, Math.min(json.length(), 100));
        }
    }

    /** Rollback a decision's changes */
    @PostMapping("/brain/rollback/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> rollbackDecision(@PathVariable String id) {
        try {
            BrainDecision result = brainDecisionService.rollbackDecision(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Create an approval-required Advantage+ Experiment with 27 permutations */
    @PostMapping("/brain/experiments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> createAbExperiment(@RequestBody Map<String, Object> body) {
        try {
            String workspaceId = sec.currentAccountId();
            String campaignId = (String) body.get("campaignId");
            List<String> headlines = (List<String>) body.get("headlines");
            List<String> bodies = (List<String>) body.get("bodies");
            List<String> ctas = (List<String>) body.get("ctas");

            if (campaignId == null || headlines == null || bodies == null || ctas == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "campaignId, headlines, bodies, and ctas are required."));
            }

            com.chubby.dolphin.entity.AdvantageExperiment exp = experimentService.createAbExperiment(
                    workspaceId, campaignId, headlines, bodies, ctas
            );
            return ResponseEntity.ok(exp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
