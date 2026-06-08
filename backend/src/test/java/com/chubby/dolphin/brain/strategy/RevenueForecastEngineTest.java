package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.RevenueForecast;
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

public class RevenueForecastEngineTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private MetricSnapshotRepository snapshotRepo;
    private RevenueForecastEngine engine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        engine = new RevenueForecastEngine(campaignRepo, snapshotRepo);
    }

    @Test
    public void testForecastSuccess() {
        String accountId = "test-ws";
        Campaign c = new Campaign();
        c.setBudget(500.0);
        c.setRoas(3.0);

        MetricSnapshot s = new MetricSnapshot();
        s.setSpend(200.0);
        s.setRevenue(600.0);

        when(campaignRepo.findByAccountId(accountId)).thenReturn(Collections.singletonList(c));
        when(snapshotRepo.findByAccountId(accountId)).thenReturn(Collections.singletonList(s));

        RevenueForecast forecast = engine.generateForecast(accountId);

        assertNotNull(forecast);
        assertTrue(forecast.getRevenue7d() > 0.0);
        assertTrue(forecast.getRevenue30d() > forecast.getRevenue7d());
        assertTrue(forecast.getRevenue90d() > forecast.getRevenue30d());
    }
}
