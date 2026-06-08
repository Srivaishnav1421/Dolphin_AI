package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import com.chubby.dolphin.service.BrainDecisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainRecommendationServiceTest {

    @Mock private BrainContextBuilder contextBuilder;
    @Mock private BrainScoringEngine scoringEngine;
    @Mock private BrainDecisionEngine decisionEngine;
    @Mock private BrainRiskEvaluator riskEvaluator;
    @Mock private BrainActionPlanner actionPlanner;
    @Mock private BrainDecisionRepository decisionRepo;
    @Mock private BrainDecisionService legacyDecisionService;
    private final ObjectMapper mapper = new ObjectMapper();

    private BrainRecommendationService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        BrainAutomationPolicyService policyService = mock(BrainAutomationPolicyService.class);
        when(policyService.evaluatePolicy(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(BrainAutomationPolicyService.AutomationDecision.MANUAL_APPROVAL);
        BrainGovernanceService governanceService = mock(BrainGovernanceService.class);
        com.chubby.dolphin.brain.execution.BrainExecutionPublisher executionPublisher = mock(com.chubby.dolphin.brain.execution.BrainExecutionPublisher.class);

        service = new BrainRecommendationService(
                contextBuilder,
                scoringEngine,
                decisionEngine,
                riskEvaluator,
                actionPlanner,
                decisionRepo,
                legacyDecisionService,
                policyService,
                governanceService,
                executionPublisher,
                mapper
        );
    }

    @Test
    public void testEvaluateAndSaveSuccess() {
        String workspaceId = "test-workspace";
        BrainContext context = BrainContext.builder().workspaceId(workspaceId).build();
        when(contextBuilder.build(workspaceId)).thenReturn(context);

        BrainDecision recommendation = new BrainDecision();
        recommendation.setDecisionType("SCALE_UP");
        when(decisionEngine.generateDecisions(context)).thenReturn(Collections.singletonList(recommendation));

        when(scoringEngine.calculateConfidence(context)).thenReturn(80.0);
        when(riskEvaluator.calculateRisk(context, recommendation)).thenReturn(20.0);
        when(actionPlanner.buildPlan(recommendation)).thenReturn(Collections.singletonList("Step 1"));

        when(decisionRepo.save(any(BrainDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<BrainDecision> result = service.evaluateAndSave(workspaceId);

        assertNotNull(result);
        assertEquals(1, result.size());
        BrainDecision saved = result.get(0);
        assertEquals("SCALE_UP", saved.getDecisionType());
        assertEquals(0.20, saved.getRiskScore());
        assertTrue(saved.getCampaignSnapshotJson().contains("Step 1"));
        verify(decisionRepo, times(1)).save(any(BrainDecision.class));
    }

    @Test
    public void testApproveDelegation() {
        String id = "dec-1";
        String email = "admin@dolphin.ai";
        BrainDecision mockDecision = new BrainDecision();
        when(legacyDecisionService.approveDecision(id, email)).thenReturn(mockDecision);

        BrainDecision approved = service.approve(id, email);

        assertNotNull(approved);
        verify(legacyDecisionService, times(1)).approveDecision(id, email);
    }

    @Test
    public void testRejectDelegation() {
        String id = "dec-1";
        String email = "admin@dolphin.ai";
        BrainDecision mockDecision = new BrainDecision();
        when(legacyDecisionService.rejectDecision(id, email)).thenReturn(mockDecision);

        BrainDecision rejected = service.reject(id, email);

        assertNotNull(rejected);
        verify(legacyDecisionService, times(1)).rejectDecision(id, email);
    }
}
