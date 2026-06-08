package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.StrategicPlan;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StrategicGoalEngineTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private MetricSnapshotRepository snapshotRepo;
    private StrategicGoalEngine engine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        engine = new StrategicGoalEngine(campaignRepo, snapshotRepo);
    }

    @Test
    public void testGeneratePlanSuccess() {
        String accountId = "test-ws";
        Campaign c = new Campaign();
        c.setSpent(2000.0);
        c.setRoas(3.5);

        MetricSnapshot s = new MetricSnapshot();
        s.setSpend(100.0);
        s.setRevenue(350.0);

        when(campaignRepo.findByAccountId(accountId)).thenReturn(Collections.singletonList(c));
        when(snapshotRepo.findByAccountId(accountId)).thenReturn(Collections.singletonList(s));

        StrategicPlan plan = engine.generatePlan(accountId);

        assertNotNull(plan);
        assertTrue(plan.getGrowthTarget() > 0.0);
        assertTrue(plan.getExpectedRevenue() > 0.0);
        assertFalse(plan.getRisks().isEmpty());
        assertFalse(plan.getMilestones().isEmpty());
    }
}
