package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.mathengine.dto.CampaignMathEvaluationResponse;
import com.chubby.dolphin.repository.*;
import com.chubby.dolphin.security.AccessControlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignMathEvaluationService {

    private final CampaignRepository campaignRepository;
    private final MetricSnapshotRepository metricSnapshotRepository;
    private final WalletRepository walletRepository;
    private final LeadRepository leadRepository;
    private final WorkspaceConfigRepository workspaceConfigRepository;
    private final CampaignMathEvaluationRepository evaluationRepository;
    private final CampaignPerformanceScoreEngine performanceScoreEngine;
    private final WalletSafetyEngine walletSafetyEngine;
    private final CplThresholdEngine cplThresholdEngine;
    private final FortyEightHourKillRuleEngine killRuleEngine;
    private final CreativeFatigueEngine creativeFatigueEngine;
    private final GrowthOpportunityEngine growthOpportunityEngine;
    private final RiskEngine riskEngine;
    private final AccessControlService access;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<CampaignMathEvaluationResponse> evaluateCampaign(UUID campaignId) {
        String workspaceId = access.currentWorkspaceId();
        Campaign campaign = campaignRepository.findByIdAndWorkspaceId(campaignId.toString(), workspaceId)
                .orElseThrow(() -> new MathEvaluationNotFoundException("Campaign not found."));
        return evaluateCampaignInternal(campaign, null).stream().map(CampaignMathEvaluationResponse::from).toList();
    }

    @Transactional
    public List<CampaignMathEvaluationResponse> evaluateWorkspace(UUID workspaceId) {
        access.requireSameWorkspace(workspaceId.toString());
        List<Campaign> campaigns = campaignRepository.findByWorkspaceId(workspaceId.toString());
        List<CampaignMathEvaluation> saved = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            saved.addAll(evaluateCampaignInternal(campaign, null));
        }
        return saved.stream().map(CampaignMathEvaluationResponse::from).toList();
    }

    @Transactional
    public List<CampaignMathEvaluationResponse> evaluateActiveCampaignsForCurrentWorkspace() {
        return evaluateActiveCampaignsForCurrentWorkspace(null);
    }

    @Transactional
    public List<CampaignMathEvaluationResponse> evaluateActiveCampaignsForCurrentWorkspace(UUID runId) {
        String workspaceId = access.currentWorkspaceId();
        List<Campaign> campaigns = campaignRepository.findByWorkspaceIdAndStatus(workspaceId, "ACTIVE");
        List<CampaignMathEvaluation> saved = new ArrayList<>();
        for (Campaign campaign : campaigns) {
            saved.addAll(evaluateCampaignInternal(campaign, runId));
        }
        return saved.stream().map(CampaignMathEvaluationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignMathEvaluationResponse> getLatestEvaluationsForCampaign(UUID campaignId) {
        UUID workspaceId = uuid(access.currentWorkspaceId(), "workspace context");
        return evaluationRepository.findTop20ByWorkspaceIdAndCampaignIdOrderByCreatedAtDesc(workspaceId, campaignId)
                .stream()
                .map(CampaignMathEvaluationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignMathEvaluationResponse> getLatestWorkspaceSignals(UUID workspaceId) {
        access.requireSameWorkspace(workspaceId.toString());
        return evaluationRepository.findTop50ByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(CampaignMathEvaluationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignMathEvaluationResponse> getLatestEvaluationsForCurrentWorkspace() {
        UUID workspaceId = uuid(access.currentWorkspaceId(), "workspace context");
        return evaluationRepository.findTop50ByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(CampaignMathEvaluationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignMathEvaluationResponse> getEvaluationsForRun(UUID runId) {
        UUID workspaceId = uuid(access.currentWorkspaceId(), "workspace context");
        return evaluationRepository.findTop50ByWorkspaceIdAndRunIdOrderByCreatedAtDesc(workspaceId, runId)
                .stream()
                .map(CampaignMathEvaluationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignMathEvaluationResponse getEvaluationById(UUID id) {
        UUID workspaceId = uuid(access.currentWorkspaceId(), "workspace context");
        return evaluationRepository.findByIdAndWorkspaceId(id, workspaceId)
                .map(CampaignMathEvaluationResponse::from)
                .orElseThrow(() -> new MathEvaluationNotFoundException("Math evaluation not found."));
    }

    @Transactional(readOnly = true)
    public CampaignMathEvaluationResponse getLatestPerformanceScoreForCampaign(UUID campaignId) {
        UUID workspaceId = uuid(access.currentWorkspaceId(), "workspace context");
        return evaluationRepository.findTop20ByWorkspaceIdAndCampaignIdOrderByCreatedAtDesc(workspaceId, campaignId)
                .stream()
                .filter(evaluation -> CampaignPerformanceScoreEngine.EVALUATION_TYPE.equals(evaluation.getEvaluationType()))
                .findFirst()
                .map(CampaignMathEvaluationResponse::from)
                .orElseThrow(() -> new MathEvaluationNotFoundException("No math score found for this campaign."));
    }

    private List<CampaignMathEvaluation> evaluateCampaignInternal(Campaign campaign, UUID runId) {
        String workspaceId = campaign.getWorkspaceId();
        UUID workspaceUuid = uuid(workspaceId, "workspace");
        UUID campaignUuid = uuid(campaign.getId(), "campaign");
        UUID organizationUuid = organizationUuid();
        WorkspaceConfig config = workspaceConfigRepository.findByWorkspaceId(workspaceId).orElse(null);
        Double targetCpl = config != null ? config.getTargetCpl() : null;
        MetricSnapshot latestSnapshot = latestSnapshot(campaign.getId());
        Wallet wallet = walletRepository.findFirstByWorkspaceId(workspaceId).orElse(null);
        List<Lead> leads = leadRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);

        List<CampaignMathEvaluation> previousCpl = evaluationRepository
                .findTop10ByWorkspaceIdAndCampaignIdAndEvaluationTypeOrderByCreatedAtDesc(
                        workspaceUuid, campaignUuid, CplThresholdEngine.EVALUATION_TYPE);

        List<MathSignal> signals = new ArrayList<>();
        MathSignal performance = performanceScoreEngine.evaluate(campaign, latestSnapshot, targetCpl);
        signals.add(performance);
        signals.add(walletSafetyEngine.evaluate(wallet, projectedSpendToday(workspaceId)));
        signals.add(cplThresholdEngine.evaluate(campaign, latestSnapshot, targetCpl, previousCpl));
        signals.add(killRuleEngine.evaluate(campaign, LocalDateTime.now()));
        signals.add(creativeFatigueEngine.evaluate(campaign, latestSnapshot));

        Double ctr = derivedCtr(campaign, latestSnapshot);
        Double cpc = latestSnapshot != null ? MathEngineUtils.cpc(latestSnapshot.getCpc(), latestSnapshot.getSpend(), latestSnapshot.getClicks()) : null;
        signals.addAll(growthOpportunityEngine.evaluate(campaign, targetCpl, performance.score()));
        signals.addAll(riskEngine.evaluate(campaign, wallet, leads, targetCpl, ctr, cpc));

        return signals.stream()
                .map(signal -> saveSignal(signal, organizationUuid, workspaceUuid, campaignUuid, runId))
                .toList();
    }

    private CampaignMathEvaluation saveSignal(MathSignal signal, UUID organizationId, UUID workspaceId, UUID campaignId, UUID runId) {
        CampaignMathEvaluation evaluation = new CampaignMathEvaluation();
        evaluation.setOrganizationId(organizationId);
        evaluation.setWorkspaceId(workspaceId);
        evaluation.setAccountId(workspaceId);
        evaluation.setCampaignId(campaignId);
        evaluation.setRunId(runId);
        evaluation.setEvaluationType(signal.evaluationType());
        evaluation.setStatus(signal.status());
        evaluation.setSeverity(signal.severity());
        evaluation.setActionType(signal.actionType());
        evaluation.setScore(signal.score());
        evaluation.setTitle(signal.title());
        evaluation.setDescription(signal.description());
        evaluation.setInputSnapshotJson(toJson(signal.inputSnapshot()));
        evaluation.setFormulaVersion(signal.formulaVersion());
        evaluation.setRequiresApproval(signal.requiresApproval());
        evaluation.setCreatedAt(LocalDateTime.now());
        return evaluationRepository.save(evaluation);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private MetricSnapshot latestSnapshot(String campaignId) {
        return metricSnapshotRepository.findByCampaignIdOrderBySnapshotDateDesc(campaignId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Double projectedSpendToday(String workspaceId) {
        return metricSnapshotRepository.findByAccountIdAndSnapshotDate(workspaceId, LocalDate.now())
                .stream()
                .mapToDouble(snapshot -> snapshot.getSpend() != null ? snapshot.getSpend() : 0.0)
                .sum();
    }

    private Double derivedCtr(Campaign campaign, MetricSnapshot snapshot) {
        return MathEngineUtils.ctrPercent(
                campaign != null ? campaign.getCtr() : snapshot != null ? snapshot.getCtr() : null,
                snapshot != null ? snapshot.getClicks() : null,
                snapshot != null ? snapshot.getImpressions() : null
        );
    }

    private UUID organizationUuid() {
        User user = access.currentUser();
        if (user != null && user.getOrganization() != null) {
            try {
                return UUID.fromString(user.getOrganization().getId());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private UUID uuid(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be a valid UUID.");
        }
        return UUID.fromString(value);
    }

    public static class MathEvaluationNotFoundException extends RuntimeException {
        public MathEvaluationNotFoundException(String message) {
            super(message);
        }
    }
}
