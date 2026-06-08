package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class BrainContextBuilder {

    private final CampaignRepository campaignRepo;
    private final LeadRepository leadRepo;
    private final WalletRepository walletRepo;
    private final CompetitorInsightRepository competitorInsightRepo;
    private final BrainDecisionRepository brainDecisionRepo;
    private final MetricSnapshotRepository metricSnapshotRepo;

    public BrainContextBuilder(CampaignRepository campaignRepo,
                               LeadRepository leadRepo,
                               WalletRepository walletRepo,
                               CompetitorInsightRepository competitorInsightRepo,
                               BrainDecisionRepository brainDecisionRepo,
                               MetricSnapshotRepository metricSnapshotRepo) {
        this.campaignRepo = campaignRepo;
        this.leadRepo = leadRepo;
        this.walletRepo = walletRepo;
        this.competitorInsightRepo = competitorInsightRepo;
        this.brainDecisionRepo = brainDecisionRepo;
        this.metricSnapshotRepo = metricSnapshotRepo;
    }

    public BrainContext build(String workspaceId) {
        if (workspaceId == null) {
            return BrainContext.builder()
                    .campaigns(new ArrayList<>())
                    .leads(new ArrayList<>())
                    .competitorInsights(new ArrayList<>())
                    .recentDecisions(new ArrayList<>())
                    .metricSnapshots(new ArrayList<>())
                    .build();
        }

        List<Campaign> campaigns = campaignRepo.findByAccountId(workspaceId);
        if (campaigns == null) campaigns = new ArrayList<>();

        List<Lead> leads = leadRepo.findByAccountId(workspaceId);
        if (leads == null) leads = new ArrayList<>();

        Wallet wallet = walletRepo.findFirstByAccountId(workspaceId).orElse(null);

        List<CompetitorInsight> competitorInsights = competitorInsightRepo.findByAccountId(workspaceId);
        if (competitorInsights == null) competitorInsights = new ArrayList<>();

        List<BrainDecision> recentDecisions = brainDecisionRepo.findByAccountIdOrderByCreatedAtDesc(workspaceId);
        if (recentDecisions == null) recentDecisions = new ArrayList<>();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        List<MetricSnapshot> metricSnapshots = metricSnapshotRepo.findByAccountIdAndDateRange(workspaceId, start, end);
        if (metricSnapshots == null) metricSnapshots = new ArrayList<>();

        return BrainContext.builder()
                .workspaceId(workspaceId)
                .campaigns(campaigns)
                .leads(leads)
                .wallet(wallet)
                .competitorInsights(competitorInsights)
                .recentDecisions(recentDecisions)
                .metricSnapshots(metricSnapshots)
                .build();
    }
}
