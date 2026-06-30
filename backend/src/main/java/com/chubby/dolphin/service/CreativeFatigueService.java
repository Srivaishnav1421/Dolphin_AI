package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Creative Fatigue Detection Engine.
 * Replaces human judgment in detecting when ad designs are wearing out
 * and automatically rotates in fresh high-predicted CTR draft variations.
 */
@Service
@Slf4j
@Transactional
public class CreativeFatigueService {

    private final AdCreativeRepository creativeRepo;
    private final MetricSnapshotRepository metricRepo;
    private final FatigueAlertRepository fatigueAlertRepo;
    private final CampaignRepository campaignRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final MetaAdsService metaAdsService;
    private final BrainDecisionRepository decisionRepo;
    private final AlertService alertService;
    private final LocalApprovalSafetyService localApprovalSafetyService;

    public CreativeFatigueService(AdCreativeRepository creativeRepo,
                                  MetricSnapshotRepository metricRepo,
                                  FatigueAlertRepository fatigueAlertRepo,
                                  CampaignRepository campaignRepo,
                                  MetaConnectionRepository metaConnRepo,
                                  MetaAdsService metaAdsService,
                                  BrainDecisionRepository decisionRepo,
                                  AlertService alertService) {
        this(creativeRepo, metricRepo, fatigueAlertRepo, campaignRepo, metaConnRepo,
                metaAdsService, decisionRepo, alertService, null);
    }

    @Autowired
    public CreativeFatigueService(AdCreativeRepository creativeRepo,
                                  MetricSnapshotRepository metricRepo,
                                  FatigueAlertRepository fatigueAlertRepo,
                                  CampaignRepository campaignRepo,
                                  MetaConnectionRepository metaConnRepo,
                                  MetaAdsService metaAdsService,
                                  BrainDecisionRepository decisionRepo,
                                  AlertService alertService,
                                  LocalApprovalSafetyService localApprovalSafetyService) {
        this.creativeRepo = creativeRepo;
        this.metricRepo = metricRepo;
        this.fatigueAlertRepo = fatigueAlertRepo;
        this.campaignRepo = campaignRepo;
        this.metaConnRepo = metaConnRepo;
        this.metaAdsService = metaAdsService;
        this.decisionRepo = decisionRepo;
        this.alertService = alertService;
        this.localApprovalSafetyService = localApprovalSafetyService;
    }

    /**
     * Detects creative fatigue on a specific campaign.
     * Evaluates active creatives against historic performance baselines.
     */
    public void detectFatigue(String campaignId) {
        log.info("🔍 Running Creative Fatigue Detection for campaign: {}", campaignId);

        Campaign campaign = campaignRepo.findById(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("Campaign {} not found, skipping fatigue check.", campaignId);
            return;
        }

        List<AdCreative> activeCreatives = creativeRepo.findByCampaignIdAndStatus(campaignId, "ACTIVE");
        if (activeCreatives.isEmpty()) {
            log.info("No active ad creatives found for campaign {}, skipping check.", campaignId);
            return;
        }

        // Fetch last 3 days of metric snapshots for baseline calculations
        LocalDate today = LocalDate.now();
        List<MetricSnapshot> last3Days = metricRepo.findByCampaignIdAndSnapshotDateBetween(
                campaignId, today.minusDays(3), today);

        if (last3Days.isEmpty()) {
            log.info("Insufficient historical metric snapshots for campaign {}, skipping baseline fatigue analysis.", campaignId);
            return;
        }

        // Compute baseline metrics across last 3 days
        double baselineCtr = last3Days.stream().mapToDouble(m -> m.getCtr() != null ? m.getCtr() : 0.0).average().orElse(0.0);
        double baselineFreq = last3Days.stream().mapToDouble(m -> m.getFrequency() != null ? m.getFrequency() : 0.0).average().orElse(0.0);
        
        // Calculate average CPM (Cost Per Mille): (Spend / Impressions) * 1000
        double totalSpend = last3Days.stream().mapToDouble(m -> m.getSpend() != null ? m.getSpend() : 0.0).sum();
        long totalImpressions = last3Days.stream().mapToLong(m -> m.getImpressions() != null ? m.getImpressions() : 0L).sum();
        double baselineCpm = totalImpressions > 0 ? (totalSpend / totalImpressions) * 1000.0 : 0.0;

        for (AdCreative creative : activeCreatives) {
            // Retrieve creative specific current metrics
            double currentCtr = creative.getActualCtr() != null ? creative.getActualCtr() : 0.0;
            long creativeImps = creative.getImpressions() != null ? creative.getImpressions() : 0L;
            double creativeSpend = creative.getSpend() != null ? creative.getSpend() : 0.0;
            double currentCpm = creativeImps > 0 ? (creativeSpend / creativeImps) * 1000.0 : 0.0;

            boolean fatigued = false;
            String fatigueType = null;

            // Rule 1: CTR Fatigue - current CTR drops below 80% of historical baseline
            if (baselineCtr > 0 && currentCtr < baselineCtr * 0.80) {
                fatigued = true;
                fatigueType = "CTR";
            }
            // Rule 2: Frequency Fatigue - average frequency saw by target audience goes above 4.0
            else if (baselineFreq > 4.0) {
                fatigued = true;
                fatigueType = "FREQUENCY";
            }
            // Rule 3: CPM Fatigue - cost per thousand impressions spikes > 25% over average
            else if (baselineCpm > 0 && currentCpm > baselineCpm * 1.25) {
                fatigued = true;
                fatigueType = "CPM";
            }

            if (fatigued) {
                log.warn("⚠️ FATIGUE DETECTED [type={}] for creative {} on campaign {}", fatigueType, creative.getId(), campaignId);

                FatigueAlert alert = new FatigueAlert();
                alert.setCampaignId(campaignId);
                alert.setCreativeId(creative.getId());
                alert.setWorkspaceId(campaign.getAccountId());
                alert.setFatigueType(fatigueType);
                alert.setBaselineCtr(baselineCtr);
                alert.setCurrentCtr(currentCtr);
                alert.setBaselineFreq(baselineFreq);
                // We use latest known frequency from snapshots
                alert.setCurrentFreq(last3Days.get(0).getFrequency());
                alert.setBaselineCpm(baselineCpm);
                alert.setCurrentCpm(currentCpm);
                alert.setDetectedAt(LocalDateTime.now());
                alert.setStatus("PENDING");

                fatigueAlertRepo.save(alert);

                // Auto-trigger rotation mechanism
                handleFatigueAlert(alert);
            }
        }
    }

