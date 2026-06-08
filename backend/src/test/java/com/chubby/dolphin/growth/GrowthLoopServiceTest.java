package com.chubby.dolphin.growth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;

import static org.mockito.Mockito.*;

public class GrowthLoopServiceTest {

    @Mock private PortfolioOrchestratorService portfolioOrchestrator;
    @Mock private SimpMessagingTemplate wsTemplate;

    private GrowthLoopService growthLoop;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        growthLoop = new GrowthLoopService(portfolioOrchestrator, wsTemplate);
    }

    @Test
    public void testExecuteGrowthLoop() {
        when(portfolioOrchestrator.orchestratePortfolio()).thenReturn(Collections.emptyList());
        growthLoop.executeGrowthLoop();
        verify(portfolioOrchestrator, times(1)).orchestratePortfolio();
        verify(wsTemplate, times(1)).convertAndSend(eq("/topic/growth/portfolio"), anyList());
    }
}
