package com.chubby.dolphin.brain;

import com.chubby.dolphin.controller.BrainAnalyticsController;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import com.chubby.dolphin.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainAnalyticsControllerTest {

    private BrainDecisionRepository decisionRepo;
    private LeadRepository leadRepository;
    private BrainLearningEngine learningEngine;
    private BrainGovernanceService governanceService;
    private com.chubby.dolphin.security.SecurityUtils sec;
    private BrainAnalyticsController controller;

    @BeforeEach
    public void setUp() {
        decisionRepo = mock(BrainDecisionRepository.class);
        leadRepository = mock(LeadRepository.class);
        learningEngine = mock(BrainLearningEngine.class);
        governanceService = mock(BrainGovernanceService.class);
        sec = mock(com.chubby.dolphin.security.SecurityUtils.class);
        when(sec.currentWorkspaceId()).thenReturn("w1");

        controller = new BrainAnalyticsController(decisionRepo, leadRepository, learningEngine, governanceService, sec);
    }

    @Test
    public void testGetBrainAnalytics() {
        when(decisionRepo.findByAccountIdOrderByCreatedAtDesc(anyString())).thenReturn(new ArrayList<>());
        when(leadRepository.count()).thenReturn(100L);
        when(governanceService.evaluateGovernance(anyString(), any(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(92.0);

        ResponseEntity<BrainAnalyticsController.BrainAnalyticsDto> response = controller.getBrainAnalytics("w1");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(92.0, response.getBody().getGovernanceScore());
        assertEquals(100, response.getBody().getLeadsGenerated());
    }
}
