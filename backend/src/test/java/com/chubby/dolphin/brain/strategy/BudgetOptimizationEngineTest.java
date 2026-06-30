package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.BudgetRecommendation;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

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
        c.setBudget(10_000.0);
        c.setSpent(2_000.0);
        c.setCpl(300.0);
        c.setRoas(4.5);

        Campaign weak = new Campaign();
        weak.setName("Legacy Awareness");
        weak.setStatus("ACTIVE");
        weak.setBudget(10_000.0);
        weak.setSpent(3_000.0);
        weak.setCpl(800.0);
        weak.setRoas(1.0);

        when(campaignRepo.findByAccountId(accountId)).thenReturn(List.of(c, weak));

        BudgetRecommendation rec = engine.optimizeBudgets(accountId);

        assertNotNull(rec);
        assertFalse(rec.getUnderfundedCampaigns().isEmpty());
        assertFalse(rec.getOverfundedCampaigns().isEmpty());
        assertTrue(rec.getWasteDetected() > 0.0);
    }
}