    /**
     * Resolves a fatigue alert by finding a replacement draft and deploying it.
     */
    public void handleFatigueAlert(FatigueAlert alert) {
        log.info("🔄 Initiating Creative Rotation for campaign: {}", alert.getCampaignId());

        Campaign campaign = campaignRepo.findById(alert.getCampaignId()).orElse(null);
        if (campaign == null) return;
        if (localSafetyBlocks("CREATIVE_FATIGUE_ROTATION")) {
            localApprovalSafetyService.auditBlockedExecution(
                    campaign.getAccountId(),
                    "CREATIVE_FATIGUE_ROTATION",
                    "FatigueAlert",
                    alert.getId(),
                    "Creative fatigue rotation blocked before Meta pause/resume and local creative mutations."
            );
            return;
        }

        // Find next DRAFT creative with highest predicted CTR
        List<AdCreative> drafts = creativeRepo.findByCampaignIdAndStatus(alert.getCampaignId(), "DRAFT");
        Optional<AdCreative> replacementOpt = drafts.stream()
                .filter(c -> c.getPredictedCtr() != null)
                .max(Comparator.comparingDouble(AdCreative::getPredictedCtr));

        if (replacementOpt.isPresent()) {
            AdCreative replacement = replacementOpt.get();
            log.info("🎯 Found ideal replacement creative: {} (Predicted CTR: {})", replacement.getId(), replacement.getPredictedCtr());

            // Active connection check
            MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(campaign.getAccountId(), "VALID").orElse(null);
            if (conn != null && replacement.getMetaAdId() != null) {
                // Execute rotation: activate replacement, pause fatigued creative
                boolean activated = metaAdsService.resumeCampaign(conn, replacement.getMetaAdId());
                if (activated) {
                    replacement.setStatus("ACTIVE");
                    replacement.setUpdatedAt(LocalDateTime.now());
                    creativeRepo.save(replacement);

                    // Pause old fatigued creative
                    AdCreative fatiguedCreative = creativeRepo.findById(alert.getCreativeId()).orElse(null);
                    if (fatiguedCreative != null) {
                        metaAdsService.pauseCampaign(conn, fatiguedCreative.getMetaAdId());
                        fatiguedCreative.setStatus("PAUSED");
                        fatiguedCreative.setUpdatedAt(LocalDateTime.now());
                        creativeRepo.save(fatiguedCreative);
                    }

                    alert.setStatus("ROTATED");
                    alert.setResolvedAt(LocalDateTime.now());
                    fatigueAlertRepo.save(alert);

                    // Record the AI Decision
                    BrainDecision decision = new BrainDecision();
                    decision.setAccountId(campaign.getAccountId());
                    decision.setCampaignId(campaign.getId());
                    decision.setCampaignName(campaign.getName());
                    decision.setDecisionType("CHANGE_CREATIVE");
                    decision.setAction("CREATIVE_ROTATION");
                    decision.setConfidence(0.95);
                    decision.setReason(String.format("Rotated creative %s to %s due to %s fatigue.",
                            alert.getCreativeId(), replacement.getId(), alert.getFatigueType()));
                    decision.setStatus("AUTO_EXECUTED");
                    decisionRepo.save(decision);

                    log.info("✅ Creative rotation executed successfully.");
                    return;
                }
            }
        }

        // If no replacement found or execution failed, notify workspace owner
        alertService.notifyReportReady(
                campaign.getAccountId(),
                "Creative Fatigue Warning - Campaign: " + campaign.getName(),
                LocalDate.now().minusDays(3).toString(),
                LocalDate.now().toString()
        );
    }

    private boolean localSafetyBlocks(String action) {
        return localApprovalSafetyService != null && localApprovalSafetyService.shouldRequireApprovalOnly(action);
    }
}
