package com.chubby.dolphin.controller;

import com.chubby.dolphin.approval.ApprovalActionType;
import com.chubby.dolphin.approval.ApprovalSeverity;
import com.chubby.dolphin.approval.ApprovalSourceModule;
import com.chubby.dolphin.approval.ApprovalQueueService;
import com.chubby.dolphin.approval.dto.ApprovalItemCreateRequest;
import com.chubby.dolphin.approval.dto.ApprovalItemResponse;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.entity.LeadInteraction;
import com.chubby.dolphin.repository.LeadInteractionRepository;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.AlertService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.chubby.dolphin.service.MetaAdsService;
import com.chubby.dolphin.service.RateLimiterService;
import com.chubby.dolphin.service.ConversationalSdrService;
import com.chubby.dolphin.service.LeadScoringService;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.entity.LeadChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lead Controller — Upgraded to use LLM Router for multi-provider AI scoring.
 */
@RestController
@RequestMapping("/api/leads")
@Slf4j
public class LeadController {

    private final LeadRepository  repo;
    private final BusinessLlmFacadeService llmRouter;
    private final SecurityUtils   sec;
    private final RateLimiterService rateLimiter;
    private final AlertService    alertService;
    private final ObjectMapper    mapper;
    private final MetaConnectionRepository metaConnRepo;
    private final MetaAdsService metaAdsService;
    private final ConversationalSdrService sdrService;
    private final com.chubby.dolphin.service.LeadPipelineTrackingService leadPipelineTrackingService;
    private final LeadInteractionRepository interactionRepo;
    private final Environment environment;
    private final AccessControlService access;
    private final AuditLogService auditLogService;
    private final LeadScoringService leadScoringService;
    private final ApprovalQueueService approvalQueueService;
    private final LocalApprovalSafetyService localApprovalSafetyService;

    @Value("${meta.webhook.verify-token:chubby_dolphin_verify_2024}")
    private String metaWebhookVerifyToken;

    @Value("${meta.app.secret:}")
    private String metaAppSecret;

    public LeadController(LeadRepository repo,
                          BusinessLlmFacadeService llmRouter,
                          SecurityUtils sec,
                          RateLimiterService rateLimiter,
                          AlertService alertService,
                          ObjectMapper mapper,
                          MetaConnectionRepository metaConnRepo,
                          MetaAdsService metaAdsService,
                          ConversationalSdrService sdrService,
                          com.chubby.dolphin.service.LeadPipelineTrackingService leadPipelineTrackingService,
                          LeadInteractionRepository interactionRepo,
                          Environment environment,
                          AccessControlService access,
                          AuditLogService auditLogService,
                          LeadScoringService leadScoringService,
                          ApprovalQueueService approvalQueueService,
                          LocalApprovalSafetyService localApprovalSafetyService) {
        this.repo = repo;
        this.llmRouter = llmRouter;
        this.sec = sec;
        this.rateLimiter = rateLimiter;
        this.alertService = alertService;
        this.mapper = mapper;
        this.metaConnRepo = metaConnRepo;
        this.metaAdsService = metaAdsService;
        this.sdrService = sdrService;
        this.leadPipelineTrackingService = leadPipelineTrackingService;
        this.interactionRepo = interactionRepo;
        this.environment = environment;
        this.access = access;
        this.auditLogService = auditLogService;
        this.leadScoringService = leadScoringService;
        this.approvalQueueService = approvalQueueService;
        this.localApprovalSafetyService = localApprovalSafetyService;
    }

