package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.mathengine.CampaignMathEvaluationService;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignRepository repo;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final AuditLogService auditLogService;
    private final CampaignMathEvaluationService mathEvaluationService;

    public CampaignController(CampaignRepository repo,
                              SecurityUtils sec,
                              AccessControlService access,
                              AuditLogService auditLogService,
                              CampaignMathEvaluationService mathEvaluationService) {
        this.repo = repo;
        this.sec = sec;
        this.access = access;
        this.auditLogService = auditLogService;
        this.mathEvaluationService = mathEvaluationService;
    }

    /** List all campaigns for the logged-in account */
    @GetMapping
    public ResponseEntity<List<Campaign>> list() {
        access.requireWorkspacePermission(Permission.CAMPAIGN_READ);
        return ResponseEntity.ok(repo.findByWorkspaceId(sec.currentWorkspaceId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Campaign> get(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_READ);
        Optional<Campaign> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(opt.get());
    }

    @GetMapping("/{id}/math-score")
    public ResponseEntity<?> mathScore(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_METRICS_READ);
        try {
            return ResponseEntity.ok(mathEvaluationService.getLatestPerformanceScoreForCampaign(java.util.UUID.fromString(id)));
        } catch (CampaignMathEvaluationService.MathEvaluationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /** Create a new campaign */
    @PostMapping
    public ResponseEntity<Campaign> create(@RequestBody Campaign c) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_CREATE);
        c.setWorkspaceId(sec.currentWorkspaceId());
        c.setStatus("ACTIVE");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        Campaign saved = repo.save(c);
        auditCampaign("CAMPAIGN_CREATED", saved, "Campaign created");
        return ResponseEntity.ok(saved);
    }

    /** Update campaign (budget, status, name etc) */
    @PutMapping("/{id}")
    public ResponseEntity<Campaign> update(@PathVariable String id, @RequestBody Campaign body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_UPDATE);
        Optional<Campaign> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        if (body.getName()     != null) c.setName(body.getName());
        if (body.getBudget()   != null) c.setBudget(body.getBudget());
        if (body.getTargetCpl()!= null) c.setTargetCpl(body.getTargetCpl());
        if (body.getObjective()!= null) c.setObjective(body.getObjective());
        if (body.getDescription() != null) c.setDescription(body.getDescription());
        if (body.getCtr()      != null) c.setCtr(body.getCtr());
        if (body.getCpl()      != null) c.setCpl(body.getCpl());
        if (body.getRoas()     != null) c.setRoas(body.getRoas());
        if (body.getSpent()    != null) c.setSpent(body.getSpent());
        if (body.getPerformanceScore() != null) c.setPerformanceScore(body.getPerformanceScore());
        c.setUpdatedAt(LocalDateTime.now());
        Campaign saved = repo.save(c);
        auditCampaign("CAMPAIGN_UPDATED", saved, "Campaign updated");
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Campaign> pause(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_PAUSE);
        Optional<Campaign> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        c.setStatus("PAUSED");
        c.setUpdatedAt(LocalDateTime.now());
        Campaign saved = repo.save(c);
        auditCampaign("CAMPAIGN_PAUSED", saved, "Campaign paused");
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Campaign> resume(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_RESUME);
        Optional<Campaign> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        c.setStatus("ACTIVE");
        c.setUpdatedAt(LocalDateTime.now());
        Campaign saved = repo.save(c);
        auditCampaign("CAMPAIGN_RESUMED", saved, "Campaign resumed");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_DELETE);
        Optional<Campaign> opt = repo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        repo.delete(c);
        auditCampaign("CAMPAIGN_DELETED", c, "Campaign deleted");
        return ResponseEntity.noContent().build();
    }

    private void auditCampaign(String action, Campaign campaign, String details) {
        User actor = access.currentUser();
        auditLogService.record(actor, actor.getOrganization(), campaign.getWorkspaceId(),
                action, "Campaign", campaign.getId(), details);
    }
}
