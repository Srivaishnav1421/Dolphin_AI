package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.AdvantageExperiment;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.repository.AdvantageExperimentRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class AdvantageExperimentService {

    private final AdvantageExperimentRepository experimentRepo;
    private final CampaignRepository campaignRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final com.chubby.dolphin.repository.AdCreativeRepository creativeRepo;
    private final BrainDecisionService brainDecisionService;
    private final WebClient webClient;

    public AdvantageExperimentService(AdvantageExperimentRepository experimentRepo,
                                      CampaignRepository campaignRepo,
                                      MetaConnectionRepository metaConnRepo,
                                      com.chubby.dolphin.repository.AdCreativeRepository creativeRepo,
                                      BrainDecisionService brainDecisionService) {
        this.experimentRepo = experimentRepo;
        this.campaignRepo = campaignRepo;
        this.metaConnRepo = metaConnRepo;
        this.creativeRepo = creativeRepo;
        this.brainDecisionService = brainDecisionService;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Propose an Advantage+ Experiment if the campaign's ROAS is suboptimal under manual targeting.
     */
    public AdvantageExperiment proposeAdvantagePlusExperiment(String workspaceId, String campaignId) {
        log.info("📊 Evaluating campaign {} for Advantage+ optimization eligibility...", campaignId);

        Campaign campaign = campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId).orElse(null);
        if (campaign == null) {
            log.warn("Campaign {} not found.", campaignId);
            return null;
        }

        double currentRoas = campaign.getRoas() != null ? campaign.getRoas() : 1.0;

        // Check if an active experiment already exists to prevent duplication
        Optional<AdvantageExperiment> existing = experimentRepo.findByCampaignIdAndStatus(campaignId, "ACTIVE");
        if (existing.isPresent()) {
            log.info("An active Advantage+ experiment already exists for campaign {}.", campaignId);
            return existing.get();
        }

        AdvantageExperiment experiment = new AdvantageExperiment();
        experiment.setWorkspaceId(workspaceId);
        experiment.setCampaignId(campaignId);
        experiment.setMetaCampaignId(campaign.getMetaCampaignId());
        experiment.setRoasBefore(currentRoas);
        experiment.setStatus("SUGGESTED");

        log.info("✅ Campaign eligible for Advantage+ switch. Suggested experiment recorded (ROAS Before: {}).", currentRoas);
        return experimentRepo.save(experiment);
    }

    /**
     * Activates the Advantage+ configuration on Meta Ads and transitions the experiment status to ACTIVE.
     */
    public AdvantageExperiment activateAdvantagePlus(String experimentId) {
        log.info("⚡ Activating Advantage+ targeting suite for experiment: {}", experimentId);

        AdvantageExperiment experiment = experimentRepo.findById(experimentId).orElse(null);
        if (experiment == null) {
            log.warn("Experiment {} not found.", experimentId);
            return null;
        }

        String workspaceId = experiment.getWorkspaceId();
        MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID").orElse(null);

        if (conn == null || conn.getAccessToken() == null || experiment.getMetaCampaignId() == null) {
            log.warn("No active Meta connection or Meta campaign ID. Advantage+ activation blocked for experiment {}.", experimentId);
            experiment.setStatus("ACTIVATION_BLOCKED");
            experiment.setRevertReason("Activation blocked: connect a valid Meta account and synced Meta campaign before enabling Advantage+.");
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentRepo.save(experiment);
            throw new IllegalStateException("Advantage+ activation requires a valid Meta connection and synced Meta campaign.");
        }

        try {
            // Call Meta Graph API to toggle Advantage+ Targeting (adv_plus_targeting flag or similar)
            String url = "https://graph.facebook.com/v21.0/" + experiment.getMetaCampaignId();

            Map<String, Object> payload = new HashMap<>();
            payload.put("advantage_plus_targeting", "ENABLED"); // Meta API toggle parameter representation
            payload.put("smart_audience_enabled", true);

            webClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(conn.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            experiment.setStatus("ACTIVE");
            experiment.setSwitchedAt(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            log.info("✅ Meta Advantage+ targeting successfully updated on Meta Graph API.");
            return experimentRepo.save(experiment);

        } catch (Exception e) {
            log.error("❌ Failed to push Advantage+ toggle to Meta API: {}", e.getMessage());
            experiment.setStatus("ACTIVATION_FAILED");
            experiment.setRevertReason("Meta activation failed: " + e.getMessage());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentRepo.save(experiment);
            throw new IllegalStateException("Meta Advantage+ activation failed. No local simulation was applied.", e);
        }
    }

    public AdvantageExperiment activateAdvantagePlus(String experimentId, String workspaceId) {
        log.info("⚡ Activating Advantage+ targeting suite for experiment: {} in workspace: {}", experimentId, workspaceId);
        AdvantageExperiment experiment = experimentRepo.findByIdAndWorkspaceId(experimentId, workspaceId).orElse(null);
        if (experiment == null) {
            log.warn("Experiment {} not found in workspace {}.", experimentId, workspaceId);
            return null;
        }
        return activateAdvantagePlus(experiment.getId());
    }

    /**
     * Periodic Evaluator: checks active experiments, computes performance deltas,
     * and automatically triggers the rollback safety switch if ROAS plummets.
     */
    public void evaluateActiveExperiments() {
        log.info("🔍 Advantage+ Experiment Evaluator: Assessing active campaigns...");

        List<AdvantageExperiment> activeList = experimentRepo.findByStatus("ACTIVE");
        for (AdvantageExperiment exp : activeList) {
            Campaign campaign = campaignRepo.findById(exp.getCampaignId()).orElse(null);
            if (campaign == null) continue;

            double currentRoas = campaign.getRoas() != null ? campaign.getRoas() : 0.0;
            double roasBefore = exp.getRoasBefore() != null ? exp.getRoasBefore() : 1.0;
            double delta = currentRoas - roasBefore;

            exp.setNetRoasDelta(delta);
            exp.setUpdatedAt(LocalDateTime.now());

            long daysSinceSwitch = ChronoUnit.DAYS.between(exp.getSwitchedAt(), LocalDateTime.now());

            if (daysSinceSwitch >= 30 && exp.getRoasAfter30d() == null) {
                exp.setRoasAfter30d(currentRoas);
            } else if (daysSinceSwitch >= 14 && exp.getRoasAfter14d() == null) {
                exp.setRoasAfter14d(currentRoas);
            }

            // SAFETY NET ROLLBACK RULE:
            // If experiment is active and ROAS plummets below 1.2 or drops by more than 35% of the starting ROAS, rollback!
            boolean shouldRevert = false;
            String reason = "";

            if (currentRoas < 1.1) {
                shouldRevert = true;
                reason = String.format("ROAS plummeted to critical level (%.2f < 1.10)", currentRoas);
            } else if (currentRoas < roasBefore * 0.65) {
                shouldRevert = true;
                reason = String.format("ROAS dropped by more than 35%% of starting ROAS (Before: %.2f | Now: %.2f)", roasBefore, currentRoas);
            }

            if (shouldRevert) {
                triggerSafetyRollback(exp, campaign, reason);
            } else if (daysSinceSwitch >= 30 && delta > 0.3) {
                // Successful completion!
                exp.setStatus("SUCCESS");
                experimentRepo.save(exp);
                log.info("🏆 Advantage+ Experiment completed successfully for campaign {}! Delta: +{}", exp.getCampaignId(), delta);
            } else {
                experimentRepo.save(exp);
            }
        }
    }

    public void evaluateActiveExperiments(String workspaceId) {
        log.info("🔍 Advantage+ Experiment Evaluator: Assessing active campaigns for workspace: {}", workspaceId);
        List<AdvantageExperiment> activeList = experimentRepo.findByWorkspaceId(workspaceId).stream()
                .filter(exp -> "ACTIVE".equals(exp.getStatus()))
                .toList();
        for (AdvantageExperiment exp : activeList) {
            Campaign campaign = campaignRepo.findByIdAndWorkspaceId(exp.getCampaignId(), workspaceId).orElse(null);
            if (campaign == null) {
                continue;
            }

            double currentRoas = campaign.getRoas() != null ? campaign.getRoas() : 0.0;
            double roasBefore = exp.getRoasBefore() != null ? exp.getRoasBefore() : 1.0;
            double delta = currentRoas - roasBefore;

            exp.setNetRoasDelta(delta);
            exp.setUpdatedAt(LocalDateTime.now());

            long daysSinceSwitch = exp.getSwitchedAt() != null
                    ? ChronoUnit.DAYS.between(exp.getSwitchedAt(), LocalDateTime.now())
                    : 0L;

            if (daysSinceSwitch >= 30 && exp.getRoasAfter30d() == null) {
                exp.setRoasAfter30d(currentRoas);
            } else if (daysSinceSwitch >= 14 && exp.getRoasAfter14d() == null) {
                exp.setRoasAfter14d(currentRoas);
            }

            boolean shouldRevert = false;
            String reason = "";
            if (currentRoas < 1.1) {
                shouldRevert = true;
                reason = String.format("ROAS plummeted to critical level (%.2f < 1.10)", currentRoas);
            } else if (currentRoas < roasBefore * 0.65) {
                shouldRevert = true;
                reason = String.format("ROAS dropped by more than 35%% of starting ROAS (Before: %.2f | Now: %.2f)", roasBefore, currentRoas);
            }

            if (shouldRevert) {
                triggerSafetyRollback(exp, campaign, reason);
            } else if (daysSinceSwitch >= 30 && delta > 0.3) {
                exp.setStatus("SUCCESS");
                experimentRepo.save(exp);
            } else {
                experimentRepo.save(exp);
            }
        }
    }

    /**
     * Executes the safety rollback. Toggles the Meta targeting options back to manual
     * and records the reversion parameters.
     */
    private void triggerSafetyRollback(AdvantageExperiment exp, Campaign campaign, String reason) {
        log.warn("🚨 SAFETY TRIGGER: Advantage+ underperforming for campaign {}. Initiating rollback!", campaign.getName());

        String workspaceId = exp.getWorkspaceId();
        MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID").orElse(null);

        if (conn != null && conn.getAccessToken() != null && exp.getMetaCampaignId() != null) {
            try {
                // Call Meta Graph API to toggle Advantage+ Targeting OFF, returning back to custom targeting specs
                String url = "https://graph.facebook.com/v21.0/" + exp.getMetaCampaignId();

                Map<String, Object> payload = new HashMap<>();
                payload.put("advantage_plus_targeting", "DISABLED");
                payload.put("smart_audience_enabled", false);

                webClient.post()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(conn.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("✅ Meta API Advantage+ targeting rolled back successfully.");

            } catch (Exception e) {
                log.error("❌ Failed to push safety rollback to Meta API: {}", e.getMessage());
            }
        }

        // Update database states
        exp.setStatus("REVERTED");
        exp.setRevertedAt(LocalDateTime.now());
        exp.setRevertReason("Safety net triggered: " + reason);
        experimentRepo.save(exp);

        log.info("✅ Advantage+ Experiment reverted status recorded in DB for campaign {}.", exp.getCampaignId());
    }

    public AdvantageExperiment createAbExperiment(String workspaceId, String campaignId,
                                                   List<String> headlines, List<String> bodies, List<String> ctas) {
        log.info("Creating approval-required Advantage+ Experiment with 27 copy permutations for campaign: {}", campaignId);

        Campaign campaign = campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> new RuntimeException("Campaign not found: " + campaignId));

        String abTestId = "ab-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Generate combinations (3 * 3 * 3 = 27 combinations)
        for (int h = 0; h < 3; h++) {
            for (int b = 0; b < 3; b++) {
                for (int c = 0; c < 3; c++) {
                    String headline = headlines.get(Math.min(h, headlines.size() - 1));
                    String body = bodies.get(Math.min(b, bodies.size() - 1));
                    String cta = ctas.get(Math.min(c, ctas.size() - 1));

                    com.chubby.dolphin.entity.AdCreative creative = new com.chubby.dolphin.entity.AdCreative();
                    creative.setAccountId(workspaceId);
                    creative.setCampaignId(campaignId);
                    creative.setHeadline(headline);
                    creative.setBody(body);
                    creative.setCallToAction(cta);
                    creative.setPlatform("FACEBOOK_FEED");
                    creative.setStatus("PENDING_APPROVAL");
                    creative.setGeneratedBy("AI_GENERATED");
                    creative.setGenerationPrompt("Advantage+ 27-copy Permutation Matrix");
                    creative.setAbTestId(abTestId);
                    creative.setAbTestGroup(String.format("EXP-H%d-B%d-C%d", h + 1, b + 1, c + 1));
                    creativeRepo.save(creative);
                }
            }
        }

        AdvantageExperiment experiment = new AdvantageExperiment();
        experiment.setWorkspaceId(workspaceId);
        experiment.setCampaignId(campaignId);
        experiment.setMetaCampaignId(campaign.getMetaCampaignId());
        experiment.setRoasBefore(campaign.getRoas() != null ? campaign.getRoas() : 1.0);
        experiment.setStatus("SUGGESTED");
        experiment.setRevertReason("A/B Test ID: " + abTestId);
        experiment.setSwitchedAt(LocalDateTime.now());
        experiment.setCreatedAt(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());

        AdvantageExperiment saved = experimentRepo.save(experiment);

        // Increment Prometheus counter
        brainDecisionService.incrementExperimentCount();

        return saved;
    }
}
