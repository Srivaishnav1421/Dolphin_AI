package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditLogRepository auditRepo;
    private final UserRepository     userRepo;
    private final CampaignRepository campaignRepo;
    private final LeadRepository     leadRepo;
    private final BrainEventRepository brainEventRepo;
    private final WorkspaceRepository workspaceRepo;
    private final AccessControlService access;
    private final AuditLogService auditLogService;

    public AdminController(AuditLogRepository auditRepo,
                           UserRepository userRepo,
                           CampaignRepository campaignRepo,
                           LeadRepository leadRepo,
                           BrainEventRepository brainEventRepo,
                           WorkspaceRepository workspaceRepo,
                           AccessControlService access,
                           AuditLogService auditLogService) {
        this.auditRepo = auditRepo;
        this.userRepo = userRepo;
        this.campaignRepo = campaignRepo;
        this.leadRepo = leadRepo;
        this.brainEventRepo = brainEventRepo;
        this.workspaceRepo = workspaceRepo;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> auditLogs() {
        User actor = access.currentUser();
        List<AuditLog> logs;
        if (access.isSystemAdmin()) {
            logs = auditRepo.findTop100ByOrderByTimestampDesc();
        } else {
            access.requireWorkspacePermission(Permission.AUDIT_READ);
            logs = auditRepo.findTop100ByOrganizationIdOrderByTimestampDesc(access.currentOrganizationId());
        }
        auditLogService.record(actor, actor.getOrganization(), actor.getAccountId(),
                "ADMIN_AUDIT_LOGS_VIEWED", "AuditLog", actor.getOrganization() != null ? actor.getOrganization().getId() : "platform",
                "Admin audit log access");
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/users")
    public ResponseEntity<?> users() {
        User actor = access.currentUser();
        List<User> users = access.isSystemAdmin()
                ? userRepo.findAll()
                : userRepo.findByOrganizationId(access.currentOrganizationId());
        auditLogService.record(actor, actor.getOrganization(), actor.getAccountId(),
                "ADMIN_USERS_VIEWED", "User", actor.getOrganization() != null ? actor.getOrganization().getId() : "platform",
                "Admin user list access");
        return ResponseEntity.ok(users.stream().map(this::safeUser).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        User actor = access.currentUser();
        if (access.isSystemAdmin()) {
            auditLogService.record(actor, actor.getOrganization(), actor.getAccountId(),
                    "ADMIN_STATS_VIEWED", "Platform", "global", "Platform stats access");
            return ResponseEntity.ok(Map.of(
                    "scope", "platform",
                    "total_users", userRepo.count(),
                    "total_campaigns", campaignRepo.count(),
                    "total_leads", leadRepo.count(),
                    "total_events", brainEventRepo.count()
            ));
        }

        access.requireWorkspacePermission(Permission.AUDIT_READ);
        String organizationId = access.currentOrganizationId();
        List<String> workspaceIds = workspaceRepo.findByOrganizationId(organizationId).stream()
                .map(workspace -> workspace.getId())
                .toList();
        auditLogService.record(actor, actor.getOrganization(), actor.getAccountId(),
                "ADMIN_STATS_VIEWED", "Organization", organizationId, "Organization stats access");
        return ResponseEntity.ok(Map.of(
            "scope", "organization",
            "organization_id", organizationId,
            "total_users", userRepo.countByOrganizationId(organizationId),
            "total_campaigns", campaignRepo.countByWorkspaceIdIn(workspaceIds),
            "total_leads", leadRepo.countByWorkspaceIdIn(workspaceIds),
            "total_events", brainEventRepo.countByWorkspaceIdIn(workspaceIds)
        ));
    }

    private Map<String, Object> safeUser(User user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getName() != null ? user.getName() : user.getEmail(),
                "role", user.getRole() != null ? user.getRole() : "VIEWER",
                "active", user.isActive(),
                "organization_id", user.getOrganization() != null ? user.getOrganization().getId() : "",
                "workspace_id", user.getWorkspaceId() != null ? user.getWorkspaceId() : ""
        );
    }
}
