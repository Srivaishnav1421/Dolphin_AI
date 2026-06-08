package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.growth.dto.ChurnPrediction;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ChurnPredictionEngineTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private LeadRepository leadRepo;

    private ChurnPredictionEngine churnEngine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        churnEngine = new ChurnPredictionEngine(campaignRepo, leadRepo);
    }

    @Test
    public void testPredictChurnInactive() {
        when(campaignRepo.findByAccountId("test-ws")).thenReturn(Collections.emptyList());
        when(leadRepo.findByAccountId("test-ws")).thenReturn(Collections.emptyList());

        ChurnPrediction prediction = churnEngine.predictChurn("test-ws");
        assertNotNull(prediction);
        assertTrue(prediction.getChurnProbability() > 50.0);
        assertTrue(prediction.getRiskFactors().contains("Zero active marketing campaigns detected in portfolio."));
    }

    @Test
    public void testPredictChurnHealthy() {
        Campaign c = new Campaign();
        c.setStatus("ACTIVE");
        c.setBudget(1000.0);

        when(campaignRepo.findByAccountId("test-ws")).thenReturn(List.of(c));
        when(leadRepo.findByAccountId("test-ws")).thenReturn(Collections.emptyList());

        ChurnPrediction prediction = churnEngine.predictChurn("test-ws");
        assertNotNull(prediction);
        assertTrue(prediction.getChurnProbability() > 10.0);
    }
}
