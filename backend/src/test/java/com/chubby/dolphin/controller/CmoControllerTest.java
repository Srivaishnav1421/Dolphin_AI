package com.chubby.dolphin.controller;

import com.chubby.dolphin.brain.BrainGovernanceService;
import com.chubby.dolphin.brain.strategy.*;
import com.chubby.dolphin.brain.strategy.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CmoControllerTest {

    @Mock private StrategicGoalEngine strategicGoalEngine;
    @Mock private RevenueForecastEngine revenueForecastEngine;
    @Mock private BudgetOptimizationEngine budgetOptimizationEngine;
    @Mock private CompetitorMonitoringService competitorMonitoringService;
    @Mock private CmoMemoryService cmoMemoryService;
    @Mock private BrainGovernanceService governanceService;
    @Mock private com.chubby.dolphin.security.SecurityUtils securityUtils;

    private CmoController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(securityUtils.currentWorkspaceId()).thenReturn("test-ws");
        controller = new CmoController(
                strategicGoalEngine,
                revenueForecastEngine,
                budgetOptimizationEngine,
                competitorMonitoringService,
                cmoMemoryService,
                governanceService,
                securityUtils
        );
    }

    @Test
    public void testGetCmoDashboard() {
        String ws = "test-ws";
        StrategicPlan plan = new StrategicPlan();
        when(strategicGoalEngine.generatePlan(ws)).thenReturn(plan);

        RevenueForecast forecast = new RevenueForecast();
        when(revenueForecastEngine.generateForecast(ws)).thenReturn(forecast);

        BudgetRecommendation rec = new BudgetRecommendation();
        when(budgetOptimizationEngine.optimizeBudgets(ws)).thenReturn(rec);

        when(competitorMonitoringService.calculateThreats(ws)).thenReturn(Collections.singletonMap("threatScore", 42.0));
        when(cmoMemoryService.getMemory(ws)).thenReturn(Collections.singletonMap("winningCreatives", Collections.emptyList()));
        when(governanceService.evaluateGovernance(eq(ws), any(), anyString(), anyDouble(), anyDouble())).thenReturn(95.0);

        ResponseEntity<CmoController.CmoDashboardDto> res = controller.getCmoDashboard(ws);

        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        CmoController.CmoDashboardDto body = res.getBody();
        assertNotNull(body);
        assertEquals(plan, body.getStrategicPlan());
        assertEquals(forecast, body.getRevenueForecast());
        assertEquals(95.0, body.getGovernanceScore());
    }

    @Test
    public void testGetForecast() {
        String ws = "test-ws";
        RevenueForecast forecast = new RevenueForecast();
        when(revenueForecastEngine.generateForecast(ws)).thenReturn(forecast);

        ResponseEntity<RevenueForecast> res = controller.getForecast(ws);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    public void testGetStrategy() {
        String ws = "test-ws";
        StrategicPlan plan = new StrategicPlan();
        when(strategicGoalEngine.generatePlan(ws)).thenReturn(plan);

        ResponseEntity<StrategicPlan> res = controller.getStrategy(ws);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    public void testGetCompetitors() {
        String ws = "test-ws";
        when(competitorMonitoringService.calculateThreats(ws)).thenReturn(Collections.singletonMap("threatScore", 65.0));

        ResponseEntity<Map<String, Object>> res = controller.getCompetitors(ws);
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(65.0, res.getBody().get("threatScore"));
    }
}
