package com.chubby.dolphin.brain.strategy;

import com.chubby.dolphin.brain.strategy.dto.BudgetRecommendation;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class BudgetOptimizationEngine {

    private final CampaignRepository campaignRepo;

    public BudgetRecommendation optimizeBudgets(String accountId) {
        List<Campaign> campaigns = campaignRepo.findByAccountId(accountId);

        List<String> underfunded = new ArrayList<>();
        List<String> overfunded = new ArrayList<>();
        List<String> scaling = new ArrayList<>();
        double wasteDetected = 0.0;
        List<Double> cplValues = campaigns.stream()
                .map(Campaign::getCpl)
                .filter(v -> v != null && v > 0.0)
                .sorted(Comparator.naturalOrder())
                .toList();
        double medianCpl = cplValues.isEmpty() ? 0.0 : cplValues.get(cplValues.size() / 2);

        for (Campaign c : campaigns) {
            double roas = c.getRoas() != null ? c.getRoas() : 2.0;
            double spent = c.getSpent() != null ? c.getSpent() : 0.0;
            double cpl = c.getCpl() != null ? c.getCpl() : 0.0;
            double budget = c.getBudget() != null ? c.getBudget() : 0.0;
            boolean hasEnoughSignal = spent > Math.max(500.0, budget * 0.10);
            boolean cplEfficient = medianCpl <= 0.0 || (cpl > 0.0 && cpl <= medianCpl * 0.85);
            boolean cplInefficient = medianCpl > 0.0 && cpl > medianCpl * 1.25;

            if (!"ACTIVE".equals(c.getStatus()) || !hasEnoughSignal) {
                continue;
            }

            if (roas >= 3.0 && cplEfficient) {
                underfunded.add(c.getName() + " (ROAS: " + roas + "x, CPL: ₹" + Math.round(cpl) + ")");
                scaling.add("Increase " + c.getName() + " by 10-15% only after the next learning window confirms CPL stability.");
            } else if (roas < 1.4 || cplInefficient) {
                overfunded.add(c.getName() + " (ROAS: " + roas + "x, CPL: ₹" + Math.round(cpl) + ")");
                wasteDetected += Math.max(0.0, spent * 0.20);
            }
        }

        return BudgetRecommendation.builder()
                .underfundedCampaigns(underfunded)
                .overfundedCampaigns(overfunded)
                .wasteDetected(wasteDetected)
                .scalingOpportunities(scaling)
                .build();
    }
}
