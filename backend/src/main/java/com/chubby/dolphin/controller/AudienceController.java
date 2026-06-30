package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.MetaAudience;
import com.chubby.dolphin.repository.MetaAudienceRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.MetaAudienceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audiences")
@Slf4j
public class AudienceController {

    private final MetaAudienceService audienceService;
    private final MetaAudienceRepository audienceRepo;
    private final SecurityUtils sec;
    private final AccessControlService access;

    public AudienceController(MetaAudienceService audienceService,
                              MetaAudienceRepository audienceRepo,
                              SecurityUtils sec,
                              AccessControlService access) {
        this.audienceService = audienceService;
        this.audienceRepo = audienceRepo;
        this.sec = sec;
        this.access = access;
    }

    /**
     * List all custom and lookalike audiences for the active workspace.
     */
    @GetMapping
    public ResponseEntity<List<MetaAudience>> listAudiences() {
        access.requireWorkspacePermission(Permission.CAMPAIGN_READ);
        return ResponseEntity.ok(audienceRepo.findByWorkspaceId(sec.currentWorkspaceId()));
    }

    /**
     * Create a new Custom Audience.
     */
    @PostMapping("/custom")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<MetaAudience> createCustom(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_UPDATE);
        String name = body.get("name");
        String description = body.getOrDefault("description", "Created via Chubby Dolphin AI");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        MetaAudience audience = audienceService.createCustomAudience(sec.currentWorkspaceId(), name, description);
        return ResponseEntity.ok(audience);
    }

    /**
     * Create a new Lookalike Audience based on a custom seed audience.
     */
    @PostMapping("/lookalike")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<MetaAudience> createLookalike(@RequestBody Map<String, Object> body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_UPDATE);
        String name = (String) body.get("name");
        String sourceAudienceId = (String) body.get("source_audience_id");
        Double ratio = body.get("ratio") != null ? Double.valueOf(body.get("ratio").toString()) : 0.01;
        String country = body.getOrDefault("country", "IN").toString();

        if (name == null || sourceAudienceId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Verify source audience belongs to workspace
        String workspaceId = sec.currentWorkspaceId();
        java.util.Optional<MetaAudience> srcOpt = audienceRepo.findByIdAndWorkspaceId(sourceAudienceId, workspaceId);
        if (srcOpt.isEmpty()) {
            // Check if it's the external Meta ID
            srcOpt = audienceRepo.findByWorkspaceId(workspaceId).stream()
                    .filter(a -> sourceAudienceId.equals(a.getMetaAudienceId()))
                    .findFirst();
        }
        if (srcOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }

        MetaAudience audience = audienceService.createLookalikeAudience(
                workspaceId, name, sourceAudienceId, ratio, country
        );
        return ResponseEntity.ok(audience);
    }

    /**
     * Build an autonomous SuperLookalike targeting pack (1%, 2%, 5% ratios).
     */
    @PostMapping("/super-lookalike")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<List<MetaAudience>> createSuperLookalike(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_UPDATE);
        String name = body.get("name");
        String sourceAudienceId = body.get("source_audience_id");
        String country = body.getOrDefault("country", "IN");

        if (name == null || sourceAudienceId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Verify source audience belongs to workspace
        String workspaceId = sec.currentWorkspaceId();
        java.util.Optional<MetaAudience> srcOpt = audienceRepo.findByIdAndWorkspaceId(sourceAudienceId, workspaceId);
        if (srcOpt.isEmpty()) {
            srcOpt = audienceRepo.findByWorkspaceId(workspaceId).stream()
                    .filter(a -> sourceAudienceId.equals(a.getMetaAudienceId()))
                    .findFirst();
        }
        if (srcOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }

        List<MetaAudience> pack = audienceService.createSuperLookalike(
                workspaceId, name, sourceAudienceId, country
        );
        return ResponseEntity.ok(pack);
    }

    /**
     * Upload an array of contacts to a custom audience.
     */
    @PostMapping("/{id}/upload")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> uploadContacts(
            @PathVariable String id,
            @RequestBody List<Map<String, String>> contacts) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_UPDATE);

        java.util.Optional<MetaAudience> audOpt = audienceRepo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (audOpt.isEmpty()) return ResponseEntity.notFound().build();

        boolean success = audienceService.uploadUsersToAudience(id, contacts);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Successfully uploaded contacts to audience."));
        } else {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to upload contacts."));
        }
    }

    /**
     * Manually trigger AI lead profiling and synchronization to push fresh HOT leads.
     */
    @PostMapping("/{id}/sync-hot-leads")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> syncHotLeads(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_UPDATE);
        java.util.Optional<MetaAudience> audOpt = audienceRepo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (audOpt.isEmpty()) return ResponseEntity.notFound().build();

        int count = audienceService.syncHotLeadsToAudience(sec.currentWorkspaceId(), id);
        return ResponseEntity.ok(Map.of("success", true, "synced_count", count));
    }
}
