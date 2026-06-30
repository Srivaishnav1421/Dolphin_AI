package com.chubby.dolphin.brain;

import com.chubby.dolphin.brain.execution.*;
import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.BrainDecisionHistoryRepository;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainExecutionServiceTest {

    private BrainDecisionRepository decisionRepo;
    private CampaignRepository campaignRepo;
    private BrainDecisionHistoryRepository historyRepo;
    private BrainOutcomeAnalyzer outcomeAnalyzer;
    private BrainLearningEngine learningEngine;
    private BrainMemoryService memoryService;
    private BrainGovernanceService governanceService;
    private BrainAutomationPolicyService policyService;
    private BrainActionAuditService actionAuditService;
    private SimpMessagingTemplate wsTemplate;
    private ObjectMapper mapper;
    private BrainExecutionService executionService;

    @BeforeEach
    public void setUp() {
        decisionRepo = mock(BrainDecisionRepository.class);
        campaignRepo = mock(CampaignRepository.class);
        historyRepo = mock(BrainDecisionHistoryRepository.class);
        outcomeAnalyzer = mock(BrainOutcomeAnalyzer.class);
        learningEngine = mock(BrainLearningEngine.class);
        memoryService = mock(BrainMemoryService.class);
        governanceService = mock(BrainGovernanceService.class);
        policyService = mock(BrainAutomationPolicyService.class);
        actionAuditService = mock(BrainActionAuditService.class);
        wsTemplate = mock(SimpMessagingTemplate.class);
        mapper = new ObjectMapper();

        executionService = new BrainExecutionService(
                decisionRepo, campaignRepo, historyRepo, outcomeAnalyzer,
                learningEngine, memoryService, governanceService, policyService,
                actionAuditService, wsTemplate, mapper
        );
    }

    @Test
    public void testValidateExecutionFails() {
        BrainDecision decision = new BrainDecision();
        decision.setAccountId(null); // fails active check

        boolean valid = executionService.validateExecution(decision);
        assertFalse(valid);
    }

    @Test
    public void testValidateExecutionSuccess() {
        BrainDecision decision = new BrainDecision();
        decision.setAccountId("w1");
        decision.setStatus("PENDING_APPROVAL");

        boolean valid = executionService.validateExecution(decision);
        assertTrue(valid);
    }

    @Test
    public void testExecuteRecommendationSuccess() {
        BrainDecision d = new BrainDecision();
        d.setId("d1");
        d.setAccountId("w1");
        d.setStatus("PENDING_APPROVAL");
        d.setCampaignId("c1");
        d.setDecisionType("PAUSE");

        Campaign c = new Campaign();
        c.setId("c1");
        c.setStatus("ACTIVE");
        c.setBudget(1000.0);

        when(decisionRepo.findById("d1")).thenReturn(Optional.of(d));
        when(campaignRepo.findById("c1")).thenReturn(Optional.of(c));
        ExecutionResult result = executionService.executeRecommendation("d1");

        assertNotNull(result);
        assertEquals(ExecutionStatus.EXECUTED, result.getStatus());
        assertEquals("EXECUTED_LOCAL_ONLY", d.getStatus());
        assertEquals("PAUSED", c.getStatus());
        verify(decisionRepo, atLeastOnce()).save(d);
        verify(campaignRepo, times(1)).save(c);
        verify(outcomeAnalyzer, never()).analyze(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(learningEngine, never()).updateStats(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}
