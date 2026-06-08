package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import org.springframework.stereotype.Component;

@Component
public class BrainScoringEngine {

    public double calculateConfidence(BrainContext context) {
        if (context == null) {
            return 0.0;
        }

        double campaignScore = 0.0;
        if (context.getCampaigns() != null && !context.getCampaigns().isEmpty()) {
            int count = context.getCampaigns().size();
            campaignScore += Math.min(count * 4.0, 20.0); // max 20 pts for campaign count

            double totalDays = 0.0;
            for (Campaign c : context.getCampaigns()) {
                totalDays += (c.getDaysOfData() != null ? c.getDaysOfData() : 0);
            }
            double avgDays = totalDays / count;
            campaignScore += Math.min(avgDays * 1.5, 20.0); // max 20 pts for tracking history
        } else {
            // Few campaigns or empty: stays low
            campaignScore += 10.0;
        }

        double leadScore = 0.0;
        if (context.getLeads() != null && !context.getLeads().isEmpty()) {
            int leadCount = context.getLeads().size();
            leadScore += Math.min(leadCount * 2.0, 20.0); // max 20 pts for lead volume
        }

        double metricScore = 0.0;
        if (context.getMetricSnapshots() != null && !context.getMetricSnapshots().isEmpty()) {
            int snapshotCount = context.getMetricSnapshots().size();
            metricScore += Math.min(snapshotCount * 0.5, 20.0); // max 20 pts for snapshot count
        }

        double historyScore = 15.0; // Base baseline
        if (context.getRecentDecisions() != null && !context.getRecentDecisions().isEmpty()) {
            long totalDecisions = context.getRecentDecisions().size();
            long accepted = context.getRecentDecisions().stream()
                    .filter(d -> "APPROVED".equals(d.getStatus()) || "AUTO_EXECUTED".equals(d.getStatus()))
                    .count();
            if (totalDecisions > 0) {
                historyScore = ((double) accepted / totalDecisions) * 20.0;
            }
        }

        double total = campaignScore + leadScore + metricScore + historyScore;
        // Clamp between 0.0 and 100.0
        return Math.max(0.0, Math.min(total, 100.0));
    }
}
