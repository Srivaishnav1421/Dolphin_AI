package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.BudgetRecommendation;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BudgetOptimizationEngineTest {

    @Mock private CampaignRepository campaignRepo;
    private BudgetOptimizationEngine engine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        engine = new BudgetOptimizationEngine(campaignRepo);
    }

    @Test
    public void testOptimizeBudgets() {
        String accountId = "test-ws";
        Campaign c = new Campaign();
        c.setName("Festive Watch scale-up");
        c.setStatus("ACTIVE");
        c.setBudget(100.0);
        c.setRoas(4.5);

        when(campaignRepo.findByAccountId(accountId)).thenReturn(Collections.singletonList(c));

        BudgetRecommendation rec = engine.optimizeBudgets(accountId);

        assertNotNull(rec);
        assertFalse(rec.getUnderfundedCampaigns().isEmpty());
        assertFalse(rec.getOverfundedCampaigns().isEmpty()); // Fallback mock check
        assertTrue(rec.getWasteDetected() >= 0.0);
    }
}
