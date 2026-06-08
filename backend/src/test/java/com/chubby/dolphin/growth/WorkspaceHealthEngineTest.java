package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WorkspaceHealthEngineTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private MetricSnapshotRepository snapshotRepo;

    private WorkspaceHealthEngine healthEngine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        healthEngine = new WorkspaceHealthEngine(campaignRepo, snapshotRepo);
    }

    @Test
    public void testCalculateHealthScoreEmptyCampaigns() {
        when(campaignRepo.findByAccountId("test-ws")).thenReturn(Collections.emptyList());
        double score = healthEngine.calculateHealthScore("test-ws");
        assertEquals(75.0, score);
        assertEquals(WorkspaceHealthEngine.HealthClassification.GOOD, healthEngine.getClassification(score));
    }

    @Test
    public void testCalculateHealthScoreActiveCampaigns() {
        Campaign c = new Campaign();
        c.setStatus("ACTIVE");
        c.setBudget(1000.0);
        c.setRoas(4.5);
        c.setSpent(500.0);

        when(campaignRepo.findByAccountId("test-ws")).thenReturn(List.of(c));
        when(snapshotRepo.findByAccountId("test-ws")).thenReturn(Collections.emptyList());

        double score = healthEngine.calculateHealthScore("test-ws");
        assertTrue(score >= 70.0);
    }
}
