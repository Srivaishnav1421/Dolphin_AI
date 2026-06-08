package com.chubby.dolphin.growth;

import com.chubby.dolphin.growth.dto.ClvForecast;
import com.chubby.dolphin.growth.dto.ChurnPrediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiceoService {

    private final WorkspaceHealthEngine healthEngine;
    private final ChurnPredictionEngine churnEngine;
    private final ClvForecastEngine clvEngine;

    public List<String> generateExecutiveRecommendations(String workspaceId) {
        log.info("👔 Generating corporate AI CEO strategic mandates for workspace: {}", workspaceId);

        double health = healthEngine.calculateHealthScore(workspaceId);
        ChurnPrediction churn = churnEngine.predictChurn(workspaceId);
        ClvForecast clv = clvEngine.forecastClv(workspaceId);

        List<String> recommendations = new ArrayList<>();

        if (health < 60) {
            recommendations.add("🚨 CRITICAL: Focus immediate resources on retaining Workspace ID " + workspaceId + ". Health index is critical (" + Math.round(health) + "/100).");
            recommendations.add("🛑 Governance Mandate: Temporarily pause experimental multi-variate test cycles to stabilize high-CPL leakages.");
        } else {
            recommendations.add("📈 Opportunistic Scaling: Allocate 25% higher AI execution budget to Workspace ID " + workspaceId + " to unlock predicted CLV gains.");
            recommendations.add("⚡ Learning Mandate: Engage standard UCB1 Bandit exploitation weights (90/10 split) to maximize high-performing assets.");
        }

        if (churn.getChurnProbability() > 50.0) {
            recommendations.add("🔥 Retention Alert: Initiate conversational retargeting workflows. Churn risk is flagged at " + Math.round(churn.getChurnProbability()) + "%.");
        } else {
            recommendations.add("🛡️ Pipeline Safe: Current churn risk is perfectly low (" + Math.round(churn.getChurnProbability()) + "%). Support baseline organic growth.");
        }

        return recommendations;
    }
}
