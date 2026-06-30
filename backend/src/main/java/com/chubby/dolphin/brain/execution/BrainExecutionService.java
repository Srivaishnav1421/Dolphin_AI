package com.chubby.dolphin.brain.execution;

import com.chubby.dolphin.brain.*;
import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.BrainDecisionHistory;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.BrainDecisionHistoryRepository;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BrainExecutionService {

    private final BrainDecisionRepository decisionRepo;
    private final CampaignRepository campaignRepo;
    private final BrainDecisionHistoryRepository historyRepo;
    private final BrainOutcomeAnalyzer outcomeAnalyzer;
    private final BrainLearningEngine learningEngine;
    private final BrainMemoryService memoryService;
    private final BrainGovernanceService governanceService;
    private final BrainAutomationPolicyService policyService;
    private final BrainActionAuditService actionAuditService;
    private final SimpMessagingTemplate wsTemplate;
    private final ObjectMapper mapper;

    @Autowired
    public BrainExecutionService(
            BrainDecisionRepository decisionRepo,
            CampaignRepository campaignRepo,
            BrainDecisionHistoryRepository historyRepo,
            BrainOutcomeAnalyzer outcomeAnalyzer,
            BrainLearningEngine learningEngine,
            BrainMemoryService memoryService,
            BrainGovernanceService governanceService,
            BrainAutomationPolicyService policyService,
            BrainActionAuditService actionAuditService,
            @Autowired(required = false) SimpMessagingTemplate wsTemplate,
            ObjectMapper mapper) {
        this.decisionRepo = decisionRepo;
        this.campaignRepo = campaignRepo;
        this.historyRepo = historyRepo;
        this.outcomeAnalyzer = outcomeAnalyzer;
        this.learningEngine = learningEngine;
        this.memoryService = memoryService;
        this.governanceService = governanceService;
        this.policyService = policyService;
        this.actionAuditService = actionAuditService;
        this.wsTemplate = wsTemplate;
        this.mapper = mapper;
    }

    public boolean validateExecution(BrainDecision decision) {
        if (decision == null) return false;

        // Validation Checks:
        // 1. Workspace is active
        if (decision.getAccountId() == null || decision.getAccountId().isBlank()) {
            log.warn("❌ Pre-flight validation failed: Blank workspace account ID.");
            return false;
        }

        // 2. Decision not already executed
        if ("EXECUTED".equals(decision.getStatus()) || "EXECUTED_LOCAL_ONLY".equals(decision.getStatus())) {
            log.warn("❌ Pre-flight validation failed: Decision {} already executed.", decision.getId());
            return false;
        }

        // 3. Campaign exists
        if (decision.getCampaignId() != null) {
            Campaign c = campaignRepo.findById(decision.getCampaignId()).orElse(null);
            if (c == null) {
                log.warn("❌ Pre-flight validation failed: Target campaign {} not found.", decision.getCampaignId());
                return false;
            }
        }

        log.info("✓ Pre-flight Safety checks passed for decision: {}", decision.getId());
        return true;
    }

    @Transactional
    public ExecutionResult executeRecommendation(String decisionId) {
        log.info("⚡ Initiating execution process for decision: {}", decisionId);
        BrainDecision decision = decisionRepo.findById(decisionId)
                .orElseThrow(() -> new RuntimeException("Decision not found: " + decisionId));

        actionAuditService.logAiAction(
                decision.getAccountId(),
                "CONTROLLED_AUTONOMOUS",
                "EXECUTE_RECOMMENDATION",
                "BrainDecision",
                decisionId,
                decision.getReason(),
                decision.getThresholdBreached() != null ? decision.getThresholdBreached() : "APPROVED_DECISION",
                "STARTED"
        );

        broadcastWs(decision.getAccountId(), decisionId, "EXECUTION_STARTED", "🚀 Execution started for recommendation: " + decision.getDecisionType());

        if (!validateExecution(decision)) {
            decision.setStatus("FAILED");
            decisionRepo.save(decision);
            actionAuditService.logAiAction(decision.getAccountId(), "CONTROLLED_AUTONOMOUS", "EXECUTE_RECOMMENDATION",
                    "BrainDecision", decisionId, "Pre-flight safety checks failed", "SAFETY_VALIDATION", "FAILED");
            broadcastWs(decision.getAccountId(), decisionId, "EXECUTION_FAILED", "❌ Validation check failed for recommendation: " + decision.getDecisionType());
            return ExecutionResult.builder()
                    .decisionId(decisionId)
                    .status(ExecutionStatus.FAILED)
                    .errorDetails("Pre-flight safety checks failed")
                    .build();
        }

        try {
            decision.setStatus("EXECUTING");
            decisionRepo.save(decision);

            Campaign campaign = null;
            if (decision.getCampaignId() != null) {
                campaign = campaignRepo.findById(decision.getCampaignId()).orElse(null);
            }

            // Capture snapshot state
            if (campaign != null) {
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("status", campaign.getStatus());
                snapshot.put("budget", campaign.getBudget());
                decision.setCampaignSnapshotJson(mapper.writeValueAsString(snapshot));
            }

            // Apply Mutations locally
            if (campaign != null) {
                switch (decision.getDecisionType()) {
                    case "PAUSE" -> campaign.setStatus("PAUSED");
                    case "RESUME" -> campaign.setStatus("ACTIVE");
                    case "SCALE_UP", "SCALE_DOWN" -> {
                        if (decision.getBudgetAfter() != null) {
                            campaign.setBudget(decision.getBudgetAfter());
                        }
                    }
                }
                campaign.setUpdatedAt(LocalDateTime.now());
                campaignRepo.save(campaign);
            }

            decision.setExecutedAt(LocalDateTime.now());
            decision.setStatus("EXECUTED_LOCAL_ONLY");
            decisionRepo.save(decision);

            broadcastWs(decision.getAccountId(), decisionId, "EXECUTION_COMPLETED", "Execution recorded. Outcome learning will update after real campaign telemetry syncs.");

            actionAuditService.logAiAction(decision.getAccountId(), "CONTROLLED_AUTONOMOUS", decision.getDecisionType(),
                    "Campaign", decision.getCampaignId(), decision.getReason(),
                    decision.getThresholdBreached() != null ? decision.getThresholdBreached() : "APPROVED_DECISION", "SUCCESS");

            return ExecutionResult.builder()
                    .id(decision.getId())
                    .decisionId(decisionId)
                    .status(ExecutionStatus.EXECUTED)
                    .executedAt(LocalDateTime.now())
                    .campaignId(decision.getCampaignId())
                    .decisionType(decision.getDecisionType())
                    .budgetBefore(decision.getBudgetBefore())
                    .budgetAfter(decision.getBudgetAfter())
                    .build();

        } catch (Exception e) {
            log.error("Execution failed for decision: {}", decisionId, e);
            decision.setStatus("FAILED");
            decisionRepo.save(decision);
            actionAuditService.logAiAction(decision.getAccountId(), "CONTROLLED_AUTONOMOUS", "EXECUTE_RECOMMENDATION",
                    "BrainDecision", decisionId, e.getMessage(), "EXECUTION_EXCEPTION", "FAILED");
            broadcastWs(decision.getAccountId(), decisionId, "EXECUTION_FAILED", "❌ Execution failed: " + e.getMessage());
            return ExecutionResult.builder()
                    .decisionId(decisionId)
                    .status(ExecutionStatus.FAILED)
                    .errorDetails(e.getMessage())
                    .build();
        }
    }

    @Transactional
    public boolean rollbackExecution(BrainDecision decision) {
        if (decision == null || decision.getCampaignSnapshotJson() == null || decision.getCampaignSnapshotJson().isBlank()) {
            log.warn("Rollback aborted: Blank or empty snapshot data.");
            return false;
        }

        broadcastWs(decision.getAccountId(), decision.getId(), "EXECUTION_STARTED", "🔄 Initiating rollback restoration...");

        try {
            Campaign campaign = campaignRepo.findById(decision.getCampaignId())
                    .orElseThrow(() -> new RuntimeException("Target campaign not found: " + decision.getCampaignId()));

            Map<String, Object> snapshot = mapper.readValue(decision.getCampaignSnapshotJson(), Map.class);
            String originalStatus = snapshot.get("status").toString();
            Double originalBudget = Double.parseDouble(snapshot.get("budget").toString());

            campaign.setStatus(originalStatus);
            campaign.setBudget(originalBudget);
            campaign.setUpdatedAt(LocalDateTime.now());
            campaignRepo.save(campaign);

            decision.setStatus("ROLLED_BACK");
            decisionRepo.save(decision);

            actionAuditService.logAiAction(decision.getAccountId(), "CONTROLLED_AUTONOMOUS", "ROLLBACK_EXECUTION",
                    "BrainDecision", decision.getId(), "Restored previous campaign state from saved snapshot.",
                    "MANUAL_ROLLBACK", "SUCCESS");

            broadcastWs(decision.getAccountId(), decision.getId(), "EXECUTION_ROLLED_BACK", "🔄 Rollback successfully completed.");
            return true;
        } catch (Exception e) {
            log.error("Failed to restore and rollback decision snapshot: {}", decision.getId(), e);
            actionAuditService.logAiAction(decision.getAccountId(), "CONTROLLED_AUTONOMOUS", "ROLLBACK_EXECUTION",
                    "BrainDecision", decision.getId(), e.getMessage(), "MANUAL_ROLLBACK", "FAILED");
            return false;
        }
    }

    private void broadcastWs(String accountId, String decisionId, String eventType, String message) {
        try {
            if (wsTemplate != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("decisionId", decisionId);
                payload.put("eventType", eventType);
                payload.put("message", message);
                payload.put("timestamp", LocalDateTime.now().toString());

                wsTemplate.convertAndSend("/topic/workspace/" + accountId + "/brain", payload);
                log.info("📡 Broadcast websocket event: {} -> {}", eventType, message);
            }
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed: {}", e.getMessage());
        }
    }
}
