package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceHealthEngine {

    private final CampaignRepository campaignRepo;
    private final MetricSnapshotRepository snapshotRepo;

    public enum HealthClassification {
        EXCELLENT, GOOD, WARNING, CRITICAL
    }

    public double calculateHealthScore(String workspaceId) {
        log.info("🏥 Calculating workspace growth health index for workspace: {}", workspaceId);
        List<Campaign> campaigns = campaignRepo.findByAccountId(workspaceId);
        if (campaigns.isEmpty()) {
            return 75.0; // Standard healthy baseline
        }

        double totalRoas = 0.0;
        int activeCount = 0;
        double totalBudget = 0.0;
        double wastedSpend = 0.0;

        for (Campaign c : campaigns) {
            if ("ACTIVE".equalsIgnoreCase(c.getStatus())) {
                activeCount++;
                totalBudget += (c.getBudget() != null ? c.getBudget() : 0.0);
                double roas = c.getRoas() != null ? c.getRoas() : 0.0;
                totalRoas += roas;

                if (roas < 1.3 && c.getSpent() != null && c.getSpent() > 0) {
                    wastedSpend += c.getSpent();
                }
            }
        }

        double avgRoas = activeCount > 0 ? (totalRoas / activeCount) : 1.5;
        double roasComponent = Math.clamp((avgRoas / 3.0) * 50.0, 10.0, 50.0);

        double wasteRatio = totalBudget > 0 ? (wastedSpend / totalBudget) : 0.0;
        double wasteComponent = Math.clamp((1.0 - wasteRatio) * 30.0, 5.0, 30.0);

        List<MetricSnapshot> snapshots = snapshotRepo.findByAccountId(workspaceId);
        double stabilityComponent = snapshots.size() > 5 ? 20.0 : 12.0;

        double score = roasComponent + wasteComponent + stabilityComponent;
        return Math.clamp(score, 0.0, 100.0);
    }

    public HealthClassification getClassification(double score) {
        if (score >= 85) return HealthClassification.EXCELLENT;
        if (score >= 70) return HealthClassification.GOOD;
        if (score >= 50) return HealthClassification.WARNING;
        return HealthClassification.CRITICAL;
    }
}
