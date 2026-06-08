package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.RevenueForecast;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenueForecastEngine {

    private final CampaignRepository campaignRepo;
    private final MetricSnapshotRepository snapshotRepo;

    public RevenueForecast generateForecast(String accountId) {
        List<Campaign> campaigns = campaignRepo.findByAccountId(accountId);
        List<MetricSnapshot> snapshots = snapshotRepo.findByAccountId(accountId);

        double dailySpendBase = 0.0;
        double dailyRevenueBase = 0.0;

        for (Campaign c : campaigns) {
            double budget = c.getBudget() != null ? c.getBudget() : 100.0;
            dailySpendBase += budget;
            double roas = c.getRoas() != null ? c.getRoas() : 2.5;
            dailyRevenueBase += (budget * roas);
        }

        if (snapshots.size() > 0) {
            double totalSnapshotSpend = 0.0;
            double totalSnapshotRevenue = 0.0;
            for (MetricSnapshot s : snapshots) {
                if (s.getSpend() != null) totalSnapshotSpend += s.getSpend();
                if (s.getRevenue() != null) totalSnapshotRevenue += s.getRevenue();
            }
            double snapshotDays = snapshots.size();
            double avgDailySpend = totalSnapshotSpend / snapshotDays;
            double avgDailyRevenue = totalSnapshotRevenue / snapshotDays;

            // Weighted average: 60% active campaigns baseline, 40% historical snapshot trends
            dailySpendBase = (dailySpendBase * 0.6) + (avgDailySpend * 0.4);
            dailyRevenueBase = (dailyRevenueBase * 0.6) + (avgDailyRevenue * 0.4);
        }

        if (dailySpendBase <= 0) dailySpendBase = 200.0;
        if (dailyRevenueBase <= 0) dailyRevenueBase = 500.0;

        // Forecast intervals with minor compounding lift scaling parameters
        double spend7d = dailySpendBase * 7;
        double revenue7d = dailyRevenueBase * 7;
        double profit7d = revenue7d - spend7d;

        double spend30d = dailySpendBase * 30;
        double revenue30d = dailyRevenueBase * 30 * 1.05; // 5% efficiency compounding lift
        double profit30d = revenue30d - spend30d;

        double spend90d = dailySpendBase * 90;
        double revenue90d = dailyRevenueBase * 90 * 1.12; // 12% compounding lift
        double profit90d = revenue90d - spend90d;

        return RevenueForecast.builder()
                .spend7d(spend7d)
                .revenue7d(revenue7d)
                .profit7d(profit7d)
                .spend30d(spend30d)
                .revenue30d(revenue30d)
                .profit30d(profit30d)
                .spend90d(spend90d)
                .revenue90d(revenue90d)
                .profit90d(profit90d)
                .build();
    }
}
