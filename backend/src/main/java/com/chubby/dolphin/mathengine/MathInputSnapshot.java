package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class MathInputSnapshot {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public static MathInputSnapshot forCampaign(Campaign campaign, MetricSnapshot snapshot) {
        MathInputSnapshot input = new MathInputSnapshot();
        if (campaign != null) {
            input.put("campaignId", campaign.getId());
            input.put("workspaceId", campaign.getWorkspaceId());
            input.put("name", campaign.getName());
            input.put("status", campaign.getStatus());
            input.put("objective", campaign.getObjective());
            input.put("budget", campaign.getBudget());
            input.put("spent", campaign.getSpent());
            input.put("ctr", campaign.getCtr());
            input.put("cpl", campaign.getCpl());
            input.put("roas", campaign.getRoas());
            input.put("performanceScore", campaign.getPerformanceScore());
            input.put("conversions", campaign.getConversions());
            input.put("daysOfData", campaign.getDaysOfData());
            input.put("conversionRate", campaign.getConversionRate());
            input.put("createdAt", campaign.getCreatedAt());
            input.put("updatedAt", campaign.getUpdatedAt());
        }
        if (snapshot != null) {
            input.put("snapshotDate", snapshot.getSnapshotDate());
            input.put("impressions", snapshot.getImpressions());
            input.put("clicks", snapshot.getClicks());
            input.put("spend", snapshot.getSpend());
            input.put("snapshotConversions", snapshot.getConversions());
            input.put("snapshotCtr", snapshot.getCtr());
            input.put("cpc", snapshot.getCpc());
            input.put("snapshotCpl", snapshot.getCpl());
            input.put("snapshotRoas", snapshot.getRoas());
            input.put("frequency", snapshot.getFrequency());
            input.put("leads", snapshot.getLeads());
        }
        input.put("evaluatedAt", LocalDateTime.now());
        return input;
    }

    public MathInputSnapshot put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public Map<String, Object> values() {
        return values;
    }
}
