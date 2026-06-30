package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.UserWorkspaceRole;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.UserWorkspaceRoleRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceTeamController {

    private static final Set<String> WORKSPACE_ROLES = Set.of("OWNER", "ADMIN", "MANAGER", "EMPLOYEE", "VIEWER");

    private final UserRepository userRepo;
    private final UserWorkspaceRoleRepository roleRepo;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final AuditLogService auditLogService;

    public WorkspaceTeamController(UserRepository userRepo,
                                   UserWorkspaceRoleRepository roleRepo,
                                   SecurityUtils sec,
                                   AccessControlService access,
                                   AuditLogService auditLogService) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.sec = sec;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/team")
    public ResponseEntity<List<Map<String, Object>>> team() {
        access.requireWorkspacePermission(Permission.MEMBER_READ);
        String workspaceId = sec.currentWorkspaceId();
        List<Map<String, Object>> users = roleRepo.findByWorkspaceId(workspaceId).stream()
                .map(role -> safeUser(role.getUser(), role.getRole()))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/team/{userId}/role")
    public ResponseEntity<?> updateRole(@PathVariable String userId,
                                        @Valid @RequestBody RoleUpdateRequest req) {
        access.requireWorkspacePermission(Permission.MEMBER_MANAGE);
        String workspaceId = sec.currentWorkspaceId();
        User actor = sec.currentUser();
        String newRole = req.role().trim().toUpperCase();

        if (!WORKSPACE_ROLES.contains(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported role"));
        }

        User target = userRepo.findById(userId).orElse(null);
        UserWorkspaceRole membership = roleRepo.findByUserIdAndWorkspaceId(userId, workspaceId).orElse(null);
        if (target == null || membership == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Team member not found in this workspace"));
        }

        String oldRole = membership.getRole() != null ? membership.getRole().toUpperCase() : "VIEWER";
        String actorRole = roleRepo.findByUserIdAndWorkspaceId(actor.getId(), workspaceId)
                .map(UserWorkspaceRole::getRole)
                .orElse(actor.getRole());
        if ("OWNER".equals(oldRole) && !"OWNER".equalsIgnoreCase(actorRole)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only owners can change owner access"));
        }

        if (target.getId().equals(actor.getId()) && !"OWNER".equals(newRole)) {
            long owners = roleRepo.findByWorkspaceId(workspaceId).stream()
                    .filter(role -> role.getUser() != null && role.getUser().isActive())
                    .filter(role -> "OWNER".equalsIgnoreCase(role.getRole()))
                    .count();
            if (owners <= 1) {
                return ResponseEntity.status(409).body(Map.of("error", "A workspace must keep at least one active owner"));
            }
        }

        membership.setRole(newRole);
        roleRepo.save(membership);
        auditLogService.record(actor, actor.getOrganization(), workspaceId,
                "WORKSPACE_MEMBER_ROLE_CHANGED", "UserWorkspaceRole", target.getId(),
                "targetUserId=" + target.getId() + "; oldRole=" + oldRole + "; newRole=" + newRole);
        return ResponseEntity.ok(safeUser(target, newRole));
    }

    private Map<String, Object> safeUser(User user, String workspaceRole) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getName() != null ? user.getName() : user.getEmail(),
                "role", workspaceRole != null ? workspaceRole : "VIEWER",
                "global_role", user.getRole() != null ? user.getRole() : "VIEWER",
                "status", user.isActive() ? "ACTIVE" : "INACTIVE",
                "account_id", user.getWorkspaceId() != null ? user.getWorkspaceId() : ""
        );
    }

    public record RoleUpdateRequest(@NotBlank String role) {}
}