    /** List leads, optionally filter by status */
    @GetMapping
    public ResponseEntity<List<Lead>> list(@RequestParam(required = false) String status) {
        access.requireWorkspacePermission(Permission.LEAD_READ);
        String workspaceId = sec.currentWorkspaceId();
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(repo.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, validateStatus(status)));
        }
        return ResponseEntity.ok(repo.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lead> get(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_READ);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @PostMapping
    public ResponseEntity<Lead> create(@RequestBody Map<String, Object> body) {
        access.requireWorkspacePermission(Permission.LEAD_CREATE);
        String workspaceId = sec.currentWorkspaceId();

        Lead lead = new Lead();
        lead.setWorkspaceId(workspaceId);
        applyLeadFields(lead, body);
        if (lead.getName() == null || lead.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (lead.getMessage() == null || lead.getMessage().isBlank()) {
            lead.setMessage("Lead created without an enquiry message.");
        }
        lead.setCreatedAt(LocalDateTime.now());
        leadScoringService.applyScoreToNewLead(lead);

        Lead saved = repo.save(lead);
        auditLead("LEAD_CREATED", saved, "Lead created with deterministic Lead Score");
        leadPipelineTrackingService.recordLeadCreated(workspaceId, saved.getId(), "CRM lead saved in database.");
        leadPipelineTrackingService.recordLeadScored(workspaceId, saved.getId(), "Deterministic Lead Score=" + Math.round(saved.getScore() * 100));
        return ResponseEntity.status(201).body(saved);
    }

    /**
     * Score a new inbound lead using the LLM Router (Ollama → Gemini fallback).
     * Call this when a new lead comes from Meta/Instagram/WhatsApp.
     */
    @PostMapping("/score")
    public ResponseEntity<Lead> score(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.LEAD_SCORE);
        String workspaceId = sec.currentWorkspaceId();
        // Rate limit: 10 AI calls per minute per user
        if (!rateLimiter.isAllowed(workspaceId, RateLimiterService.LimitType.AI)) {
            return ResponseEntity.status(429).build();
        }

        String name = cleanString(body.getOrDefault("name", "Unknown"), 255);
        String message = cleanString(body.getOrDefault("message", ""), 2000);
        String source = cleanString(body.getOrDefault("source", "UNKNOWN"), 80);
        if (message == null) {
            return ResponseEntity.badRequest().build();
        }
        if (name == null) {
            name = "Unknown";
        }
        if (source == null) {
            source = "UNKNOWN";
        }

        // Call LLM Router (tries Ollama first, then Gemini)
        BusinessLlmFacadeService.LlmResponse aiResponse = llmRouter.scoreLead(message, source);
        log.info("🧠 Lead scored [via {}] '{}': {}", aiResponse.provider(), name, aiResponse.text());

        Lead lead = new Lead();
        lead.setWorkspaceId(workspaceId);
        lead.setName(name);
        lead.setMessage(message);
        lead.setSource(source);
        lead.setCreatedAt(LocalDateTime.now());

        try {
            Map<String, Object> parsed = mapper.readValue(aiResponse.text(), Map.class);
            lead.setScore(clamp(toDouble(parsed.get("score"), 0.5), 0.0, 1.0));
            lead.setTemperature(validateTemperature(cleanString(parsed.getOrDefault("temperature", "UNKNOWN"), 50)));
            lead.setBudgetSignal((String) parsed.getOrDefault("budget_signal", "—"));
            lead.setTimelineSignal((String) parsed.getOrDefault("timeline_signal", "—"));
            lead.setIntentSignal((String) parsed.getOrDefault("intent_signal", "—"));
            lead.setLocationSignal((String) parsed.getOrDefault("location_signal", "—"));
            lead.setGeminiAnalysis(aiResponse.text());
        } catch (Exception e) {
            log.warn("Could not parse AI response — using defaults: {}", e.getMessage());
            lead.setScore(0.5);
            lead.setTemperature("UNKNOWN");
            lead.setGeminiAnalysis("AI analysis unavailable. Lead was saved with default qualification.");
        }
        lead.setTemperature(validateTemperature(lead.getTemperature()));
        lead.setStatus("NEW");
        lead.setPipelineStage(stageForTemperature(lead.getTemperature()));
        lead.setConversionProbability(clamp(lead.getScore(), 0.0, 1.0));
        lead.setPriority(priorityForScore(lead.getScore()));
        lead.setNextBestAction(nextActionForTemperature(lead.getTemperature()));

        Lead saved = repo.save(lead);
        auditLead("LEAD_SCORED_CREATED", saved, "Lead scored and created");

        // Alert on HOT leads
        if ("HOT".equals(saved.getTemperature()) && saved.getScore() != null && saved.getScore() >= 0.7) {
            alertService.notifyHotLead(workspaceId, name, saved.getScore());
        }

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/score")
    public ResponseEntity<?> scoreExistingLead(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_SCORE);
        String workspaceId = sec.currentWorkspaceId();
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, workspaceId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Lead lead = opt.get();
        leadScoringService.refreshScoreWithoutStatusOverride(lead);
        Lead saved = repo.save(lead);
        auditLead("LEAD_SCORE_RECALCULATED", saved, "Deterministic Lead Score recalculated");
        leadPipelineTrackingService.recordLeadScored(workspaceId, saved.getId(), "Deterministic Lead Score=" + Math.round(saved.getScore() * 100));
        LeadScoringService.LeadScoreResult score = leadScoringService.score(saved);
        LeadScoringService.NextActionResult action = leadScoringService.recommend(saved);
        return ResponseEntity.ok(Map.of(
                "lead", saved,
                "score", score.score(),
                "temperature", score.temperature(),
                "score_breakdown", score.breakdown(),
                "score_breakdown_json", score.breakdownJson(),
                "next_action", action.action(),
                "action_type", action.actionType()
        ));
    }

    @PostMapping("/{id}/recommend-next-action")
    public ResponseEntity<?> recommendNextAction(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_READ);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead lead = opt.get();
        LeadScoringService.NextActionResult action = leadScoringService.recommend(lead);
        return ResponseEntity.ok(Map.of(
                "lead_id", lead.getId(),
                "next_action", action.action(),
                "action_type", action.actionType(),
                "score", action.score().score(),
                "temperature", action.score().temperature(),
                "score_breakdown", action.score().breakdown(),
                "score_breakdown_json", action.score().breakdownJson()
        ));
    }

    @PostMapping("/{id}/submit-followup-approval")
    public ResponseEntity<?> submitFollowupApproval(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_ACTIVITY_ADD);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead lead = opt.get();
        LeadScoringService.NextActionResult action = leadScoringService.recommend(lead);

        ApprovalItemResponse approval = approvalQueueService.createApprovalItem(new ApprovalItemCreateRequest(
                null,
                null,
                null,
                ApprovalSourceModule.SALES_CLOSER,
                "Lead",
                lead.getId(),
                approvalAction(action.actionType()),
                "Sales follow-up: " + safeTitle(lead.getName()),
                action.action(),
                recommendationJson(lead, action),
                mathSnapshotJson(lead, action),
                severity(action.score().score()),
                false,
                null
        ));

        auditLead("LEAD_FOLLOWUP_APPROVAL_SUBMITTED", lead, "Sales Closer follow-up approval submitted");
        return ResponseEntity.status(201).body(Map.of(
                "lead", lead,
                "approval", approval,
                "next_action", action.action(),
                "action_type", action.actionType()
        ));
    }

    /**
     * Webhook endpoint for Meta Lead Forms.
     * Meta sends lead data here automatically when someone fills a form.
     * This endpoint does NOT require authentication (webhook from Meta).
     */
    @PostMapping("/webhook/meta")
    public ResponseEntity<?> metaLeadWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            jakarta.servlet.http.HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        if (!rateLimiter.isAllowed(ipAddress, RateLimiterService.LimitType.WEBHOOK)) {
            log.warn("⚠️ Rate limit exceeded on Meta Lead Webhook from IP: {}", ipAddress);
            return ResponseEntity.status(429).body(Map.of("error", "Too many requests"));
        }
        try {
            log.info("📥 Meta Lead Webhook raw body received from IP: {}", ipAddress);

            if (signatureHeader == null || signatureHeader.isBlank()) {
                log.error("❌ Missing X-Hub-Signature-256 header received from Meta!");
                leadPipelineTrackingService.recordFailure(null, null, "WEBHOOK_RECEIVED", "Missing X-Hub-Signature-256 signature.");
                return ResponseEntity.status(401).body(Map.of("error", "Missing signature"));
            }

            if (!verifySignature(rawBody, signatureHeader)) {
                log.error("❌ Invalid X-Hub-Signature-256 received from Meta!");
                leadPipelineTrackingService.recordFailure(null, null, "WEBHOOK_RECEIVED", "Invalid X-Hub-Signature-256 signature.");
                return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
            }

            leadPipelineTrackingService.recordWebhookReceived(null, "Meta Leadgen Webhook received and signature verified.");

            JsonNode payload = mapper.readTree(rawBody);
            // Parse Meta Webhook changes
            JsonNode entryNode = payload.path("entry");
            if (entryNode.isArray()) {
                for (JsonNode entry : entryNode) {
                    String pageId = entry.path("id").asText();
                    JsonNode changesNode = entry.path("changes");
                    if (changesNode.isArray()) {
                        for (JsonNode change : changesNode) {
                            if ("leadgen".equals(change.path("field").asText())) {
                                JsonNode value = change.path("value");
                                String leadgenId = value.path("leadgen_id").asText();
                                String formId = value.path("form_id").asText();
                                log.info("🎯 Processing lead gen event: leadgen_id={}, form_id={}, page_id={}", leadgenId, formId, pageId);
                                
                                // Find correct connection by pageId. Never fall back to another workspace.
                                MetaConnection conn = metaConnRepo.findFirstByMetaPageIdAndTokenStatus(pageId, "VALID")
                                        .orElse(null);
                                
                                if (conn != null) {
                                    leadPipelineTrackingService.recordWorkspaceResolved(conn.getAccountId(), null, "Workspace resolved successfully for page ID: " + pageId);
                                    
                                    // Fetch full lead fields from Facebook API via MetaAdsService
                                    Map<String, String> details = metaAdsService.fetchLeadDetails(conn, leadgenId);
                                    
                                    String name = cleanString(details.getOrDefault("full_name", details.getOrDefault("name", "Meta Lead")), 255);
                                    String email = cleanString(details.getOrDefault("email", ""), 255);
                                    String phone = cleanString(details.getOrDefault("phone_number", ""), 50);
                                    if (name == null) name = "Meta Lead";
                                    String combinedMsg = String.format("Form ID: %s. Email: %s. Phone: %s. Lead details: %s",
                                            formId, email, phone, details.toString());
                                    
                                    // Score the lead using LLM Router
                                    BusinessLlmFacadeService.LlmResponse aiResponse = llmRouter.scoreLead(combinedMsg, "META_LEADGEN");
                                    log.info("🧠 Auto-scored lead '{}' [via {}]: {}", name, aiResponse.provider(), aiResponse.text());
                                    
                                    Lead lead = new Lead();
                                    lead.setAccountId(conn.getAccountId());
                                    lead.setName(name);
                                    lead.setEmail(email);
                                    lead.setPhone(phone);
                                    lead.setMessage(combinedMsg);
                                    lead.setSource("META_LEADGEN");
                                    lead.setCreatedAt(LocalDateTime.now());
                                    
                                    try {
                                        Map<String, Object> parsed = mapper.readValue(aiResponse.text(), Map.class);
                                        lead.setScore(clamp(toDouble(parsed.get("score"), 0.5), 0.0, 1.0));
                                        lead.setTemperature(validateTemperature(cleanString(parsed.getOrDefault("temperature", "UNKNOWN"), 50)));
                                        lead.setBudgetSignal((String) parsed.getOrDefault("budget_signal", "—"));
                                        lead.setTimelineSignal((String) parsed.getOrDefault("timeline_signal", "—"));
                                        lead.setIntentSignal((String) parsed.getOrDefault("intent_signal", "—"));
                                        lead.setLocationSignal((String) parsed.getOrDefault("location_signal", "—"));
                                        lead.setGeminiAnalysis(aiResponse.text());
                                    } catch (Exception e) {
                                        log.warn("Could not parse AI response: {}", e.getMessage());
                                        lead.setScore(0.5);
                                        lead.setTemperature("UNKNOWN");
                                        lead.setGeminiAnalysis(aiResponse.text());
                                    }
                                    lead.setTemperature(validateTemperature(lead.getTemperature()));
                                    lead.setStatus("NEW");
                                    lead.setPipelineStage(stageForTemperature(lead.getTemperature()));
                                    lead.setConversionProbability(clamp(lead.getScore(), 0.0, 1.0));
                                    lead.setPriority(priorityForScore(lead.getScore()));
                                    lead.setNextBestAction(nextActionForTemperature(lead.getTemperature()));

                                    Lead saved = repo.save(lead);
                                    leadPipelineTrackingService.recordLeadCreated(conn.getAccountId(), saved.getId(), "Lead saved to database: " + saved.getName());
                                    leadPipelineTrackingService.recordLeadScored(conn.getAccountId(), saved.getId(), "AI Lead scored with score: " + saved.getScore() + ", Temperature: " + saved.getTemperature());

                                    // Alert if HOT
                                    if ("HOT".equals(saved.getTemperature()) && saved.getScore() != null && saved.getScore() >= 0.7) {
                                        alertService.notifyHotLead(conn.getAccountId(), name, saved.getScore());
                                    }
                                } else {
                                    log.warn("⚠️ No active Meta connection found to fetch leadgen details for page_id: {}", pageId);
                                    leadPipelineTrackingService.recordFailure(null, null, "WORKSPACE_RESOLVED", "Failed to resolve workspace for page ID: " + pageId);
                                    return ResponseEntity.status(404).body(Map.of(
                                            "status", "unresolved_workspace",
                                            "message", "No active Meta connection found for this page ID."
                                    ));
                                }
                            }
                        }
                    }
                }
            }
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            log.error("Meta webhook handling failed: {}", e.getMessage(), e);
            leadPipelineTrackingService.recordFailure(null, null, "PIPELINE_FAILED", "Meta webhook ingestion execution error: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Meta webhook ingestion failed."));
        }
    }

    /** Meta webhook verification (GET request with verify_token) */
    @GetMapping("/webhook/meta")
    public ResponseEntity<?> verifyMetaWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && metaWebhookVerifyToken.equals(verifyToken)) {
            log.info("✅ Meta webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }
        log.warn("❌ Webhook verification failed. verifyToken received: {}", verifyToken);
        return ResponseEntity.status(403).build();
    }

    private boolean verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("❌ Webhook signature validation failed: signature header is missing.");
            return false;
        }
        if (!signatureHeader.startsWith("sha256=")) {
            log.warn("❌ Webhook signature validation failed: signature format is invalid.");
            return false;
        }
        try {
            if (metaAppSecret == null || metaAppSecret.isBlank()) {
                log.error("Meta app secret is not configured. Rejecting Meta lead webhook signature validation.");
                return false;
            }
            String signature = signatureHeader.replace("sha256=", "");
            javax.crypto.spec.SecretKeySpec signingKey = new javax.crypto.spec.SecretKeySpec(metaAppSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String calculatedSig = hexString.toString();
            return java.security.MessageDigest.isEqual(
                    signature.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    calculatedSig.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Update CRM lead fields safely within the active workspace.
     * Used by the pipeline board, assignment, follow-up planning, and business profile edits.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        access.requireWorkspacePermission(Permission.LEAD_UPDATE);
        if (body.containsKey("assignedUserId") || body.containsKey("assigned_user_id")) {
            access.requireWorkspacePermission(Permission.LEAD_ASSIGN);
        }
        String workspaceId = sec.currentWorkspaceId();
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, workspaceId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead lead = opt.get();

        String oldStatus = lead.getStatus();
        String oldStage = lead.getPipelineStage();

        applyLeadFields(lead, body);

        Lead saved = repo.save(lead);
        auditLead("LEAD_UPDATED", saved, "Lead updated");
        if ((oldStatus != null && !oldStatus.equals(saved.getStatus())) ||
                (oldStage != null && !oldStage.equals(saved.getPipelineStage()))) {
            leadPipelineTrackingService.recordLeadScored(workspaceId, saved.getId(),
                    "CRM lead updated: status " + oldStatus + " -> " + saved.getStatus() +
                            ", stage " + oldStage + " -> " + saved.getPipelineStage());
        }
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
        access.requireWorkspacePermission(Permission.LEAD_UPDATE);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead lead = opt.get();
        String status = cleanString(body.get("status"), 50);
        String stage = cleanString(body.get("pipeline_stage") != null ? body.get("pipeline_stage") : body.get("pipelineStage"), 80);
        if (status != null) {
            lead.setStatus(validateStatus(status));
        }
        if (stage != null) {
            lead.setPipelineStage(validatePipelineStage(stage));
        }
        Lead saved = repo.save(lead);
        auditLead("LEAD_STATUS_UPDATED", saved, "Lead status updated");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_DELETE);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead l = opt.get();
        repo.delete(l);
        auditLead("LEAD_DELETED", l, "Lead deleted");
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  AI SDR Conversations
    // ══════════════════════════════════════════════════════════════════

    /** Fetch lead conversation history */
    @GetMapping("/{id}/chat")
    public ResponseEntity<List<LeadChatMessage>> getChatHistory(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_READ);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sdrService.getConversationHistory(id, sec.currentWorkspaceId()));
    }

    /** Post new user message simulating inbound chat, return AI response */
    @PostMapping("/{id}/chat")
    public ResponseEntity<LeadChatMessage> receiveChatMessage(@PathVariable String id, @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.LEAD_ACTIVITY_ADD);
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead l = opt.get();
        String msg = cleanString(body.get("message"), 2000);
        if (msg == null || msg.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        LeadChatMessage reply = sdrService.receiveMessage(l, msg);
        return ResponseEntity.ok(reply);
    }

    /** Fetch persisted CRM timeline notes for a lead in the active workspace. */
    @GetMapping("/{id}/interactions")
    public ResponseEntity<List<LeadInteraction>> getInteractions(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.LEAD_READ);
        String workspaceId = sec.currentWorkspaceId();
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, workspaceId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(interactionRepo.findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(id, workspaceId));
    }

    @GetMapping("/{id}/activities")
    public ResponseEntity<List<LeadInteraction>> getActivities(@PathVariable String id) {
        return getInteractions(id);
    }

    /** Add a CRM note/call/email/meeting entry to the lead timeline. */
    @PostMapping("/{id}/interactions")
    public ResponseEntity<?> addInteraction(@PathVariable String id, @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.LEAD_ACTIVITY_ADD);
        String workspaceId = sec.currentWorkspaceId();
        Optional<Lead> opt = repo.findByIdAndWorkspaceId(id, workspaceId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Lead lead = opt.get();

        String details = cleanString(body.get("details") != null ? body.get("details") : body.get("notes"), 2000);
        if (details == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Interaction note is required"));
        }

        LeadInteraction interaction = new LeadInteraction();
        interaction.setLeadId(id);
        interaction.setWorkspaceId(workspaceId);
        interaction.setType(validateInteractionType(cleanString(body.get("type"), 50)));
        interaction.setChannel(validateInteractionChannel(cleanString(body.get("channel"), 30)));
        interaction.setDetails(details);
        interaction.setCreatedAt(LocalDateTime.now());

        lead.setLastContactedAt(LocalDateTime.now());
        repo.save(lead);

        LeadInteraction saved = interactionRepo.save(interaction);
        auditLead("LEAD_ACTIVITY_ADDED", lead, "Lead activity added");
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<?> addNote(@PathVariable String id, @RequestBody Map<String, String> body) {
        Map<String, String> noteBody = new HashMap<>(body);
        noteBody.putIfAbsent("type", "NOTE");
        noteBody.putIfAbsent("channel", "CRM");
        return addInteraction(id, noteBody);
    }

    /**
     * Simulates a pipeline run to test monitoring, failure triggers, and dashboard updates.
     */
    @PostMapping("/simulate-run")
    public ResponseEntity<Map<String, String>> simulatePipelineRun(@RequestBody Map<String, Object> body) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        }
        access.requireWorkspacePermission(Permission.LEAD_SCORE);
        String workspaceId = sec.currentWorkspaceId();
        if (localApprovalSafetyService.shouldRequireApprovalOnly("CRM_SIMULATE_RUN")) {
            localApprovalSafetyService.auditBlockedExecution(
                    workspaceId,
                    "CRM_SIMULATE_RUN",
                    "Lead",
                    null,
                    "Dev simulator blocked before fake lead or simulated WhatsApp timeline mutation."
            );
            return ResponseEntity.status(403).body(Map.of(
                    "status", "blocked",
                    "approval_required", "true",
                    "message", localApprovalSafetyService.blockedMessage("CRM simulator")
            ));
        }
        boolean injectFailure = Boolean.TRUE.equals(body.get("injectFailure"));
        String stage = (String) body.getOrDefault("stage", "ALL");

        String mockLeadId = java.util.UUID.randomUUID().toString();

        leadPipelineTrackingService.recordWebhookReceived(workspaceId, "Mock Webhook Payload received at simulate-run.");

        if ("WEBHOOK".equals(stage)) {
            if (injectFailure) {
                leadPipelineTrackingService.recordFailure(workspaceId, null, "WORKSPACE_RESOLVED", "Simulated Workspace Resolution Failure.");
            }
            return ResponseEntity.ok(Map.of("status", "simulated", "stage", "WEBHOOK"));
        }

        leadPipelineTrackingService.recordWorkspaceResolved(workspaceId, mockLeadId, "Mock workspace resolved for test lead.");

        Lead mockLead = new Lead();
        mockLead.setWorkspaceId(workspaceId);
        mockLead.setName("Simulated Lead " + mockLeadId.substring(0, 8));
        mockLead.setSource("META_SIMULATOR");
        mockLead.setStatus("WARM");
        mockLead.setScore(0.85);
        mockLead.setPhone("+919999999999");
        mockLead.setMessage("Hello, I am interested in your marketing tool!");
        mockLead.setCreatedAt(LocalDateTime.now());
        mockLead = repo.save(mockLead);

        leadPipelineTrackingService.recordLeadCreated(workspaceId, mockLead.getId(), "Simulated lead saved in CRM.");
        leadPipelineTrackingService.recordLeadScored(workspaceId, mockLead.getId(), "Simulated AI Lead scoring: Score=0.85, status=WARM.");

        if (injectFailure && "WHATSAPP".equals(stage)) {
            leadPipelineTrackingService.recordFailure(workspaceId, mockLead.getId(), "WHATSAPP_SENT", "Simulated WhatsApp Dispatch API Outage.");
            return ResponseEntity.ok(Map.of("status", "simulated", "stage", "WHATSAPP_FAILED"));
        }

        leadPipelineTrackingService.recordWhatsAppSent(workspaceId, mockLead.getId(), "Simulated WhatsApp follow-up template followup_day1 dispatched.");

        if (injectFailure) {
            leadPipelineTrackingService.recordFailure(workspaceId, mockLead.getId(), "WHATSAPP_DELIVERED", "Simulated delivery failure status from Meta Cloud API.");
        } else {
            leadPipelineTrackingService.recordWhatsAppDelivered(workspaceId, mockLead.getId(), "Simulated Meta delivery report confirmed.");
            leadPipelineTrackingService.recordReplyReceived(workspaceId, mockLead.getId(), "Simulated client response 'YES' parsed successfully.");
        }

        // Force manual execution update of gauges/schedulers
        leadPipelineTrackingService.computeObservabilityRates();

        return ResponseEntity.ok(Map.of(
            "status", "simulated",
            "leadId", mockLead.getId(),
            "failureInjected", String.valueOf(injectFailure)
        ));
    }

    private double toDouble(Object v, double def) {
        if (v == null) return def;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return def; }
    }

    private Double toNullableDouble(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private Double clamp(Double value, double min, double max) {
        if (value == null) return null;
        return Math.max(min, Math.min(max, value));
    }

    private void auditLead(String action, Lead lead, String details) {
        User actor = access.currentUser();
        auditLogService.record(actor, actor.getOrganization(), lead.getWorkspaceId(),
                action, "Lead", lead.getId(), details);
    }

    private String cleanString(Object value, int maxLength) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isBlank()) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String validateStatus(String status) {
        if (status == null) return "NEW";
        String normalized = status.toUpperCase();
        return switch (normalized) {
            case "NEW", "CONTACTED", "QUALIFIED", "WON", "LOST" -> normalized;
            default -> "NEW";
        };
    }

    private String validateTemperature(String temperature) {
        if (temperature == null) return "UNKNOWN";
        String normalized = temperature.toUpperCase();
        return switch (normalized) {
            case "HOT", "WARM", "COLD", "UNKNOWN" -> normalized;
            default -> "UNKNOWN";
        };
    }

    private String validatePriority(String priority) {
        if (priority == null) return "MEDIUM";
        String normalized = priority.toUpperCase();
        return switch (normalized) {
            case "LOW", "MEDIUM", "HIGH", "URGENT" -> normalized;
            default -> "MEDIUM";
        };
    }

    private String validatePipelineStage(String stage) {
        if (stage == null) return "NEW_LEAD";
        String normalized = stage.toUpperCase().replace(' ', '_');
        return switch (normalized) {
            case "NEW_LEAD", "CONTACTED", "QUALIFIED", "INTERESTED", "PROPOSAL_SENT",
                    "FOLLOW_UP", "NEGOTIATION", "CONVERTED", "LOST", "DORMANT", "RECYCLED" -> normalized;
            default -> "NEW_LEAD";
        };
    }

    private String stageForTemperature(String temperature) {
        String normalized = validateTemperature(temperature);
        if (normalized == null) normalized = "UNKNOWN";
        return switch (normalized) {
            case "HOT" -> "INTERESTED";
            case "WARM" -> "QUALIFIED";
            case "COLD" -> "NEW_LEAD";
            default -> "NEW_LEAD";
        };
    }

    private String priorityForScore(Double score) {
        if (score == null) return "MEDIUM";
        if (score >= 0.85) return "URGENT";
        if (score >= 0.7) return "HIGH";
        if (score >= 0.4) return "MEDIUM";
        return "LOW";
    }

    private String nextActionForTemperature(String temperature) {
        String normalized = validateTemperature(temperature);
        if (normalized == null) normalized = "COLD";
        return switch (normalized) {
            case "HOT" -> "Call within 15 minutes and send a WhatsApp proposal.";
            case "WARM" -> "Send pricing details and schedule a follow-up reminder.";
            case "COLD" -> "Ask one qualifying question before assigning sales time.";
            default -> "Collect contact details before prioritizing";
        };
    }

    private String validateInteractionType(String type) {
        if (type == null) return "NOTE";
        String normalized = type.toUpperCase();
        return switch (normalized) {
            case "NOTE", "EMAIL", "CALL", "MEETING", "SYSTEM", "WHATSAPP", "POSITIVE_REPLY", "NEGATIVE_REPLY", "CALL_BOOKED" -> normalized;
            default -> "NOTE";
        };
    }

    private String validateInteractionChannel(String channel) {
        if (channel == null) return "CRM";
        String normalized = channel.toUpperCase();
        return switch (normalized) {
            case "CRM", "EMAIL", "CALL", "MEETING", "WHATSAPP", "SYSTEM" -> normalized;
            default -> "CRM";
        };
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { return LocalDateTime.parse(value.toString()); } catch (Exception e) { return null; }
    }

    private void applyLeadFields(Lead lead, Map<String, Object> body) {
        if (body.containsKey("name")) lead.setName(cleanString(body.get("name"), 255));
        if (body.containsKey("phone")) lead.setPhone(cleanString(body.get("phone"), 50));
        if (body.containsKey("email")) lead.setEmail(cleanString(body.get("email"), 255));
        if (body.containsKey("source")) lead.setSource(cleanString(body.get("source"), 80));
        if (body.containsKey("message")) lead.setMessage(cleanString(body.get("message"), 2000));
        if (body.containsKey("campaignId")) lead.setCampaignId(cleanString(body.get("campaignId"), 36));
        if (body.containsKey("campaign_id")) lead.setCampaignId(cleanString(body.get("campaign_id"), 36));
        if (body.containsKey("assignedUserId")) lead.setAssignedUserId(cleanString(body.get("assignedUserId"), 36));
        if (body.containsKey("assigned_user_id")) lead.setAssignedUserId(cleanString(body.get("assigned_user_id"), 36));
        if (body.containsKey("tags")) lead.setTags(cleanString(body.get("tags"), 1000));
        if (body.containsKey("notes")) lead.setNotes(cleanString(body.get("notes"), 4000));
        if (body.containsKey("priority")) lead.setPriority(validatePriority(cleanString(body.get("priority"), 30)));
        if (body.containsKey("budget")) lead.setBudget(toNullableDouble(body.get("budget")));
        if (body.containsKey("interestCategory")) lead.setInterestCategory(cleanString(body.get("interestCategory"), 255));
        if (body.containsKey("interest_category")) lead.setInterestCategory(cleanString(body.get("interest_category"), 255));
        if (body.containsKey("location")) lead.setLocation(cleanString(body.get("location"), 255));
        if (body.containsKey("conversionProbability")) lead.setConversionProbability(clamp(toNullableDouble(body.get("conversionProbability")), 0.0, 1.0));
        if (body.containsKey("conversion_probability")) lead.setConversionProbability(clamp(toNullableDouble(body.get("conversion_probability")), 0.0, 1.0));
        if (body.containsKey("expectedRevenue")) lead.setExpectedRevenue(toNullableDouble(body.get("expectedRevenue")));
        if (body.containsKey("expected_revenue")) lead.setExpectedRevenue(toNullableDouble(body.get("expected_revenue")));
        if (body.containsKey("lostReason")) lead.setLostReason(cleanString(body.get("lostReason"), 1000));
        if (body.containsKey("lost_reason")) lead.setLostReason(cleanString(body.get("lost_reason"), 1000));
        if (body.containsKey("aiSummary")) lead.setAiSummary(cleanString(body.get("aiSummary"), 4000));
        if (body.containsKey("ai_summary")) lead.setAiSummary(cleanString(body.get("ai_summary"), 4000));
        if (body.containsKey("nextBestAction")) lead.setNextBestAction(cleanString(body.get("nextBestAction"), 1000));
        if (body.containsKey("next_best_action")) lead.setNextBestAction(cleanString(body.get("next_best_action"), 1000));
        if (body.containsKey("lastContactedAt")) lead.setLastContactedAt(parseDateTime(body.get("lastContactedAt")));
        if (body.containsKey("last_contacted_at")) lead.setLastContactedAt(parseDateTime(body.get("last_contacted_at")));
        if (body.containsKey("nextFollowUpAt")) lead.setNextFollowUpAt(parseDateTime(body.get("nextFollowUpAt")));
        if (body.containsKey("next_follow_up_at")) lead.setNextFollowUpAt(parseDateTime(body.get("next_follow_up_at")));
        if (body.containsKey("status")) lead.setStatus(validateStatus(cleanString(body.get("status"), 50)));
        if (body.containsKey("pipelineStage")) lead.setPipelineStage(validatePipelineStage(cleanString(body.get("pipelineStage"), 80)));
        if (body.containsKey("pipeline_stage")) lead.setPipelineStage(validatePipelineStage(cleanString(body.get("pipeline_stage"), 80)));
    }

    private ApprovalActionType approvalAction(String actionType) {
        return switch (actionType) {
            case "SEND_WHATSAPP" -> ApprovalActionType.SEND_WHATSAPP;
            case "SEND_EMAIL" -> ApprovalActionType.SEND_EMAIL;
            case "CALL_LEAD" -> ApprovalActionType.CALL_LEAD;
            default -> ApprovalActionType.FOLLOW_UP;
        };
    }

    private ApprovalSeverity severity(int score) {
        if (score >= 75) return ApprovalSeverity.HIGH;
        if (score >= 45) return ApprovalSeverity.MEDIUM;
        return ApprovalSeverity.LOW;
    }

    private String recommendationJson(Lead lead, LeadScoringService.NextActionResult action) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "module", "SALES_CLOSER",
                    "leadId", lead.getId(),
                    "leadName", lead.getName() != null ? lead.getName() : "",
                    "phone", lead.getPhone() != null ? lead.getPhone() : "",
                    "email", lead.getEmail() != null ? lead.getEmail() : "",
                    "recommendedAction", action.action(),
                    "actionType", action.actionType(),
                    "sendsAutomatically", false,
                    "whatsappSetupRequired", true
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String mathSnapshotJson(Lead lead, LeadScoringService.NextActionResult action) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "formulaVersion", LeadScoringService.FORMULA_VERSION,
                    "leadId", lead.getId(),
                    "score", action.score().score(),
                    "temperature", action.score().temperature(),
                    "scoreBreakdown", action.score().breakdown(),
                    "reason", action.score().breakdown().get("reason")
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String safeTitle(String value) {
        return value == null || value.isBlank() ? "Lead" : cleanString(value, 120);
    }
}
