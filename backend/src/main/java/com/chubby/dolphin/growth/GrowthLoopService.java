package com.chubby.dolphin.growth;

import com.chubby.dolphin.growth.dto.PortfolioInsight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GrowthLoopService {

    private final PortfolioOrchestratorService portfolioOrchestrator;
    private final SimpMessagingTemplate wsTemplate;

    @Autowired
    public GrowthLoopService(
            PortfolioOrchestratorService portfolioOrchestrator,
            @Autowired(required = false) SimpMessagingTemplate wsTemplate) {
        this.portfolioOrchestrator = portfolioOrchestrator;
        this.wsTemplate = wsTemplate;
    }

    @Scheduled(fixedDelay = 21600000)
    public void executeGrowthLoop() {
        log.info("🔄 [AGOS Core Scheduler] Commencing 6-hour dynamic portfolio growth optimization loop...");
        try {
            List<PortfolioInsight> insights = portfolioOrchestrator.orchestratePortfolio();
            log.info("📊 Completed portfolio growth analysis. Workspaces processed: {}", insights.size());

            if (wsTemplate != null) {
                log.info("📡 Broadcasting real-time growth loop outcomes via WebSockets...");
                wsTemplate.convertAndSend("/topic/growth/portfolio", insights);
            }
        } catch (Exception e) {
            log.error("❌ Growth loop cycle failed: {}", e.getMessage(), e);
        }
    }
}
