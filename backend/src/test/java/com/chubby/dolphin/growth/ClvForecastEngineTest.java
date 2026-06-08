package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.growth.dto.ClvForecast;
import com.chubby.dolphin.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClvForecastEngineTest {

    @Mock private CampaignRepository campaignRepo;
    private ClvForecastEngine clvEngine;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        clvEngine = new ClvForecastEngine(campaignRepo);
    }

    @Test
    public void testForecastClvEmpty() {
        when(campaignRepo.findByAccountId("test-ws")).thenReturn(Collections.emptyList());

        ClvForecast forecast = clvEngine.forecastClv("test-ws");
        assertNotNull(forecast);
        assertEquals(1500.0, forecast.getCurrentClv());
        assertEquals(1770.0, forecast.getPredictedClv());
    }

    @Test
    public void testForecastClvActive() {
        Campaign c = new Campaign();
        c.setSpent(2000.0);
        c.setRoas(4.0); // revenue = 2000 * 4 = 8000

        when(campaignRepo.findByAccountId("test-ws")).thenReturn(List.of(c));

        ClvForecast forecast = clvEngine.forecastClv("test-ws");
        assertNotNull(forecast);
        // currentClv = 8000 + (2000 * 0.15) = 8300
        assertEquals(8300.0, forecast.getCurrentClv());
        assertTrue(forecast.getGrowthPotential() > 0);
    }
}
