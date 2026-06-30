package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace/config")
@Slf4j
public class WorkspaceConfigController {

    private final WorkspaceConfigRepository configRepo;
    private final SecurityUtils sec;

    public WorkspaceConfigController(WorkspaceConfigRepository configRepo, SecurityUtils sec) {
        this.configRepo = configRepo;
        this.sec = sec;
    }

    /**
     * Get the workspace configuration for the active tenant.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getConfig() {
        String workspaceId = sec.currentAccountId();
        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    WorkspaceConfig emptyConfig = new WorkspaceConfig();
                    emptyConfig.setWorkspaceId(workspaceId);
                    return emptyConfig;
                });
        return ResponseEntity.ok(toSafeResponse(config));
    }

    /**
     * Save/Update the workspace configuration — OWNER/ADMIN only.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        String workspaceId = sec.currentAccountId();
        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElse(new WorkspaceConfig());

        config.setWorkspaceId(workspaceId);
        config.setWhatsappPhoneId((String) body.get("whatsappPhoneId"));
        if (hasNewSecret(body, "whatsappToken")) {
            config.setWhatsappToken((String) body.get("whatsappToken"));
        }
        if (hasNewSecret(body, "whatsappVerifyToken")) {
            config.setWhatsappVerifyToken((String) body.get("whatsappVerifyToken"));
        }
        config.setBrandName((String) body.get("brandName"));
        config.setBrandLogoUrl((String) body.get("brandLogoUrl"));
        config.setBillingEmail((String) body.get("billingEmail"));
        config.setGstin((String) body.get("gstin"));
        config.setLegalName((String) body.get("legalName"));
        config.setBillingAddress((String) body.get("billingAddress"));
        config.setStateCode((String) body.get("stateCode"));
        config.setPanNumber((String) body.get("panNumber"));
        config.setBankDetails((String) body.get("bankDetails"));
        
        if (body.containsKey("minRoasThreshold")) config.setMinRoasThreshold(toDouble(body.get("minRoasThreshold")));
        if (body.containsKey("maxSpendLimit")) config.setMaxSpendLimit(toDouble(body.get("maxSpendLimit")));
        if (body.containsKey("targetCpl")) config.setTargetCpl(toDouble(body.get("targetCpl")));
        if (body.containsKey("autoOptimizationEnabled")) config.setAutoOptimizationEnabled((Boolean) body.get("autoOptimizationEnabled"));
        
        config.setUpdatedAt(LocalDateTime.now());

        WorkspaceConfig saved = configRepo.save(config);
        log.info("💼 Workspace configurations updated for tenant: {}", workspaceId);
        return ResponseEntity.ok(toSafeResponse(saved));
    }

    private Map<String, Object> toSafeResponse(WorkspaceConfig config) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", config.getId());
        response.put("workspaceId", config.getWorkspaceId());
        response.put("whatsappPhoneId", config.getWhatsappPhoneId());
        response.put("whatsappTokenConfigured", hasText(config.getWhatsappToken()));
        response.put("whatsappVerifyTokenConfigured", hasText(config.getWhatsappVerifyToken()) || hasText(config.getWhatsappVerifyTokenHash()));
        response.put("whatsappWebhookEnabled", Boolean.TRUE.equals(config.getWhatsappWebhookEnabled()));
        response.put("minRoasThreshold", config.getMinRoasThreshold());
        response.put("maxSpendLimit", config.getMaxSpendLimit());
        response.put("targetCpl", config.getTargetCpl());
        response.put("autoOptimizationEnabled", Boolean.TRUE.equals(config.getAutoOptimizationEnabled()));
        response.put("brandName", config.getBrandName());
        response.put("brandLogoUrl", config.getBrandLogoUrl());
        response.put("billingEmail", config.getBillingEmail());
        response.put("gstin", config.getGstin());
        response.put("legalName", config.getLegalName());
        response.put("billingAddress", config.getBillingAddress());
        response.put("stateCode", config.getStateCode());
        response.put("panNumber", config.getPanNumber());
        response.put("bankDetails", config.getBankDetails());
        response.put("createdAt", config.getCreatedAt());
        response.put("updatedAt", config.getUpdatedAt());
        return response;
    }

    private boolean hasNewSecret(Map<String, Object> body, String key) {
        if (!body.containsKey(key)) {
            return false;
        }
        Object raw = body.get(key);
        if (!(raw instanceof String value)) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !trimmed.contains("••");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
