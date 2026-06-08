package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.repository.BrainDecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainGovernanceServiceTest {

    private BrainDecisionRepository decisionRepo;
    private BrainGovernanceService governanceService;

    @BeforeEach
    public void setUp() {
        decisionRepo = mock(BrainDecisionRepository.class);
        governanceService = new BrainGovernanceService(decisionRepo);
    }

    @Test
    public void testEvaluateGovernanceOverScaling() {
        // scaling budget from 100 to 200 (+100% > 40%)
        double score = governanceService.evaluateGovernance("w1", "c1", "SCALE_UP", 100.0, 200.0);
        assertEquals(75.0, score); // 100 - 25
    }

    @Test
    public void testEvaluateGovernanceOscillation() {
        List<BrainDecision> recents = new ArrayList<>();
        BrainDecision d1 = new BrainDecision();
        d1.setCampaignId("c1");
        d1.setDecisionType("PAUSE");
        d1.setStatus("EXECUTED");
        recents.add(d1);

        BrainDecision d2 = new BrainDecision();
        d2.setCampaignId("c1");
        d2.setDecisionType("RESUME");
        d2.setStatus("EXECUTED");
        recents.add(d2);

        when(decisionRepo.findTop50ByAccountIdOrderByCreatedAtDesc("w1")).thenReturn(recents);

        double score = governanceService.evaluateGovernance("w1", "c1", "PAUSE", 100.0, 100.0);
        assertEquals(60.0, score); // 100 - 40
    }
}
