package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.TenantAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampaignMathEvaluationServiceTest {

    private CampaignRepository campaignRepository;
    private MetricSnapshotRepository metricSnapshotRepository;
    private WalletRepository walletRepository;
    private LeadRepository leadRepository;
    private WorkspaceConfigRepository workspaceConfigRepository;
    private CampaignMathEvaluationRepository evaluationRepository;
    private AccessControlService access;
    private CampaignMathEvaluationService service;
    private UUID workspaceId;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        metricSnapshotRepository = mock(MetricSnapshotRepository.class);
        walletRepository = mock(WalletRepository.class);
        leadRepository = mock(LeadRepository.class);
        workspaceConfigRepository = mock(WorkspaceConfigRepository.class);
        evaluationRepository = mock(CampaignMathEvaluationRepository.class);
        access = mock(AccessControlService.class);
        workspaceId = UUID.randomUUID();
        campaignId = UUID.randomUUID();

        CampaignPerformanceScoreEngine performance = new CampaignPerformanceScoreEngine();
        WalletSafetyEngine wallet = new WalletSafetyEngine();
        CplThresholdEngine cpl = new CplThresholdEngine();
        FortyEightHourKillRuleEngine kill = new FortyEightHourKillRuleEngine();
        CreativeFatigueEngine fatigue = new CreativeFatigueEngine();
        GrowthOpportunityEngine opportunity = new GrowthOpportunityEngine();
        RiskEngine risk = new RiskEngine();
        org.springframework.test.util.ReflectionTestUtils.setField(performance, "defaultTargetCpl", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(wallet, "lowThreshold", 1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(wallet, "criticalThreshold", 0.0);
        org.springframework.test.util.ReflectionTestUtils.setField(wallet, "dailySpendCap", 5000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(cpl, "defaultTargetCpl", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(cpl, "noLeadSpendThreshold", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(kill, "minimumSpend", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(kill, "hours", 48L);
        org.springframework.test.util.ReflectionTestUtils.setField(fatigue, "lowCtrPercent", 0.5);
        org.springframework.test.util.ReflectionTestUtils.setField(fatigue, "highCpc", 125.0);
        org.springframework.test.util.ReflectionTestUtils.setField(fatigue, "clicksNoConversionThreshold", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(opportunity, "defaultTargetCpl", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(opportunity, "conversionRateThreshold", 5.0);
        org.springframework.test.util.ReflectionTestUtils.setField(opportunity, "minimumSpend", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(risk, "defaultTargetCpl", 500.0);
        org.springframework.test.util.ReflectionTestUtils.setField(risk, "lowWalletThreshold", 1000.0);
        org.springframework.test.util.ReflectionTestUtils.setField(risk, "noRecentLeadsDays", 7L);

        service = new CampaignMathEvaluationService(
                campaignRepository,
                metricSnapshotRepository,
                walletRepository,
                leadRepository,
                workspaceConfigRepository,
                evaluationRepository,
                performance,
                wallet,
                cpl,
                kill,
                fatigue,
                opportunity,
                risk,
                access,
                new ObjectMapper()
        );

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(access.currentWorkspaceId()).thenReturn(workspaceId.toString());
        when(access.currentUser()).thenReturn(user);
        when(workspaceConfigRepository.findByWorkspaceId(workspaceId.toString())).thenReturn(Optional.empty());
        when(walletRepository.findFirstByWorkspaceId(workspaceId.toString())).thenReturn(Optional.of(wallet(5000.0)));
        when(leadRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId.toString())).thenReturn(List.of());
        when(metricSnapshotRepository.findByCampaignIdOrderBySnapshotDateDesc(campaignId.toString())).thenReturn(List.of());
        when(metricSnapshotRepository.findByAccountIdAndSnapshotDate(eq(workspaceId.toString()), any(LocalDate.class))).thenReturn(List.of());
        when(evaluationRepository.findTop10ByWorkspaceIdAndCampaignIdAndEvaluationTypeOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(evaluationRepository.save(any(CampaignMathEvaluation.class))).thenAnswer(inv -> {
            CampaignMathEvaluation saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void evaluationSnapshotSavedWithFormulaVersionAndScope() {
        Campaign campaign = campaign();
        campaign.setCtr(1.0);
        campaign.setCpl(250.0);
        campaign.setSpent(1000.0);
        campaign.setConversions(5);
        campaign.setCreatedAt(LocalDateTime.now().minusHours(10));
        when(campaignRepository.findByIdAndWorkspaceId(campaignId.toString(), workspaceId.toString()))
                .thenReturn(Optional.of(campaign));

        var responses = service.evaluateCampaign(campaignId);

        assertFalse(responses.isEmpty());
        verify(evaluationRepository, atLeastOnce()).save(argThat(e ->
                workspaceId.equals(e.getWorkspaceId())
                        && campaignId.equals(e.getCampaignId())
                        && e.getInputSnapshotJson() != null
                        && !e.getInputSnapshotJson().isBlank()
                        && e.getFormulaVersion() != null
        ));
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void emptyWorkspaceEvaluationReturnsEmptyListWithoutFakeSignals() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE")).thenReturn(List.of());

        var responses = service.evaluateActiveCampaignsForCurrentWorkspace();

        assertTrue(responses.isEmpty());
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void adBrainRunIdIsPersistedOnEvaluationRows() {
        UUID runId = UUID.randomUUID();
        Campaign campaign = campaign();
        campaign.setCtr(1.0);
        campaign.setCpl(250.0);
        campaign.setSpent(1000.0);
        campaign.setConversions(5);
        campaign.setCreatedAt(LocalDateTime.now().minusHours(10));
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign));

        var responses = service.evaluateActiveCampaignsForCurrentWorkspace(runId);

        assertFalse(responses.isEmpty());
        assertTrue(responses.stream().allMatch(response -> runId.toString().equals(response.runId())));
        verify(evaluationRepository, atLeastOnce()).save(argThat(e -> runId.equals(e.getRunId())));
    }

    @Test
    void latestEvaluationsAreScopedToCurrentWorkspace() {
        UUID otherCampaign = UUID.randomUUID();
        when(evaluationRepository.findTop20ByWorkspaceIdAndCampaignIdOrderByCreatedAtDesc(workspaceId, otherCampaign))
                .thenReturn(List.of());

        assertTrue(service.getLatestEvaluationsForCampaign(otherCampaign).isEmpty());
        verify(evaluationRepository).findTop20ByWorkspaceIdAndCampaignIdOrderByCreatedAtDesc(workspaceId, otherCampaign);
    }

    @Test
    void crossWorkspaceEvaluationIsBlocked() {
        UUID otherWorkspace = UUID.randomUUID();
        doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireSameWorkspace(otherWorkspace.toString());

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> service.evaluateWorkspace(otherWorkspace));
    }

    private Campaign campaign() {
        Campaign c = new Campaign();
        c.setId(campaignId.toString());
        c.setWorkspaceId(workspaceId.toString());
        c.setName("Persistence Campaign");
        c.setStatus("ACTIVE");
        c.setObjective("LEADS");
        return c;
    }

    private Wallet wallet(double balance) {
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID().toString());
        wallet.setAccountId(workspaceId.toString());
        wallet.setBalance(balance);
        wallet.setDailyBudgetLimit(10000.0);
        return wallet;
    }
}
