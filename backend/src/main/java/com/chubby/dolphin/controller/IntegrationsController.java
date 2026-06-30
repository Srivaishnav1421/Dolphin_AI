package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.IntegrationSetting;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.entity.WorkspaceAiConfig;
import com.chubby.dolphin.repository.IntegrationSettingRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.repository.WorkspaceAiConfigRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.service.ai.AnthropicAiService;
import com.chubby.dolphin.service.ai.GeminiAiService;
import com.chubby.dolphin.service.ai.HuggingFaceAiService;
import com.chubby.dolphin.service.ai.OpenAiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/integrations")
@Slf4j
public class IntegrationsController {

    private static final DateTimeFormatter STATUS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> LIVE_VALIDATION_PROVIDERS = Set.of("openai", "gemini", "anthropic", "huggingface");

    private final IntegrationSettingRepository repository;
    private final MetaConnectionRepository metaConnectionRepository;
    private final WorkspaceConfigRepository workspaceConfigRepository;
    private final WorkspaceAiConfigRepository workspaceAiConfigRepository;
    private final OpenAiService openAiService;
    private final GeminiAiService geminiAiService;
    private final AnthropicAiService anthropicAiService;
    private final HuggingFaceAiService huggingFaceAiService;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final AuditLogService auditLogService;
    private final LocalApprovalSafetyService localApprovalSafetyService;
    private final ObjectMapper mapper = new ObjectMapper();

    public IntegrationsController(IntegrationSettingRepository repository,
                                  MetaConnectionRepository metaConnectionRepository,
                                  WorkspaceConfigRepository workspaceConfigRepository,
                                  WorkspaceAiConfigRepository workspaceAiConfigRepository,
                                  OpenAiService openAiService,
                                  GeminiAiService geminiAiService,
                                  AnthropicAiService anthropicAiService,
                                  HuggingFaceAiService huggingFaceAiService,
                                  SecurityUtils sec,
                                  AccessControlService access,
                                  AuditLogService auditLogService,
                                  LocalApprovalSafetyService localApprovalSafetyService) {
        this.repository = repository;
        this.metaConnectionRepository = metaConnectionRepository;
        this.workspaceConfigRepository = workspaceConfigRepository;
        this.workspaceAiConfigRepository = workspaceAiConfigRepository;
        this.openAiService = openAiService;
        this.geminiAiService = geminiAiService;
        this.anthropicAiService = anthropicAiService;
        this.huggingFaceAiService = huggingFaceAiService;
        this.sec = sec;
        this.access = access;
        this.auditLogService = auditLogService;
        this.localApprovalSafetyService = localApprovalSafetyService;
    }

    /**
     * GET /api/integrations/status
     */
    @GetMapping("/status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getStatus() {
        access.requireWorkspacePermission(Permission.INTEGRATION_READ);
        String workspaceId = sec.currentAccountId();
        List<IntegrationSetting> settings = repository.findAllByWorkspaceId(workspaceId);
        
        Map<String, Object> response = new HashMap<>();
        for (IntegrationSetting s : settings) {
            Map<String, Object> providerInfo = new HashMap<>();
            String validationStatus = normalizeValidationStatus(s.getValidationStatus());
            providerInfo.put("configured", true);
            providerInfo.put("connected", "VALIDATED".equals(validationStatus));
            providerInfo.put("validationStatus", validationStatus);
            providerInfo.put("validationMessage", s.getLastValidationMessage());
            providerInfo.put("lastValidatedAt", s.getLastValidatedAt() != null
                    ? s.getLastValidatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : null);
            providerInfo.put("lastChecked", s.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            try {
                Map<String, String> creds = mapper.readValue(s.getCredentialsJson(), new TypeReference<Map<String, String>>() {});
                Map<String, String> masked = new HashMap<>();
                for (Map.Entry<String, String> entry : creds.entrySet()) {
                    masked.put(entry.getKey(), maskKey(entry.getValue()));
                }
                providerInfo.put("maskedKeys", masked);
            } catch (Exception e) {
                providerInfo.put("maskedKeys", new HashMap<String, String>());
            }
            response.put(s.getProviderId(), providerInfo);
        }
        addMetaStatus(workspaceId, response);
        addWhatsAppStatus(workspaceId, response);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/integrations/{provider}/connect
     */
    @PostMapping("/{provider}/connect")
    @Transactional
    public ResponseEntity<Map<String, Object>> connect(
            @PathVariable("provider") String provider,
            @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
        
        String workspaceId = sec.currentAccountId();
        String normalizedProvider = provider.toLowerCase();
        log.info("Connecting integration for provider: {} in workspace: {}", normalizedProvider, workspaceId);

        // Perform basic input validation
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Credentials cannot be empty"));
        }

        // Simulating external validation: if url is passed, check format; if key is passed, ensure not blank
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", entry.getKey() + " cannot be blank"));
            }
            if (entry.getKey().toLowerCase().contains("url") && !entry.getValue().startsWith("http")) {
                return ResponseEntity.badRequest().body(Map.of("message", entry.getKey() + " must be a valid URL"));
            }
        }

