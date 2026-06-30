package com.chubby.dolphin.service;

import com.chubby.dolphin.config.RabbitConfig;
import com.chubby.dolphin.dto.BrainDecisionMessage;
import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class BrainDecisionService {

    private final CampaignRepository campaignRepo;
    private final BrainDecisionRepository decisionRepo;
    private final BrainEventRepository eventRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final BusinessLlmFacadeService llmRouter;
    private final SafetyRulesEngine safetyEngine;
    private final MetaAdsService metaAdsService;
    private final AlertService alertService;
    private final ObjectMapper mapper;
    private final BrainFeedbackService brainFeedbackService;
    private final SimpMessagingTemplate wsTemplate;
    private final CreativeFatigueService creativeFatigueService;
    private final WorkspaceConfigRepository workspaceConfigRepo;
    private final BrainDecisionHistoryRepository historyRepo;
    private final LocalApprovalSafetyService localApprovalSafetyService;

    // Optional dependencies for resilience during tests
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;

    // Counters
    private Counter decisionsCounter;
    private Counter pausedCounter;
    private Counter experimentsCounter;

    @Value("${brain.auto-execute.min-confidence:0.85}")
    private double autoExecuteThreshold;

    @Value("${brain.approval-required.min-confidence:0.50}")
    private double approvalThreshold;

    private BrainDecisionService self;

    @Autowired
    public void setSelf(@Lazy BrainDecisionService self) {
        this.self = self;
    }

    public BrainDecisionService(CampaignRepository campaignRepo,
                                 BrainDecisionRepository decisionRepo,
                                 BrainEventRepository eventRepo,
                                 MetaConnectionRepository metaConnRepo,
                                 BusinessLlmFacadeService llmRouter,
                                 SafetyRulesEngine safetyEngine,
                                 MetaAdsService metaAdsService,
                                 AlertService alertService,
                                 ObjectMapper mapper,
                                 BrainFeedbackService brainFeedbackService,
                                 SimpMessagingTemplate wsTemplate,
                                 CreativeFatigueService creativeFatigueService,
                                 WorkspaceConfigRepository workspaceConfigRepo,
                                 BrainDecisionHistoryRepository historyRepo,
                                 @Autowired(required = false) RabbitTemplate rabbitTemplate,
                                 @Autowired(required = false) MeterRegistry meterRegistry) {
        this(campaignRepo, decisionRepo, eventRepo, metaConnRepo, llmRouter, safetyEngine,
                metaAdsService, alertService, mapper, brainFeedbackService, wsTemplate,
                creativeFatigueService, workspaceConfigRepo, historyRepo, rabbitTemplate, meterRegistry, null);
    }

    @Autowired
    public BrainDecisionService(CampaignRepository campaignRepo,
                                 BrainDecisionRepository decisionRepo,
                                 BrainEventRepository eventRepo,
                                 MetaConnectionRepository metaConnRepo,
                                 BusinessLlmFacadeService llmRouter,
                                 SafetyRulesEngine safetyEngine,
                                 MetaAdsService metaAdsService,
                                 AlertService alertService,
                                 ObjectMapper mapper,
                                 BrainFeedbackService brainFeedbackService,
                                 SimpMessagingTemplate wsTemplate,
                                 CreativeFatigueService creativeFatigueService,
                                 WorkspaceConfigRepository workspaceConfigRepo,
                                 BrainDecisionHistoryRepository historyRepo,
                                 @Autowired(required = false) RabbitTemplate rabbitTemplate,
                                 @Autowired(required = false) MeterRegistry meterRegistry,
                                 @Autowired(required = false) LocalApprovalSafetyService localApprovalSafetyService) {
        this.campaignRepo = campaignRepo;
        this.decisionRepo = decisionRepo;
        this.eventRepo = eventRepo;
        this.metaConnRepo = metaConnRepo;
        this.llmRouter = llmRouter;
        this.safetyEngine = safetyEngine;
        this.metaAdsService = metaAdsService;
        this.alertService = alertService;
        this.mapper = mapper;
        this.brainFeedbackService = brainFeedbackService;
        this.wsTemplate = wsTemplate;
        this.creativeFatigueService = creativeFatigueService;
        this.workspaceConfigRepo = workspaceConfigRepo;
        this.historyRepo = historyRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistry = meterRegistry;
        this.localApprovalSafetyService = localApprovalSafetyService;

        // Initialize micrometer metrics with fallbacks
        if (meterRegistry != null) {
            this.decisionsCounter = meterRegistry.counter("chubby_dolphin_brain_decisions_total");
            this.pausedCounter = meterRegistry.counter("chubby_dolphin_brain_campaigns_paused_total");
            this.experimentsCounter = meterRegistry.counter("chubby_dolphin_brain_experiments_total");
        }
    }

    // Increments metrics safely
    private void incrementDecisionCount() {
        if (decisionsCounter != null) {
            decisionsCounter.increment();
        }
    }

    public void incrementPausedCount() {
        if (pausedCounter != null) {
            pausedCounter.increment();
        }
    }

    public void incrementExperimentCount() {
        if (experimentsCounter != null) {
            experimentsCounter.increment();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Campaign Evaluation (Single Campaign)
    // ══════════════════════════════════════════════════════════════════

    public BrainDecision evaluateCampaign(Campaign campaign) {
        String accountId = campaign.getAccountId();
        incrementDecisionCount();

        // 1. Get Workspace thresholds & Auto Pause conditions
        WorkspaceConfig config = workspaceConfigRepo.findByWorkspaceId(accountId).orElse(null);
        double minRoas = config != null && config.getMinRoasThreshold() != null ? config.getMinRoasThreshold() : 2.0;
        double maxSpend = config != null && config.getMaxSpendLimit() != null ? config.getMaxSpendLimit() : 10000.0;
        double targetCpl = config != null && config.getTargetCpl() != null ? config.getTargetCpl() : 500.0;
        boolean autoOptimize = config != null && config.getAutoOptimizationEnabled() != null ? config.getAutoOptimizationEnabled() : false;

        // Calculate metrics
        double currentRoas = safe(campaign.getRoas());
        double currentSpent = safe(campaign.getSpent());
        double currentCpl = safe(campaign.getCpl());
        int days = campaign.getDaysOfData() != null ? campaign.getDaysOfData() : 0;
        int conversions = campaign.getConversions() != null ? campaign.getConversions() : 0;

        double conversionRate = conversions > 0 ? (conversions * 100.0) / (currentSpent / (currentCpl > 0 ? currentCpl : 10.0) + 1.0) : 0.0;
        double spendVelocity = currentSpent / (days > 0 ? days : 1.0);

        campaign.setConversionRate(Math.round(conversionRate * 100.0) / 100.0);
        campaign.setSpendVelocity(Math.round(spendVelocity * 100.0) / 100.0);
        campaignRepo.save(campaign);

        // Pre-create Explainability Trigger Metrics & Threshold breached strings
        String triggerMetricsStr = String.format("ROAS=%.2f, Spent=%.2f, CPL=%.2f, Conversions=%d, Days=%d", 
                currentRoas, currentSpent, currentCpl, conversions, days);
        String breachedThresholdStr = "NONE";
        String action = "CONTINUE";
        double confidence = 0.85;
        double risk = 0.15;
        String reason = "Campaign metrics are currently performing optimally within safety bounds.";

        // Determine if Auto Pause logic triggers
        if (currentRoas < minRoas) {
            action = "PAUSE";
            breachedThresholdStr = String.format("ROAS (%.2f) < Workspace Min Threshold (%.2f)", currentRoas, minRoas);
            reason = "[Auto-Pause] Campaign halted due to ROAS dropping below the minimum workspace safety threshold.";
            confidence = 0.95;
            risk = 0.10;
        } else if (currentSpent > maxSpend) {
            action = "PAUSE";
            breachedThresholdStr = String.format("Spend (%.2f) > Workspace Max Spend Limit (%.2f)", currentSpent, maxSpend);
            reason = "[Auto-Pause] Campaign halted due to total cumulative spend exceeding the workspace budget cap.";
            confidence = 0.98;
            risk = 0.05;
        } else if (currentCpl > targetCpl * 1.30) {
            action = "PAUSE";
            breachedThresholdStr = String.format("CPL (%.2f) > target CPL (%.2f) by 30%%+", currentCpl, targetCpl);
            reason = "[Auto-Pause] Campaign halted due to Cost Per Lead scaling beyond the 30% safety overrun limit.";
            confidence = 0.92;
            risk = 0.20;
        } else {
            // Ask LLM Router for optimization if no hard limits are breached
            try {
                BusinessLlmFacadeService.LlmResponse aiResponse = llmRouter.evaluateCampaign(
                    campaign.getName(),
                    currentRoas,
                    safe(campaign.getCtr()),
                    currentCpl,
                    currentSpent,
                    safe(campaign.getBudget())
                );
                Map<String, Object> parsed = mapper.readValue(aiResponse.text(), Map.class);
                action = parsed.getOrDefault("action", "CONTINUE").toString();
                confidence = toDouble(parsed.get("confidence"), 0.85);
                reason = parsed.getOrDefault("reason", aiResponse.text()).toString();
                risk = toDouble(parsed.get("risk_score"), 0.25);
            } catch (Exception e) {
                log.warn("Failed to parse LLM analysis: {}", e.getMessage());
            }
        }

        // Save pre-modification snapshot
        String snapshotJson = "";
        try {
            Map<String, Object> snapMap = new HashMap<>();
            snapMap.put("status", campaign.getStatus());
            snapMap.put("budget", campaign.getBudget());
            snapMap.put("spent", campaign.getSpent());
            snapMap.put("conversions", campaign.getConversions());
            snapMap.put("daysOfData", campaign.getDaysOfData());
            snapshotJson = mapper.writeValueAsString(snapMap);
        } catch (Exception e) {
            log.warn("Snapshot serialization failed: {}", e.getMessage());
        }

        // Log recommendation before execution
        log.info("🤖 Recommendation Generated: Campaign={} | Action={} | Breached={} | Trigger={} | Confidence={}",
                 campaign.getName(), action, breachedThresholdStr, triggerMetricsStr, confidence);

        BrainDecision decision = new BrainDecision();
        decision.setAccountId(accountId);
        decision.setCampaignId(campaign.getId());
        decision.setCampaignName(campaign.getName());
        decision.setDecisionType(action);
        decision.setAction(action + ": " + reason);
        decision.setConfidence(confidence);
        decision.setConfidenceScore(confidence);
        decision.setRiskScore(risk);
        decision.setReason(reason);
        decision.setTriggerMetrics(triggerMetricsStr);
        decision.setThresholdBreached(breachedThresholdStr);
        decision.setCampaignSnapshotJson(snapshotJson);
        decision.setRoasAtDecision(currentRoas);
        decision.setCtrAtDecision(campaign.getCtr());
        decision.setCplAtDecision(currentCpl);
        decision.setSpentAtDecision(currentSpent);

        // Adjust budget suggestion
        if (campaign.getBudget() != null && ("SCALE_UP".equals(action) || "SCALE_DOWN".equals(action))) {
            double multiplier = "SCALE_UP".equals(action) ? 1.20 : 0.80;
            decision.setBudgetBefore(campaign.getBudget());
            decision.setBudgetAfter(campaign.getBudget() * multiplier);
        }

        // Local approval-first mode: risky campaign actions are never auto-executed.
        decision.setStatus("PENDING_APPROVAL");
        if (autoOptimize && confidence >= autoExecuteThreshold) {
            decision.setReason(decision.getReason() + " [Auto-optimization requested, but local approval-first mode disabled execution.]");
            log.info("Auto-execution suppressed. Decision stored for approval.");
        } else {
            log.info("Approval required before any risky campaign action.");
        }

        BrainDecision saved = decisionRepo.save(decision);
        saveHistory(saved, snapshotJson, triggerMetricsStr, breachedThresholdStr);

        // Trigger real-time ws event
        try {
            BrainEvent evt = saveEvent(accountId, "BRAIN_DECISION",
                    "🧠 " + campaign.getName() + " → " + action + " [" + saved.getStatus() + "]", "INFO");
            wsTemplate.convertAndSend("/topic/workspace/" + accountId + "/brain-events", evt);
        } catch (Exception e) {
            log.debug("WebSocket push failed: {}", e.getMessage());
        }

        return saved;
    }

    private void saveHistory(BrainDecision decision, String snapshot, String triggers, String breached) {
        try {
            BrainDecisionHistory history = new BrainDecisionHistory();
            history.setDecisionId(decision.getId());
            history.setAccountId(decision.getAccountId());
            history.setCampaignId(decision.getCampaignId());
            history.setAction(decision.getDecisionType());
            history.setConfidenceScore(decision.getConfidenceScore());
            history.setRiskScore(decision.getRiskScore());
            history.setMetricsAtDecision(triggers);
            history.setThresholdsAtDecision(breached);
            history.setCampaignSnapshotJson(snapshot);
            history.setStatus(decision.getStatus());
            historyRepo.save(history);
        } catch (Exception e) {
            log.error("Could not write history log: {}", e.getMessage());
        }
    }

    private void queueDecisionMessage(BrainDecision decision) {
        try {
            if (rabbitTemplate != null) {
                log.info("🔌 Dispatching campaign task to RabbitMQ worker...");
                rabbitTemplate.convertAndSend(RabbitConfig.OPTIMIZATION_QUEUE, new BrainDecisionMessage(
                        decision.getId(), decision.getCampaignId(), decision.getDecisionType(), decision.getBudgetAfter()
                ));
            } else {
                log.warn("⚠️ RabbitMQ not connected. Fallback to local synchronous processing.");
                self.executeDecisionAsync(decision.getId());
            }
        } catch (Exception e) {
            log.error("Queue dispatch failure, processing synchronously: {}", e.getMessage());
            self.executeDecisionAsync(decision.getId());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Asynchronous Worker Thread Execution
    // ══════════════════════════════════════════════════════════════════

    @Transactional
    public void executeDecisionAsync(String decisionId) {
        log.info("⚡ Executing Decision in background worker thread: id={}", decisionId);
        BrainDecision decision = decisionRepo.findById(decisionId).orElse(null);
        if (decision == null) return;

        Campaign campaign = campaignRepo.findById(decision.getCampaignId()).orElse(null);
        if (campaign == null) return;

        executeDecision(decision, campaign);
        decisionRepo.save(decision);

        // Update history log status
        List<BrainDecisionHistory> histories = historyRepo.findByDecisionId(decisionId);
        for (BrainDecisionHistory h : histories) {
            h.setStatus(decision.getStatus());
            historyRepo.save(h);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Approve / Reject Pending Decisions
    // ══════════════════════════════════════════════════════════════════

    public BrainDecision approveDecision(String decisionId, String approverEmail) {
        BrainDecision decision = decisionRepo.findById(decisionId)
                .orElseThrow(() -> new RuntimeException("Decision not found: " + decisionId));

        if (!"PENDING_APPROVAL".equals(decision.getStatus())) {
            throw new RuntimeException("Decision is not pending approval: " + decision.getStatus());
        }

        decision.setApprovedBy(approverEmail);
        decision.setApprovedAt(LocalDateTime.now());
        decision.setStatus("APPROVED");

        BrainDecision saved = decisionRepo.save(decision);
        saveEvent(decision.getAccountId(), "DECISION_APPROVED",
                "Decision approved for '" + decision.getCampaignName() + "'. Execution is disabled in local approval-first mode.", "INFO");
        return saved;
    }

    public BrainDecision rejectDecision(String decisionId, String rejectorEmail) {
        BrainDecision decision = decisionRepo.findById(decisionId)
                .orElseThrow(() -> new RuntimeException("Decision not found: " + decisionId));

        decision.setStatus("REJECTED");
        decision.setApprovedBy(rejectorEmail);
        decision.setApprovedAt(LocalDateTime.now());

        BrainDecision saved = decisionRepo.save(decision);
        saveHistory(saved, decision.getCampaignSnapshotJson(), decision.getTriggerMetrics(), decision.getThresholdBreached());

        saveEvent(decision.getAccountId(), "DECISION_REJECTED",
                "❌ Decision rejected for '" + decision.getCampaignName() + "': " +
                decision.getDecisionType() + " by " + rejectorEmail, "INFO");

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Execute Decision (Meta Integrator)
    // ══════════════════════════════════════════════════════════════════

    private void executeDecision(BrainDecision decision, Campaign campaign) {
        String accountId = decision.getAccountId();
        if (localSafetyBlocks("BRAIN_DECISION_EXECUTION")) {
            decision.setStatus("PENDING_APPROVAL");
            decision.setReason(decision.getReason() + " [Blocked by local approval-first safety gate; execution requires approval and external integration is disabled.]");
            localApprovalSafetyService.auditBlockedExecution(
                    accountId,
                    "BRAIN_DECISION_EXECUTION",
                    "BrainDecision",
                    decision.getId(),
                    "Legacy Brain execution blocked before Meta pause/resume/budget mutation."
            );
            saveEvent(accountId, "LOCAL_SAFETY_BLOCKED",
                    "Legacy Brain execution blocked for '" + decision.getCampaignName() + "'. Approval-first local mode is active.",
                    "WARNING");
            return;
        }

        // Safety pre-flight check
        Optional<MetaConnection> connOpt = metaAdsService.getActiveConnection(accountId);
        MetaConnection conn = connOpt.orElse(null);

        SafetyRulesEngine.SafetyResult safety = safetyEngine.fullPreFlightCheck(
            accountId, conn, decision.getDecisionType(), campaign, decision.getBudgetAfter()
        );

        if (!safety.passed()) {
            log.warn("🛑 Safety blocked: {} — {}", safety.code(), safety.reason());
            decision.setStatus("BLOCKED_BY_SAFETY");
            decision.setReason(decision.getReason() + " [BLOCKED: " + safety.reason() + "]");
            saveEvent(accountId, "SAFETY_BLOCK",
                    "🛑 Safety Engine blocked: " + safety.reason(), "WARNING");
            return;
        }

        // Execute on Meta if we have a connection
        boolean executedOnMeta = false;
        if (conn != null && campaign.getMetaCampaignId() != null) {
            executedOnMeta = switch (decision.getDecisionType()) {
                case "PAUSE" -> metaAdsService.pauseCampaign(conn, campaign.getMetaCampaignId());
                case "RESUME" -> metaAdsService.resumeCampaign(conn, campaign.getMetaCampaignId());
                case "SCALE_UP", "SCALE_DOWN" -> {
                    if (decision.getBudgetAfter() != null) {
                        yield metaAdsService.updateBudget(conn, campaign.getMetaCampaignId(),
                                                          decision.getBudgetAfter());
                    }
                    yield false;
                }
                default -> false;
            };
        }

        // Update local DB status
        switch (decision.getDecisionType()) {
            case "PAUSE" -> {
                campaign.setStatus("PAUSED");
                campaign.setUpdatedAt(LocalDateTime.now());
                campaignRepo.save(campaign);
                incrementPausedCount();
            }
            case "RESUME" -> {
                campaign.setStatus("ACTIVE");
                campaign.setUpdatedAt(LocalDateTime.now());
                campaignRepo.save(campaign);
            }
            case "SCALE_UP", "SCALE_DOWN" -> {
                if (decision.getBudgetAfter() != null) {
                    campaign.setBudget(decision.getBudgetAfter());
                    campaign.setUpdatedAt(LocalDateTime.now());
                    campaignRepo.save(campaign);
                }
            }
        }

        decision.setExecutedAt(LocalDateTime.now());
        decision.setStatus(executedOnMeta ? "EXECUTED" : "EXECUTED_LOCAL_ONLY");

        log.info("✅ Decision executed successfully: {} → {} (Meta Sync: {})",
                 campaign.getName(), decision.getDecisionType(), executedOnMeta);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Auditing & 1-Click Rollback Restoration
    // ══════════════════════════════════════════════════════════════════

    public BrainDecision rollbackDecision(String decisionId) {
        log.info("🔄 Rolling back decision: {}", decisionId);
        BrainDecision decision = decisionRepo.findById(decisionId)
                .orElseThrow(() -> new RuntimeException("Decision not found: " + decisionId));

        if (decision.getCampaignSnapshotJson() == null || decision.getCampaignSnapshotJson().isEmpty()) {
            throw new RuntimeException("Rollback failed: No snapshot state data preserved for this decision.");
        }

        Campaign campaign = campaignRepo.findById(decision.getCampaignId())
                .orElseThrow(() -> new RuntimeException("Campaign not found: " + decision.getCampaignId()));

        try {
            Map<String, Object> snapshot = mapper.readValue(decision.getCampaignSnapshotJson(), Map.class);
            String originalStatus = snapshot.get("status").toString();
            Double originalBudget = toDouble(snapshot.get("budget"), null);

            campaign.setStatus(originalStatus);
            if (originalBudget != null) {
                campaign.setBudget(originalBudget);
            }
            campaign.setUpdatedAt(LocalDateTime.now());
            campaignRepo.save(campaign);

            decision.setStatus("ROLLED_BACK");
            BrainDecision saved = decisionRepo.save(decision);

            // Log history update
            List<BrainDecisionHistory> histories = historyRepo.findByDecisionId(decisionId);
            for (BrainDecisionHistory h : histories) {
                h.setStatus("ROLLED_BACK");
                historyRepo.save(h);
            }

            saveEvent(decision.getAccountId(), "ROLLBACK_SUCCESS",
                    "🔄 Successfully rolled back campaign '" + campaign.getName() + "' to budget=" + originalBudget + ", status=" + originalStatus, "SUCCESS");

            return saved;
        } catch (Exception e) {
            log.error("Failed to restore campaign from snapshot JSON: {}", e.getMessage());
            throw new RuntimeException("Rollback restoration failure", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Autonomous Brain Cycle (Scheduled)
    // ══════════════════════════════════════════════════════════════════

    @Scheduled(fixedDelayString = "${brain.cycle.interval-ms:900000}")
    public void autonomousBrainCycle() {
        log.info("🧠 Autonomous Brain Cycle started...");
        if (localSafetyBlocks("BRAIN_AUTONOMOUS_CYCLE")) {
            localApprovalSafetyService.auditBlockedExecution(
                    null,
                    "BRAIN_AUTONOMOUS_CYCLE",
                    "Campaign",
                    null,
                    "Scheduled legacy Brain cycle blocked before campaign evaluation or mutation."
            );
            return;
        }

        List<Campaign> allActive = campaignRepo.findByStatus("ACTIVE");
        int evaluated = 0;
        int actioned = 0;

        for (Campaign campaign : allActive) {
            try {
                if (safe(campaign.getSpent()) <= 0 && safe(campaign.getRoas()) <= 0
                        && safe(campaign.getCtr()) <= 0 && safe(campaign.getBudget()) <= 0) {
                    continue;
                }

                // Emergency wallet sweep: local approval-first mode records a critical warning only.
                if (safetyEngine.isEmergencyPauseNeeded(campaign.getAccountId())) {
                    saveEvent(campaign.getAccountId(), "EMERGENCY_PAUSE",
                            "Emergency pause recommended for '" + campaign.getName() + "' due to low wallet balance. Approval required before action.", "CRITICAL");
                    continue;
                }

                BrainDecision decision = evaluateCampaign(campaign);
                evaluated++;
                if ("EXECUTED".equals(decision.getStatus()) || "AUTO_EXECUTED".equals(decision.getStatus())) {
                    actioned++;
                }

                creativeFatigueService.detectFatigue(campaign.getId());
            } catch (Exception e) {
                log.error("Brain cycle error for campaign '{}': {}", campaign.getName(), e.getMessage());
            }
        }

        log.info("✅ Brain Cycle complete — evaluated: {}, actioned: {}", evaluated, actioned);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Query Methods
    // ══════════════════════════════════════════════════════════════════

    public List<BrainDecision> getRecentDecisions(String accountId) {
        return decisionRepo.findTop50ByAccountIdOrderByCreatedAtDesc(accountId);
    }

    public List<BrainDecision> getPendingApprovals(String accountId) {
        return decisionRepo.findByAccountIdAndStatus(accountId, "PENDING_APPROVAL");
    }

    public long getPendingApprovalCount(String accountId) {
        return decisionRepo.countByAccountIdAndStatus(accountId, "PENDING_APPROVAL");
    }

    private BrainEvent saveEvent(String accountId, String type, String message, String severity) {
        BrainEvent e = new BrainEvent();
        e.setAccountId(accountId);
        e.setEventType(type);
        e.setMessage(message);
        e.setSeverity(severity);
        e.setCreatedAt(LocalDateTime.now());
        return eventRepo.save(e);
    }

    private double safe(Double v) { return v != null ? v : 0.0; }

    private Double toDouble(Object v, Double def) {
        if (v == null) return def;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return def; }
    }

    private boolean localSafetyBlocks(String action) {
        return localApprovalSafetyService != null && localApprovalSafetyService.shouldRequireApprovalOnly(action);
    }
}
