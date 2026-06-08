package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    public ResponseEntity<WorkspaceConfig> getConfig() {
        String workspaceId = sec.currentAccountId();
        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    // Create dynamic empty config if none exists yet
                    WorkspaceConfig newConfig = new WorkspaceConfig();
                    newConfig.setWorkspaceId(workspaceId);
                    newConfig.setCreatedAt(LocalDateTime.now());
                    newConfig.setUpdatedAt(LocalDateTime.now());
                    return configRepo.save(newConfig);
                });
        return ResponseEntity.ok(config);
    }

    /**
     * Save/Update the workspace configuration — OWNER/ADMIN only.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<WorkspaceConfig> updateConfig(@RequestBody Map<String, Object> body) {
        String workspaceId = sec.currentAccountId();
        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElse(new WorkspaceConfig());

        config.setWorkspaceId(workspaceId);
        config.setWhatsappPhoneId((String) body.get("whatsappPhoneId"));
        config.setWhatsappToken((String) body.get("whatsappToken"));
        config.setWhatsappVerifyToken((String) body.get("whatsappVerifyToken"));
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
        return ResponseEntity.ok(saved);
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
