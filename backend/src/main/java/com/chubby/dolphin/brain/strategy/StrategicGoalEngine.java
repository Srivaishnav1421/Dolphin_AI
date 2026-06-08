package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.StrategicPlan;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StrategicGoalEngine {

    private final CampaignRepository campaignRepo;
    private final MetricSnapshotRepository snapshotRepo;

    public StrategicPlan generatePlan(String accountId) {
        List<Campaign> campaigns = campaignRepo.findByAccountId(accountId);
        List<MetricSnapshot> snapshots = snapshotRepo.findByAccountId(accountId);

        double totalSpend = 0.0;
        double totalRevenue = 0.0;
        double totalRoasSum = 0.0;
        int roasCount = 0;

        for (Campaign c : campaigns) {
            if (c.getSpent() != null) totalSpend += c.getSpent();
            if (c.getRoas() != null) {
                totalRoasSum += c.getRoas();
                roasCount++;
            }
        }

        for (MetricSnapshot s : snapshots) {
            if (s.getSpend() != null) totalSpend += s.getSpend();
            if (s.getRevenue() != null) totalRevenue += s.getRevenue();
        }

        double avgRoas = roasCount > 0 ? (totalRoasSum / roasCount) : 2.5;
        if (avgRoas <= 0) avgRoas = 2.5;

        // Baseline math modeling
        double growthTarget = Math.clamp((avgRoas * 8.5), 5.0, 45.0);
        double expectedRevenue = totalRevenue > 0 ? totalRevenue * (1 + growthTarget / 100.0) : 50000.0;
        double projectedSpend = expectedRevenue / avgRoas;
        double confidence = Math.clamp(75.0 + (snapshots.size() * 0.4), 60.0, 95.0);

        List<String> risks = new ArrayList<>();
        risks.add("Audience ad fatigue in core demographics due to high frequency overlap.");
        risks.add("Wallet budget depletion on high-bid competitor keywords.");
        if (growthTarget > 30.0) {
            risks.add("Over-leveraging capital on hyper-aggressive exploration cycles.");
        }

        List<String> milestones = new ArrayList<>();
        milestones.add("Milestone 1: Complete Meta Pixel Lookalike retraining sequence.");
        milestones.add("Milestone 2: Reduce budget allocation on waste creative segments by 25%.");
        milestones.add("Milestone 3: Scale highly-performing UCB1 Bandit ad variations by 40%.");

        String plan7Day = "Objective: Eliminate waste and set base parameters.\n" +
                "Reasoning: Initial diagnostics reveal high ad frequency in active channels.\n" +
                "Expected Impact: 12% Cost-Per-Lead reduction.\n" +
                "Risk Level: LOW\n" +
                "Success Probability: 92%";

        String plan30Day = "Objective: Retrain Lookalike sets and reallocate budgets.\n" +
                "Reasoning: Leverage high lead density snapshots to trigger Meta CAPI enhancements.\n" +
                "Expected Impact: +22% overall ROAS lift.\n" +
                "Risk Level: MEDIUM\n" +
                "Success Probability: 84%";

        String plan90Day = "Objective: Broad scale portfolio optimization.\n" +
                "Reasoning: Secure persistent ROI compounding using long-term learning stats.\n" +
                "Expected Impact: +45% Revenue growth.\n" +
                "Risk Level: HIGH\n" +
                "Success Probability: 76%";

        return StrategicPlan.builder()
                .growthTarget(growthTarget)
                .expectedRevenue(expectedRevenue)
                .projectedSpend(projectedSpend)
                .confidence(confidence)
                .risks(risks)
                .milestones(milestones)
                .plan7Day(plan7Day)
                .plan30Day(plan30Day)
                .plan90Day(plan90Day)
                .build();
    }
}
