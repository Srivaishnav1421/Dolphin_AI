package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainContextBuilderTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private LeadRepository leadRepo;
    @Mock private WalletRepository walletRepo;
    @Mock private CompetitorInsightRepository competitorInsightRepo;
    @Mock private BrainDecisionRepository brainDecisionRepo;
    @Mock private MetricSnapshotRepository metricSnapshotRepo;

    private BrainContextBuilder builder;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        builder = new BrainContextBuilder(
                campaignRepo,
                leadRepo,
                walletRepo,
                competitorInsightRepo,
                brainDecisionRepo,
                metricSnapshotRepo
        );
    }

    @Test
    public void testBuildSuccess() {
        String workspaceId = "test-workspace";
        Campaign c = new Campaign();
        c.setId("camp-1");
        c.setAccountId(workspaceId);

        Lead l = new Lead();
        l.setId("lead-1");
        l.setAccountId(workspaceId);

        Wallet w = new Wallet();
        w.setAccountId(workspaceId);
        w.setBalance(5000.0);

        CompetitorInsight ci = new CompetitorInsight();
        ci.setAccountId(workspaceId);

        BrainDecision bd = new BrainDecision();
        bd.setAccountId(workspaceId);

        MetricSnapshot ms = new MetricSnapshot();
        ms.setAccountId(workspaceId);

        when(campaignRepo.findByAccountId(workspaceId)).thenReturn(Collections.singletonList(c));
        when(leadRepo.findByAccountId(workspaceId)).thenReturn(Collections.singletonList(l));
        when(walletRepo.findFirstByAccountId(workspaceId)).thenReturn(Optional.of(w));
        when(competitorInsightRepo.findByAccountId(workspaceId)).thenReturn(Collections.singletonList(ci));
        when(brainDecisionRepo.findByAccountIdOrderByCreatedAtDesc(workspaceId)).thenReturn(Collections.singletonList(bd));
        when(metricSnapshotRepo.findByAccountIdAndDateRange(eq(workspaceId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(ms));

        BrainContext context = builder.build(workspaceId);

        assertNotNull(context);
        assertEquals(workspaceId, context.getWorkspaceId());
        assertEquals(1, context.getCampaigns().size());
        assertEquals(1, context.getLeads().size());
        assertNotNull(context.getWallet());
        assertEquals(5000.0, context.getWallet().getBalance());
        assertEquals(1, context.getCompetitorInsights().size());
        assertEquals(1, context.getRecentDecisions().size());
        assertEquals(1, context.getMetricSnapshots().size());
    }

    @Test
    public void testBuildNullSafety() {
        BrainContext context = builder.build(null);
        assertNotNull(context);
        assertNull(context.getWorkspaceId());
        assertTrue(context.getCampaigns().isEmpty());
        assertTrue(context.getLeads().isEmpty());
        assertNull(context.getWallet());
        assertTrue(context.getCompetitorInsights().isEmpty());
        assertTrue(context.getRecentDecisions().isEmpty());
        assertTrue(context.getMetricSnapshots().isEmpty());
    }
}
