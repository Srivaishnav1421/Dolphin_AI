package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CreativeFatigueServiceTest {

    @Mock private AdCreativeRepository creativeRepo;
    @Mock private MetricSnapshotRepository metricRepo;
    @Mock private FatigueAlertRepository fatigueAlertRepo;
    @Mock private CampaignRepository campaignRepo;
    @Mock private MetaConnectionRepository metaConnRepo;
    @Mock private MetaAdsService metaAdsService;
    @Mock private BrainDecisionRepository decisionRepo;
    @Mock private AlertService alertService;
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private CreativeFatigueService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(localApprovalSafetyService.shouldRequireApprovalOnly(anyString())).thenReturn(false);
        service = new CreativeFatigueService(
                creativeRepo, metricRepo, fatigueAlertRepo, campaignRepo,
                metaConnRepo, metaAdsService, decisionRepo, alertService, localApprovalSafetyService
        );
    }

    @Test
    public void testDetectFatigue_CtrTriggersAlertAndRotation() {
        String campaignId = "camp-123";
        String accountId = "acc-456";

        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setAccountId(accountId);
        campaign.setName("Test Campaign");
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));

        AdCreative activeCreative = new AdCreative();
        activeCreative.setId("creative-1");
        activeCreative.setCampaignId(campaignId);
        activeCreative.setStatus("ACTIVE");
        activeCreative.setActualCtr(1.0); // 1.0% CTR
        activeCreative.setMetaAdId("meta-ad-1");
        when(creativeRepo.findByCampaignIdAndStatus(campaignId, "ACTIVE"))
                .thenReturn(List.of(activeCreative));
        when(creativeRepo.findById("creative-1"))
                .thenReturn(Optional.of(activeCreative));

        // Create baseline snapshots showing 2.0% average CTR (1.0% is below 80% of 2.0%)
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setCampaignId(campaignId);
        snapshot.setSnapshotDate(LocalDate.now());
        snapshot.setCtr(2.0);
        snapshot.setFrequency(1.5);
        snapshot.setImpressions(1000L);
        snapshot.setSpend(10.0); // CPM = 10.0
        when(metricRepo.findByCampaignIdAndSnapshotDateBetween(eq(campaignId), any(), any()))
                .thenReturn(List.of(snapshot));

        // Replacement draft creative
        AdCreative draftCreative = new AdCreative();
        draftCreative.setId("creative-2");
        draftCreative.setCampaignId(campaignId);
        draftCreative.setStatus("DRAFT");
        draftCreative.setPredictedCtr(2.5);
        draftCreative.setMetaAdId("meta-ad-2");
        when(creativeRepo.findByCampaignIdAndStatus(campaignId, "DRAFT"))
                .thenReturn(List.of(draftCreative));

        MetaConnection conn = new MetaConnection();
        conn.setAccountId(accountId);
        conn.setTokenStatus("VALID");
        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(accountId, "VALID"))
                .thenReturn(Optional.of(conn));

        when(metaAdsService.resumeCampaign(any(), eq("meta-ad-2"))).thenReturn(true);
        when(metaAdsService.pauseCampaign(any(), eq("meta-ad-1"))).thenReturn(true);

        service.detectFatigue(campaignId);

        // Verify Fatigue Alert is saved
        verify(fatigueAlertRepo, times(2)).save(any(FatigueAlert.class));
        // Verify creative rotation activated replacement
        verify(creativeRepo, times(1)).save(argThat(c -> "creative-2".equals(c.getId()) && "ACTIVE".equals(c.getStatus())));
        // Verify old creative paused
        verify(creativeRepo, times(1)).save(argThat(c -> "creative-1".equals(c.getId()) && "PAUSED".equals(c.getStatus())));
        // Verify decision logged
        verify(decisionRepo, times(1)).save(any(BrainDecision.class));
    }

    @Test
    public void testHandleFatigueAlertBlockedInLocalModeBeforeMetaOrCreativeMutation() {
        String campaignId = "camp-123";
        String accountId = "acc-456";

        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setAccountId(accountId);
        campaign.setName("Test Campaign");
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(localApprovalSafetyService.shouldRequireApprovalOnly("CREATIVE_FATIGUE_ROTATION")).thenReturn(true);

        FatigueAlert alert = new FatigueAlert();
        alert.setId("alert-123");
        alert.setCampaignId(campaignId);
        alert.setCreativeId("creative-1");

        service.handleFatigueAlert(alert);

        verify(localApprovalSafetyService).auditBlockedExecution(
                eq(accountId),
                eq("CREATIVE_FATIGUE_ROTATION"),
                eq("FatigueAlert"),
                eq("alert-123"),
                contains("blocked before Meta pause/resume")
        );
        verify(creativeRepo, never()).findByCampaignIdAndStatus(anyString(), anyString());
        verify(metaConnRepo, never()).findFirstByAccountIdAndTokenStatus(anyString(), anyString());
        verify(metaAdsService, never()).resumeCampaign(any(), anyString());
        verify(metaAdsService, never()).pauseCampaign(any(), anyString());
        verify(creativeRepo, never()).save(any());
        verify(decisionRepo, never()).save(any());
    }
}
