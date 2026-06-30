package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.adbrain.dto.AdBrainRunResultDto;
import com.chubby.dolphin.adbrain.dto.AdBrainSignalDto;
import com.chubby.dolphin.entity.BrainEvent;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.mathengine.CampaignMathEvaluation;
import com.chubby.dolphin.mathengine.CampaignMathEvaluationRepository;
import com.chubby.dolphin.mathengine.MathActionType;
import com.chubby.dolphin.mathengine.dto.CampaignMathEvaluationResponse;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdBrainRunService {

    private final CampaignRepository campaignRepository;
    private final CampaignMathEvaluationRepository mathEvaluationRepository;
    private final com.chubby.dolphin.mathengine.CampaignMathEvaluationService mathEvaluationService;
    private final AdBrainRunRepository runRepository;
    private final AdBrainApprovalBridge approvalBridge;
    private final AdBrainRecommendationMapper mapper;
    private final BrainEventRepository brainEventRepository;
    private final AccessControlService access;

    @Transactional
    public AdBrainRunResultDto runCurrentWorkspace() {
        String workspace = access.currentWorkspaceId();
        UUID workspaceId = requiredUuid(workspace, "workspace context");
        User actor = access.currentUser();
        UUID organizationId = uuid(actor != null && actor.getOrganization() != null ? actor.getOrganization().getId() : null);

        AdBrainRunSummary run = new AdBrainRunSummary();
        run.setWorkspaceId(workspaceId);
        run.setAccountId(workspaceId);
        run.setOrganizationId(organizationId);
        run.setCreatedBy(uuid(actor != null ? actor.getId() : null));
        run.setStatus(AdBrainRunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        run = runRepository.save(run);

        saveEvent(workspace, "AD_BRAIN_RUN_STARTED", "Ad Brain manual run started.", "INFO");

        try {
            List<Campaign> campaigns = campaignRepository.findByWorkspaceIdAndStatus(workspace, "ACTIVE");
            List<CampaignMathEvaluationResponse> signals = mathEvaluationService.evaluateActiveCampaignsForCurrentWorkspace(run.getId());

            int approvalsCreated = 0;
            int duplicateApprovals = 0;
            int risks = 0;
            int opportunities = 0;

            for (CampaignMathEvaluationResponse signal : signals) {
                if (mapper.isRisk(signal.severity())) {
                    risks++;
                    saveEvent(workspace, "AD_BRAIN_RISK_DETECTED", eventMessage(signal), "WARNING");
                }
                if (mapper.isOpportunity(signal.evaluationType(), signal.actionType())) {
                    opportunities++;
                    saveEvent(workspace, "AD_BRAIN_OPPORTUNITY_DETECTED", eventMessage(signal), "INFO");
                }

                AdBrainApprovalBridge.BridgeResult bridged = approvalBridge.createApprovalIfNeeded(signal, workspaceId, organizationId, actor);
                if (bridged.created()) {
                    approvalsCreated++;
                    saveEvent(workspace, "AD_BRAIN_APPROVAL_CREATED", "Approval item created for " + signal.actionType(), "SUCCESS");
                } else if (bridged.duplicate()) {
                    duplicateApprovals++;
                }
            }

            for (Campaign campaign : campaigns) {
                saveEvent(workspace, "AD_BRAIN_CAMPAIGN_EVALUATED", "Campaign evaluated: " + campaign.getName(), "INFO");
            }

            run.setCampaignsEvaluated(campaigns.size());
            run.setEvaluationsCreated(signals.size());
            run.setApprovalItemsCreated(approvalsCreated);
            run.setDuplicateApprovalsSkipped(duplicateApprovals);
            run.setRisksCreated(risks);
            run.setOpportunitiesCreated(opportunities);
            run.setStatus(AdBrainRunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            AdBrainRunSummary saved = runRepository.save(run);

            saveEvent(workspace, "AD_BRAIN_RUN_COMPLETED",
                    "Ad Brain completed. " + approvalsCreated + " approvals created, "
                            + duplicateApprovals + " duplicates skipped.", "SUCCESS");
            return AdBrainRunResultDto.from(saved);
        } catch (RuntimeException ex) {
            run.setStatus(AdBrainRunStatus.FAILED);
            run.setErrorMessage(clean(ex.getMessage()));
            run.setCompletedAt(LocalDateTime.now());
            AdBrainRunSummary failed = runRepository.save(run);
            saveEvent(workspace, "AD_BRAIN_RUN_FAILED", "Ad Brain failed. No actions were executed.", "CRITICAL");
            return AdBrainRunResultDto.from(failed);
        }
    }

    @Transactional(readOnly = true)
    public AdBrainRunResultDto latestStatus() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return runRepository.findTopByWorkspaceIdOrderByStartedAtDesc(workspaceId)
                .map(AdBrainRunResultDto::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AdBrainRunResultDto> recentRuns() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return runRepository.findTop20ByWorkspaceIdOrderByStartedAtDesc(workspaceId).stream()
                .map(AdBrainRunResultDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdBrainRunResultDto runById(UUID id) {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return runRepository.findByIdAndWorkspaceId(id, workspaceId)
                .map(AdBrainRunResultDto::from)
                .orElseThrow(() -> new AdBrainRunNotFoundException("Ad Brain run not found."));
    }

    @Transactional(readOnly = true)
    public List<AdBrainSignalDto> latestSignals() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return mathEvaluationRepository.findTop50ByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toSignal)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignMathEvaluationResponse> latestEvaluations() {
        return mathEvaluationService.getLatestEvaluationsForCurrentWorkspace();
    }

    @Transactional(readOnly = true)
    public List<CampaignMathEvaluationResponse> evaluationsForRun(UUID runId) {
        return mathEvaluationService.getEvaluationsForRun(runId);
    }

    @Transactional(readOnly = true)
    public CampaignMathEvaluationResponse evaluationById(UUID id) {
        return mathEvaluationService.getEvaluationById(id);
    }

    private AdBrainSignalDto toSignal(CampaignMathEvaluation evaluation) {
        return new AdBrainSignalDto(
                id(evaluation.getId()),
                id(evaluation.getCampaignId()),
                evaluation.getEvaluationType(),
                evaluation.getStatus(),
                evaluation.getSeverity(),
                evaluation.getActionType(),
                evaluation.getScore(),
                evaluation.getTitle(),
                evaluation.getDescription(),
                evaluation.getFormulaVersion(),
                evaluation.getRequiresApproval(),
                evaluation.getCreatedAt()
        );
    }

    private String eventMessage(CampaignMathEvaluationResponse signal) {
        MathActionType action = signal.actionType() == null ? MathActionType.NONE : signal.actionType();
        return signal.title() + " - " + action;
    }

    private BrainEvent saveEvent(String workspaceId, String type, String message, String severity) {
        BrainEvent event = new BrainEvent();
        event.setWorkspaceId(workspaceId);
        event.setEventType(type);
        event.setMessage(message);
        event.setSeverity(severity);
        event.setCreatedAt(LocalDateTime.now());
        return brainEventRepository.save(event);
    }

    private UUID requiredUuid(String value, String label) {
        UUID parsed = uuid(value);
        if (parsed == null) {
            throw new IllegalArgumentException(label + " must be a valid UUID.");
        }
        return parsed;
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String id(UUID value) {
        return value == null ? null : value.toString();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return cleaned.length() <= 2000 ? cleaned : cleaned.substring(0, 2000);
    }

    public static class AdBrainRunNotFoundException extends RuntimeException {
        public AdBrainRunNotFoundException(String message) {
            super(message);
        }
    }
}
