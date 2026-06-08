package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Wallet;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainDecisionEngineTest {

    @Mock private BusinessLlmFacadeService llmRouter;
    private final ObjectMapper mapper = new ObjectMapper();
    private BrainDecisionEngine engine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        BrainLearningEngine learningEngine = mock(BrainLearningEngine.class);
        when(learningEngine.getLearningStats(anyString(), anyString()))
                .thenReturn(new BrainLearningEngine.LearningStats(0.85, 0.15, 12.0, 10.0));
        BrainExperimentEngine experimentEngine = mock(BrainExperimentEngine.class);
        BrainMemoryService memoryService = mock(BrainMemoryService.class);
        BrainGovernanceService governanceService = mock(BrainGovernanceService.class);

        engine = new BrainDecisionEngine(llmRouter, mapper, learningEngine, experimentEngine, memoryService, governanceService);
    }

    @Test
    public void testLlmSuccessPath() {
        String json = "[\n" +
                "  {\n" +
                "    \"title\": \"SCALE_UP\",\n" +
                "    \"description\": \"Scale up campaign budget due to low CPL\",\n" +
                "    \"impact\": \"Expected 20% ROAS lift\",\n" +
                "    \"priority\": \"HIGH\"\n" +
                "  }\n" +
                "]";
        BusinessLlmFacadeService.LlmResponse response = new BusinessLlmFacadeService.LlmResponse(json, "OLLAMA", "llama3");
        when(llmRouter.ask(anyString())).thenReturn(response);

        Campaign c = new Campaign();
        c.setId("camp-1");
        c.setName("Campaign 1");
        c.setBudget(1000.0);

        BrainContext context = BrainContext.builder()
                .workspaceId("test-workspace")
                .campaigns(Collections.singletonList(c))
                .build();

        List<BrainDecision> decisions = engine.generateDecisions(context);

        assertNotNull(decisions);
        assertEquals(1, decisions.size());
        BrainDecision d = decisions.get(0);
        assertEquals("SCALE_UP", d.getDecisionType());
        assertEquals("Scale up campaign budget due to low CPL", d.getAction());
        assertEquals("Expected 20% ROAS lift", d.getReason());
        assertEquals(90.0, d.getConfidenceScore());
    }

    @Test
    public void testFallbackRuleBasedPath() {
        // Mock LlmRouterService to throw exception or return null to trigger fallback
        when(llmRouter.ask(anyString())).thenThrow(new RuntimeException("LLM offline"));

        Campaign c = new Campaign();
        c.setId("camp-1");
        c.setName("Underperforming Campaign");
        c.setStatus("ACTIVE");
        c.setRoas(1.2); // Low ROAS triggers PAUSE/SCALE_DOWN in RuleEngine
        c.setBudget(1000.0);

        Wallet w = new Wallet();
        w.setBalance(50.0); // low balance triggers WALLET_FUND in RuleEngine

        BrainContext context = BrainContext.builder()
                .workspaceId("test-workspace")
                .campaigns(Collections.singletonList(c))
                .wallet(w)
                .build();

        List<BrainDecision> decisions = engine.generateDecisions(context);

        assertNotNull(decisions);
        assertFalse(decisions.isEmpty());
        // Should contain budget reallocate / scale down
        boolean hasScaleDown = decisions.stream().anyMatch(d -> "SCALE_DOWN".equals(d.getDecisionType()));
        boolean hasReallocate = decisions.stream().anyMatch(d -> "BUDGET_REALLOCATE".equals(d.getDecisionType()));

        assertTrue(hasScaleDown || hasReallocate);
    }
}
