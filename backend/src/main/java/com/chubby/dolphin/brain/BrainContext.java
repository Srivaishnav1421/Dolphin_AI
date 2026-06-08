package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrainContext {
    private String workspaceId;
    private List<Campaign> campaigns;
    private List<Lead> leads;
    private Wallet wallet;
    private List<CompetitorInsight> competitorInsights;
    private List<BrainDecision> recentDecisions;
    private List<MetricSnapshot> metricSnapshots;
}
