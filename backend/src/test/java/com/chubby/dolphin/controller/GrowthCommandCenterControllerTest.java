package com.chubby.dolphin.controller;

import com.chubby.dolphin.growth.*;
import com.chubby.dolphin.growth.dto.ClvForecast;
import com.chubby.dolphin.growth.dto.ChurnPrediction;
import com.chubby.dolphin.growth.dto.PortfolioInsight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GrowthCommandCenterControllerTest {

    @Mock private PortfolioOrchestratorService orchestrator;
    @Mock private WorkspaceHealthEngine healthEngine;
    @Mock private ChurnPredictionEngine churnEngine;
    @Mock private ClvForecastEngine clvEngine;
    @Mock private AiceoService ceoService;
    @Mock private com.chubby.dolphin.security.SecurityUtils securityUtils;

    private GrowthCommandCenterController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(securityUtils.currentWorkspaceId()).thenReturn("test-ws");
        controller = new GrowthCommandCenterController(
                orchestrator, healthEngine, churnEngine, clvEngine, ceoService, securityUtils
        );
    }

    @Test
    public void testGetPortfolio() {
        when(orchestrator.orchestratePortfolio()).thenReturn(Collections.emptyList());
        ResponseEntity<List<PortfolioInsight>> res = controller.getPortfolio();
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    public void testGetChurn() {
        ChurnPrediction prediction = ChurnPrediction.builder().churnProbability(20.0).build();
        when(churnEngine.predictChurn("test-ws")).thenReturn(prediction);

        ResponseEntity<ChurnPrediction> res = controller.getChurn("test-ws");
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    public void testGetExecutiveSummary() {
        when(orchestrator.orchestratePortfolio()).thenReturn(Collections.emptyList());
        when(ceoService.generateExecutiveRecommendations("test-ws")).thenReturn(List.of("Mandate 1"));

        ResponseEntity<Map<String, Object>> res = controller.getExecutiveSummary("test-ws");
        assertNotNull(res);
        assertEquals(200, res.getStatusCode().value());
        assertTrue(res.getBody().containsKey("revenueManaged"));
        assertTrue(res.getBody().containsKey("ceoRecommendations"));
    }
}
