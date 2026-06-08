package com.chubby.dolphin.brain;

import com.chubby.dolphin.brain.execution.BrainExecutionPublisher;
import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import com.chubby.dolphin.service.BrainDecisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class BrainRecommendationService {

    private final BrainContextBuilder contextBuilder;
    private final BrainScoringEngine scoringEngine;
    private final BrainDecisionEngine decisionEngine;
    private final BrainRiskEvaluator riskEvaluator;
    private final BrainActionPlanner actionPlanner;
    private final BrainDecisionRepository decisionRepo;
    private final BrainDecisionService legacyDecisionService;
    private final BrainAutomationPolicyService policyService;
    private final BrainGovernanceService governanceService;
    private final BrainExecutionPublisher executionPublisher;
    private final ObjectMapper mapper;

    @Autowired
    public BrainRecommendationService(BrainContextBuilder contextBuilder,
                                      BrainScoringEngine scoringEngine,
                                      BrainDecisionEngine decisionEngine,
                                      BrainRiskEvaluator riskEvaluator,
                                      BrainActionPlanner actionPlanner,
                                      BrainDecisionRepository decisionRepo,
                                      BrainDecisionService legacyDecisionService,
                                      BrainAutomationPolicyService policyService,
                                      BrainGovernanceService governanceService,
                                      BrainExecutionPublisher executionPublisher,
                                      ObjectMapper mapper) {
        this.contextBuilder = contextBuilder;
        this.scoringEngine = scoringEngine;
        this.decisionEngine = decisionEngine;
        this.riskEvaluator = riskEvaluator;
        this.actionPlanner = actionPlanner;
        this.decisionRepo = decisionRepo;
        this.legacyDecisionService = legacyDecisionService;
        this.policyService = policyService;
        this.governanceService = governanceService;
        this.executionPublisher = executionPublisher;
        this.mapper = mapper;
    }

    public List<BrainDecision> getRecentRecommendations(String workspaceId) {
        return decisionRepo.findByAccountIdOrderByCreatedAtDesc(workspaceId);
    }

    public Optional<BrainDecision> getRecommendationById(String id) {
        return decisionRepo.findById(id);
    }

    @Transactional
    public List<BrainDecision> evaluateAndSave(String workspaceId) {
        log.info("🧠 Initializing Autonomous Brain evaluation cycle for workspace: {}", workspaceId);

        // 1. Build BrainContext
        BrainContext context = contextBuilder.build(workspaceId);

        // 2. Generate Recommendations
        List<BrainDecision> recommendations = decisionEngine.generateDecisions(context);
        List<BrainDecision> savedList = new ArrayList<>();

        // 3. For each recommendation, perform scoring, risk check, action plan and persist
        for (BrainDecision decision : recommendations) {
            decision.setAccountId(workspaceId);
            decision.setCreatedAt(LocalDateTime.now());

            // Calculate confidence
            double overallConfidence = scoringEngine.calculateConfidence(context);
            double recommendationConfidence = decision.getConfidenceScore() != null && decision.getConfidenceScore() > 0
                    ? (decision.getConfidenceScore() * 0.7 + overallConfidence * 0.3)
                    : overallConfidence;
            decision.setConfidenceScore(recommendationConfidence);
            decision.setConfidence(recommendationConfidence / 100.0);

            // Calculate risk
            double risk = riskEvaluator.calculateRisk(context, decision);
            decision.setRiskScore(risk / 100.0);

            // Build action plan and serialize to TEXT json
            List<String> plan = actionPlanner.buildPlan(decision);
            try {
                String planJson = mapper.writeValueAsString(plan);
                decision.setCampaignSnapshotJson(planJson);
            } catch (Exception e) {
                log.warn("Failed to serialize action plan to JSON: {}", e.getMessage());
                decision.setCampaignSnapshotJson("[]");
            }

            // Safety Governance Scoring
            double govScore = governanceService.evaluateGovernance(
                    workspaceId, 
                    decision.getCampaignId(), 
                    decision.getDecisionType(), 
                    decision.getBudgetBefore(), 
                    decision.getBudgetAfter()
            );

            // Evaluate Automation Policy
            BrainAutomationPolicyService.AutomationDecision policyResult = policyService.evaluatePolicy(
                    risk, 
                    recommendationConfidence, 
                    15.0, // Expected ROI Baseline
                    govScore, 
                    context.getWallet() != null && context.getWallet().getBalance() < 1000.0, 
                    false, // Assuming active connection present 
                    false  // Assuming within limits
            );

            // Routing
            if (policyResult == BrainAutomationPolicyService.AutomationDecision.BLOCK) {
                decision.setStatus("BLOCKED_BY_SAFETY");
                decision.setReason(decision.getReason() + " [Safety Blocked]");
            } else if (policyResult == BrainAutomationPolicyService.AutomationDecision.AUTO_APPROVE) {
                decision.setStatus("APPROVED"); // Enqueued for immediate auto-execution!
            } else {
                decision.setStatus("PENDING_APPROVAL");
            }

            // Save to existing table
            BrainDecision saved = decisionRepo.save(decision);
            savedList.add(saved);

            // If auto-approved, publish directly to RabbitMQ queue!
            if (policyResult == BrainAutomationPolicyService.AutomationDecision.AUTO_APPROVE) {
                log.info("⚡ Auto-Approve rule triggered! Enqueuing decision ID: {}", saved.getId());
                executionPublisher.publishExecution(saved.getId());
            }
        }

        log.info("🧠 Brain evaluation completed. Generated {} recommendations.", savedList.size());
        return savedList;
    }

    @Transactional
    public BrainDecision approve(String id, String email) {
        log.info("🧠 Admin approved decision recommendation: {} by: {}", id, email);
        BrainDecision approved = legacyDecisionService.approveDecision(id, email);
        // Dispatch to RabbitMQ queue for execution
        executionPublisher.publishExecution(approved.getId());
        return approved;
    }

    @Transactional
    public BrainDecision reject(String id, String email) {
        log.info("🧠 Admin rejected decision recommendation: {} by: {}", id, email);
        return legacyDecisionService.rejectDecision(id, email);
    }
}
