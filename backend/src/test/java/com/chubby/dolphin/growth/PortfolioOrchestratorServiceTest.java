package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.growth.dto.ClvForecast;
import com.chubby.dolphin.growth.dto.ChurnPrediction;
import com.chubby.dolphin.growth.dto.PortfolioInsight;
import com.chubby.dolphin.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PortfolioOrchestratorServiceTest {

    @Mock private WorkspaceRepository workspaceRepo;
    @Mock private WorkspaceHealthEngine healthEngine;
    @Mock private ChurnPredictionEngine churnEngine;
    @Mock private ClvForecastEngine clvEngine;
    @Mock private com.chubby.dolphin.security.SecurityUtils securityUtils;

    private PortfolioOrchestratorService orchestrator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        orchestrator = new PortfolioOrchestratorService(workspaceRepo, healthEngine, churnEngine, clvEngine, securityUtils);
    }

    @Test
    public void testOrchestratePortfolio() {
        Workspace ws = new Workspace();
        ws.setId("ws-123");
        ws.setName("Test Workspace");

        when(workspaceRepo.findAll()).thenReturn(List.of(ws));
        when(healthEngine.calculateHealthScore("ws-123")).thenReturn(88.0);
        when(healthEngine.getClassification(88.0)).thenReturn(WorkspaceHealthEngine.HealthClassification.EXCELLENT);

        ChurnPrediction churn = ChurnPrediction.builder().churnProbability(15.0).riskFactors(Collections.emptyList()).build();
        when(churnEngine.predictChurn("ws-123")).thenReturn(churn);

        ClvForecast clv = ClvForecast.builder().currentClv(5000.0).predictedClv(6000.0).growthPotential(20.0).build();
        when(clvEngine.forecastClv("ws-123")).thenReturn(clv);

        List<PortfolioInsight> insights = orchestrator.orchestratePortfolio();
        assertNotNull(insights);
        assertEquals(1, insights.size());
        assertEquals("ws-123", insights.get(0).getWorkspaceId());
        assertEquals(88.0, insights.get(0).getHealthScore());
        assertEquals("EXCELLENT", insights.get(0).getHealthClassification());
    }
}
