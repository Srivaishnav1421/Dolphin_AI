package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.approval.*;
import com.chubby.dolphin.adbrain.dto.AdBrainRunResultDto;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.BrainEvent;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.mathengine.*;
import com.chubby.dolphin.mathengine.dto.CampaignMathEvaluationResponse;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.security.AccessControlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdBrainRunServiceTest {

    private CampaignRepository campaignRepository;
    private CampaignMathEvaluationRepository mathEvaluationRepository;
    private com.chubby.dolphin.mathengine.CampaignMathEvaluationService mathEvaluationService;
    private AdBrainRunRepository runRepository;
    private ApprovalItemRepository approvalRepository;
    private AuditLogService auditLogService;
    private BrainEventRepository brainEventRepository;
    private AccessControlService access;
    private AdBrainRunService service;
    private UUID workspaceId;
    private UUID organizationId;
    private UUID userId;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        mathEvaluationRepository = mock(CampaignMathEvaluationRepository.class);
        mathEvaluationService = mock(com.chubby.dolphin.mathengine.CampaignMathEvaluationService.class);
        runRepository = mock(AdBrainRunRepository.class);
        approvalRepository = mock(ApprovalItemRepository.class);
        auditLogService = mock(AuditLogService.class);
        brainEventRepository = mock(BrainEventRepository.class);
        access = mock(AccessControlService.class);

        workspaceId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        campaignId = UUID.randomUUID();

        Organization org = new Organization();
        org.setId(organizationId.toString());
        User user = new User();
        user.setId(userId.toString());
        user.setEmail("owner@dolphin.ai");
        user.setOrganization(org);

        when(access.currentWorkspaceId()).thenReturn(workspaceId.toString());
        when(access.currentUser()).thenReturn(user);
        when(runRepository.save(any(AdBrainRunSummary.class))).thenAnswer(inv -> {
            AdBrainRunSummary run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
        when(approvalRepository.save(any(ApprovalItem.class))).thenAnswer(inv -> {
            ApprovalItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(auditLogService.redact(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(brainEventRepository.save(any(BrainEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AdBrainRecommendationMapper mapper = new AdBrainRecommendationMapper();
        ApprovalRequiredActionService approvalRequiredActionService = new ApprovalRequiredActionService(approvalRepository, auditLogService);
        AdBrainApprovalBridge bridge = new AdBrainApprovalBridge(approvalRepository, approvalRequiredActionService, mapper, new ObjectMapper());
        service = new AdBrainRunService(
                campaignRepository,
                mathEvaluationRepository,
                mathEvaluationService,
                runRepository,
                bridge,
                mapper,
                brainEventRepository,
                access
        );
    }

    @Test
    void manualRunCallsMathEngineAndStoresCompletedRun() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign()));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class)))
                .thenReturn(List.of(signal(MathEvaluationStatus.OK, MathSeverity.HIGH, MathActionType.KILL_CAMPAIGN, true)));

        AdBrainRunResultDto result = service.runCurrentWorkspace();

        assertEquals(AdBrainRunStatus.COMPLETED, result.status());
        assertEquals(1, result.campaignsEvaluated());
        assertEquals(1, result.evaluationsCreated());
        verify(mathEvaluationService).evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class));
        verify(runRepository, atLeast(2)).save(any(AdBrainRunSummary.class));
    }

    @Test
    void emptyWorkspaceReturnsCompletedRunWithNoApprovals() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE")).thenReturn(List.of());
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class))).thenReturn(List.of());

        AdBrainRunResultDto result = service.runCurrentWorkspace();

        assertEquals(AdBrainRunStatus.COMPLETED, result.status());
        assertEquals(0, result.campaignsEvaluated());
        assertEquals(0, result.approvalItemsCreated());
        verify(approvalRepository, never()).save(any());
    }

    @Test
    void highCplSignalCreatesChangeObjectiveApprovalWithMathSnapshot() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign()));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class)))
                .thenReturn(List.of(signal(MathEvaluationStatus.OK, MathSeverity.HIGH, MathActionType.CHANGE_OBJECTIVE, true)));

        AdBrainRunResultDto result = service.runCurrentWorkspace();

        assertEquals(1, result.approvalItemsCreated());
        verify(approvalRepository).save(argThat(item ->
                item.getSourceModule() == ApprovalSourceModule.AD_BRAIN
                        && item.getActionType() == ApprovalActionType.CHANGE_OBJECTIVE
                        && item.getStatus() == ApprovalStatus.PENDING
                        && item.getMathSnapshotJson() != null
                        && item.getMathSnapshotJson().contains("inputSnapshotJson")
        ));
        verify(auditLogService).record(any(), any(), eq(workspaceId.toString()),
                eq("APPROVAL_ITEM_CREATED"), eq("ApprovalItem"), anyString(), contains("new_status=PENDING"));
    }

    @Test
    void fortyEightHourKillSignalCreatesKillCampaignApproval() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign()));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class)))
                .thenReturn(List.of(signal(MathEvaluationStatus.OK, MathSeverity.CRITICAL, MathActionType.KILL_CAMPAIGN, true)));

        service.runCurrentWorkspace();

        verify(approvalRepository).save(argThat(item -> item.getActionType() == ApprovalActionType.KILL_CAMPAIGN));
    }

    @Test
    void notEnoughDataAndNoneActionDoNotCreateApprovals() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign()));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class))).thenReturn(List.of(
                signal(MathEvaluationStatus.NOT_ENOUGH_DATA, MathSeverity.INFO, MathActionType.CHANGE_BUDGET, true),
                signal(MathEvaluationStatus.OK, MathSeverity.LOW, MathActionType.NONE, true)
        ));

        AdBrainRunResultDto result = service.runCurrentWorkspace();

        assertEquals(0, result.approvalItemsCreated());
        verify(approvalRepository, never()).save(any());
    }

    @Test
    void existingPendingApprovalPreventsDuplicateApproval() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign()));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class)))
                .thenReturn(List.of(signal(MathEvaluationStatus.OK, MathSeverity.HIGH, MathActionType.CHANGE_OBJECTIVE, true)));
        when(approvalRepository.existsByWorkspaceIdAndSourceModuleAndSourceEntityIdAndActionTypeAndStatus(
                workspaceId, ApprovalSourceModule.AD_BRAIN, campaignId, ApprovalActionType.CHANGE_OBJECTIVE, ApprovalStatus.PENDING))
                .thenReturn(true);

        AdBrainRunResultDto result = service.runCurrentWorkspace();

        assertEquals(0, result.approvalItemsCreated());
        assertEquals(1, result.duplicateApprovalsSkipped());
        verify(approvalRepository, never()).save(any());
    }

    @Test
    void adBrainDoesNotMutateCampaigns() {
        Campaign campaign = campaign();
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE")).thenReturn(List.of(campaign));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class)))
                .thenReturn(List.of(signal(MathEvaluationStatus.OK, MathSeverity.HIGH, MathActionType.PAUSE_CAMPAIGN, true)));

        service.runCurrentWorkspace();

        assertEquals("ACTIVE", campaign.getStatus());
        assertEquals(1000.0, campaign.getBudget());
        assertEquals("LEADS", campaign.getObjective());
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void runCreatesBrainEvents() {
        when(campaignRepository.findByWorkspaceIdAndStatus(workspaceId.toString(), "ACTIVE"))
                .thenReturn(List.of(campaign()));
        when(mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(any(UUID.class)))
                .thenReturn(List.of(signal(MathEvaluationStatus.OK, MathSeverity.HIGH, MathActionType.KILL_CAMPAIGN, true)));

        service.runCurrentWorkspace();

        verify(brainEventRepository, atLeastOnce()).save(argThat(event ->
                event.getEventType().startsWith("AD_BRAIN_")
                        && workspaceId.toString().equals(event.getWorkspaceId())
        ));
    }

    @Test
    void latestStatusAndRunDetailsAreWorkspaceScoped() {
        UUID runId = UUID.randomUUID();
        AdBrainRunSummary run = new AdBrainRunSummary();
        run.setId(runId);
        run.setWorkspaceId(workspaceId);
        run.setStatus(AdBrainRunStatus.COMPLETED);
        when(runRepository.findTopByWorkspaceIdOrderByStartedAtDesc(workspaceId)).thenReturn(Optional.of(run));
        when(runRepository.findByIdAndWorkspaceId(runId, workspaceId)).thenReturn(Optional.of(run));

        assertEquals(runId.toString(), service.latestStatus().runId());
        assertEquals(runId.toString(), service.runById(runId).runId());
        verify(runRepository).findByIdAndWorkspaceId(runId, workspaceId);
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId(campaignId.toString());
        campaign.setWorkspaceId(workspaceId.toString());
        campaign.setName("B3 Campaign");
        campaign.setStatus("ACTIVE");
        campaign.setObjective("LEADS");
        campaign.setBudget(1000.0);
        return campaign;
    }

    private CampaignMathEvaluationResponse signal(
            MathEvaluationStatus status,
            MathSeverity severity,
            MathActionType actionType,
            boolean requiresApproval
    ) {
        return new CampaignMathEvaluationResponse(
                UUID.randomUUID().toString(),
                organizationId.toString(),
                workspaceId.toString(),
                workspaceId.toString(),
                campaignId.toString(),
                UUID.randomUUID().toString(),
                actionType == MathActionType.CHANGE_OBJECTIVE ? "CPL_THRESHOLD" : "FORTY_EIGHT_HOUR_KILL_RULE",
                status,
                severity,
                actionType,
                40.0,
                "B3 signal",
                "Deterministic B3 signal.",
                "{\"campaignId\":\"" + campaignId + "\"}",
                "b3-test-v1",
                requiresApproval,
                LocalDateTime.now()
        );
    }
}