        try {
            String json = mapper.writeValueAsString(body);
            Optional<IntegrationSetting> existingOpt = repository.findByWorkspaceIdAndProviderId(workspaceId, normalizedProvider);
            
            IntegrationSetting setting;
            if (existingOpt.isPresent()) {
                setting = existingOpt.get();
                setting.setCredentialsJson(json);
                setting.setUpdatedAt(LocalDateTime.now());
            } else {
                setting = new IntegrationSetting(workspaceId, normalizedProvider, json);
            }
            setting.setValidationStatus("PENDING_VALIDATION");
            setting.setLastValidatedAt(null);
            setting.setLastValidationMessage("Credentials stored. Run Test connection to verify live access.");
            repository.save(setting);
            auditIntegration(existingOpt.isPresent() ? "INTEGRATION_UPDATED" : "INTEGRATION_CONNECTED",
                    workspaceId, normalizedProvider, "Integration credentials stored");

            // Return masked keys as confirmation
            Map<String, String> masked = new HashMap<>();
            for (Map.Entry<String, String> entry : body.entrySet()) {
                masked.put(entry.getKey(), maskKey(entry.getValue()));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("configured", true);
            result.put("connected", false);
            result.put("validationStatus", "PENDING_VALIDATION");
            result.put("message", "Credentials stored. Run Test connection to verify live access.");
            result.put("maskedKeys", masked);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error saving integration for provider " + normalizedProvider, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal error saving credentials"));
        }
    }

    /**
     * POST /api/integrations/{provider}/test
     */
    @PostMapping("/{provider}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable("provider") String provider) {
        access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
        String workspaceId = sec.currentAccountId();
        String normalizedProvider = provider.toLowerCase();
        Optional<IntegrationSetting> settingOpt = repository.findByWorkspaceIdAndProviderId(workspaceId, normalizedProvider);
        
        if (settingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No credentials configured for this provider"));
        }

        // Credentials can be saved before provider-specific live validators are implemented.
        // Do not report a false success here; production verification must call the provider API.
        try {
            IntegrationSetting setting = settingOpt.get();
            Map<String, String> creds = mapper.readValue(setting.getCredentialsJson(), new TypeReference<Map<String, String>>() {});

            for (Map.Entry<String, String> entry : creds.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("message", entry.getKey() + " is blank. Update credentials before testing."));
                }
            }

            if (requiresLiveProviderValidation(normalizedProvider)
                    && localApprovalSafetyService.shouldRequireApprovalOnly("INTEGRATION_TEST_" + normalizedProvider.toUpperCase())) {
                localApprovalSafetyService.auditBlockedExecution(
                        workspaceId,
                        "INTEGRATION_TEST_" + normalizedProvider.toUpperCase(),
                        "Integration",
                        normalizedProvider,
                        "Blocked /api/integrations/" + normalizedProvider + "/test before live provider validation."
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "approval_required", true,
                        "external_execution_allowed", false,
                        "status", "blocked",
                        "message", localApprovalSafetyService.blockedMessage(providerDisplayName(normalizedProvider) + " connection test")
                ));
            }

            if ("openai".equals(normalizedProvider)) {
                openAiService.validateWorkspaceConnection(workspaceId);
                markValidation(setting, "VALIDATED", "OpenAI validated successfully.");
                activateWorkspaceProvider(workspaceId, LlmProvider.OPENAI);
                auditIntegration("INTEGRATION_VALIDATED", workspaceId, normalizedProvider, "OpenAI validated successfully");
                return ResponseEntity.ok(Map.of(
                        "status", "connected",
                        "message", "OpenAI validated successfully. It is available for task routing."
                ));
            }

            if ("gemini".equals(normalizedProvider)) {
                geminiAiService.validateWorkspaceConnection(workspaceId);
                markValidation(setting, "VALIDATED", "Gemini validated successfully.");
                activateWorkspaceProvider(workspaceId, LlmProvider.GEMINI);
                auditIntegration("INTEGRATION_VALIDATED", workspaceId, normalizedProvider, "Gemini validated successfully");
                return ResponseEntity.ok(Map.of(
                        "status", "connected",
                        "message", "Gemini validated successfully. Creative Studio will use this provider."
                ));
            }

            if ("anthropic".equals(normalizedProvider)) {
                anthropicAiService.validateWorkspaceConnection(workspaceId);
                markValidation(setting, "VALIDATED", "Anthropic validated successfully.");
                activateWorkspaceProvider(workspaceId, LlmProvider.ANTHROPIC);
                auditIntegration("INTEGRATION_VALIDATED", workspaceId, normalizedProvider, "Anthropic validated successfully");
                return ResponseEntity.ok(Map.of(
                        "status", "connected",
                        "message", "Anthropic validated successfully. It is available for task routing."
                ));
            }

            if ("huggingface".equals(normalizedProvider)) {
                huggingFaceAiService.validateWorkspaceConnection(workspaceId);
                markValidation(setting, "VALIDATED", "Hugging Face validated successfully.");
                activateWorkspaceProvider(workspaceId, LlmProvider.HUGGINGFACE);
                auditIntegration("INTEGRATION_VALIDATED", workspaceId, normalizedProvider, "Hugging Face validated successfully");
                return ResponseEntity.ok(Map.of(
                        "status", "connected",
                        "message", "Hugging Face validated successfully. It is available for task routing."
                ));
            }

            markValidation(setting, "PENDING_VALIDATION", "Live provider validation is not enabled for this connector yet.");
            auditIntegration("INTEGRATION_VALIDATION_PENDING", workspaceId, normalizedProvider, "Live validation not enabled");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "status", "pending_live_validation",
                    "message", "Credentials are stored. Live provider validation is not enabled for this connector yet."
            ));

        } catch (Exception e) {
            settingOpt.ifPresent(setting -> markValidation(setting, "FAILED", cleanMessage(e.getMessage())));
            auditIntegration("INTEGRATION_VALIDATION_FAILED", workspaceId, normalizedProvider, cleanMessage(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Connection test failed: " + cleanMessage(e.getMessage())));
        }
    }

    /**
     * DELETE /api/integrations/{provider}
     */
    @DeleteMapping("/{provider}")
    @Transactional
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable("provider") String provider) {
        access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
        String workspaceId = sec.currentAccountId();
        String normalizedProvider = provider.toLowerCase();
        log.info("Disconnecting provider: {} in workspace: {}", normalizedProvider, workspaceId);
        repository.deleteByWorkspaceIdAndProviderId(workspaceId, normalizedProvider);
        if (llmProviderFromIntegration(normalizedProvider).isPresent()) {
            activateWorkspaceProvider(workspaceId, LlmProvider.OLLAMA);
        }
        auditIntegration("INTEGRATION_DISCONNECTED", workspaceId, normalizedProvider, "Integration disconnected");
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    private void addMetaStatus(String workspaceId, Map<String, Object> response) {
        Optional<MetaConnection> activeConnection = metaConnectionRepository.findFirstByWorkspaceIdAndTokenStatus(workspaceId, "VALID");
        if (activeConnection.isPresent()) {
            MetaConnection conn = activeConnection.get();
            Map<String, Object> providerInfo = new HashMap<>();
            providerInfo.put("configured", true);
            providerInfo.put("connected", true);
            providerInfo.put("validationStatus", "VALIDATED");
            providerInfo.put("validationMessage", "Meta ad account connection is present. Local mode still blocks publish and launch actions.");
            providerInfo.put("lastValidatedAt", conn.getUpdatedAt() != null ? conn.getUpdatedAt().format(STATUS_TIME) : null);
            providerInfo.put("lastChecked", LocalDateTime.now().format(STATUS_TIME));
            providerInfo.put("maskedKeys", Map.of(
                    "ad_account_id", maskKey(conn.getMetaAdAccountId()),
                    "token_status", conn.getTokenStatus()
            ));
            response.put("meta", providerInfo);
            return;
        }
        response.putIfAbsent("meta", disconnectedInfo("No Meta ad account is connected for this workspace."));
    }

    private void addWhatsAppStatus(String workspaceId, Map<String, Object> response) {
        Optional<WorkspaceConfig> config = workspaceConfigRepository.findByWorkspaceId(workspaceId);
        boolean configured = config
                .map(c -> hasText(c.getWhatsappPhoneId()) && hasText(c.getWhatsappToken()))
                .orElse(false);

        if (!configured) {
            response.putIfAbsent("whatsapp", disconnectedInfo("WhatsApp credentials are not configured for this workspace."));
            return;
        }

        Map<String, Object> providerInfo = new HashMap<>();
        providerInfo.put("configured", true);
        providerInfo.put("connected", false);
        providerInfo.put("validationStatus", "PENDING_VALIDATION");
        providerInfo.put("validationMessage", "WhatsApp credentials are configured. Live send validation is disabled in local approval-first mode.");
        providerInfo.put("lastChecked", LocalDateTime.now().format(STATUS_TIME));
        providerInfo.put("maskedKeys", Map.of("phone_id", maskKey(config.get().getWhatsappPhoneId())));
        response.put("whatsapp", providerInfo);
    }

    private Map<String, Object> disconnectedInfo(String message) {
        Map<String, Object> providerInfo = new HashMap<>();
        providerInfo.put("configured", false);
        providerInfo.put("connected", false);
        providerInfo.put("validationStatus", "PENDING_VALIDATION");
        providerInfo.put("validationMessage", message);
        providerInfo.put("lastChecked", LocalDateTime.now().format(STATUS_TIME));
        providerInfo.put("maskedKeys", Map.of());
        return providerInfo;
    }

    private void activateWorkspaceProvider(String workspaceId, LlmProvider provider) {
        WorkspaceAiConfig config = workspaceAiConfigRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> WorkspaceAiConfig.builder().workspaceId(workspaceId).build());
        config.setActiveProvider(provider);
        workspaceAiConfigRepository.save(config);
    }

    private void markValidation(IntegrationSetting setting, String status, String message) {
        setting.setValidationStatus(status);
        setting.setLastValidatedAt(LocalDateTime.now());
        setting.setLastValidationMessage(cleanMessage(message));
        repository.save(setting);
    }

    private String normalizeValidationStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PENDING_VALIDATION";
        }
        return switch (status.toUpperCase()) {
            case "VALIDATED", "FAILED", "PENDING_VALIDATION" -> status.toUpperCase();
            default -> "PENDING_VALIDATION";
        };
    }

    private String cleanMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Provider did not return a usable validation response.";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        sanitized = auditLogService.redact(sanitized);
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private Optional<LlmProvider> llmProviderFromIntegration(String providerId) {
        return switch (providerId) {
            case "openai" -> Optional.of(LlmProvider.OPENAI);
            case "gemini" -> Optional.of(LlmProvider.GEMINI);
            case "anthropic" -> Optional.of(LlmProvider.ANTHROPIC);
            case "huggingface" -> Optional.of(LlmProvider.HUGGINGFACE);
            case "ollama" -> Optional.of(LlmProvider.OLLAMA);
            default -> Optional.empty();
        };
    }

    private boolean requiresLiveProviderValidation(String providerId) {
        return LIVE_VALIDATION_PROVIDERS.contains(providerId);
    }

    private String providerDisplayName(String providerId) {
        return switch (providerId) {
            case "openai" -> "OpenAI";
            case "gemini" -> "Gemini";
            case "anthropic" -> "Anthropic";
            case "huggingface" -> "Hugging Face";
            default -> providerId;
        };
    }

    private String maskKey(String key) {
        if (key == null) return "";
        if (key.length() <= 8) {
            return "••••••••";
        }
        return "••••••••" + key.substring(key.length() - 4);
    }

    private void auditIntegration(String action, String workspaceId, String provider, String details) {
        User actor = access.currentUser();
        auditLogService.record(actor, actor.getOrganization(), workspaceId,
                action, "Integration", provider, auditLogService.redact(details));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
