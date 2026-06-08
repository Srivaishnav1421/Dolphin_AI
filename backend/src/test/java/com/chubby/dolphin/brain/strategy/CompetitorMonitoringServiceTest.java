package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.entity.CompetitorInsight;
import com.chubby.dolphin.repository.CompetitorInsightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CompetitorMonitoringServiceTest {

    @Mock private CompetitorInsightRepository insightRepo;
    private CompetitorMonitoringService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CompetitorMonitoringService(insightRepo);
    }

    @Test
    public void testThreatScoreCalculation() {
        String accountId = "test-ws";
        CompetitorInsight insight = new CompetitorInsight();
        insight.setCompetitorUrl("https://spy-alpha.com");
        insight.setPricingModel("FREEMIUM");
        insight.setExtractedHooks(List.of("Hook 1", "Hook 2"));
        insight.setTargetDemographics("India market segment");

        when(insightRepo.findByAccountId(accountId)).thenReturn(Collections.singletonList(insight));

        Map<String, Object> result = service.calculateThreats(accountId);

        assertNotNull(result);
        assertTrue(result.containsKey("threatScore"));
        double score = (double) result.get("threatScore");
        assertTrue(score > 35.0);
    }
}
