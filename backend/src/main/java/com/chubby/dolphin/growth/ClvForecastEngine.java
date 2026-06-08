package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.growth.dto.ClvForecast;
import com.chubby.dolphin.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClvForecastEngine {

    private final CampaignRepository campaignRepo;

    public ClvForecast forecastClv(String workspaceId) {
        log.info("📊 Executing dynamic Customer Lifetime Value (CLV) forecasts for workspace: {}", workspaceId);

        List<Campaign> campaigns = campaignRepo.findByAccountId(workspaceId);
        double totalSpent = campaigns.stream().mapToDouble(c -> c.getSpent() != null ? c.getSpent() : 0.0).sum();
        double totalRevenue = campaigns.stream()
                .mapToDouble(c -> {
                    double spent = c.getSpent() != null ? c.getSpent() : 0.0;
                    double roas = c.getRoas() != null ? c.getRoas() : 0.0;
                    return spent * roas;
                }).sum();

        double baseValue = totalRevenue > 0 ? totalRevenue : 1500.0;
        double currentClv = baseValue + (totalSpent * 0.15);

        double predictedClv = currentClv * 1.18;
        double growthPotential = ((predictedClv - currentClv) / currentClv) * 100.0;

        return ClvForecast.builder()
                .currentClv(currentClv)
                .predictedClv(predictedClv)
                .growthPotential(growthPotential)
                .build();
    }
}
