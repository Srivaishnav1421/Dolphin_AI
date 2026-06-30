package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.UserWorkspaceRole;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.UserWorkspaceRoleRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.security.TenantAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final UserWorkspaceRoleRepository roleRepository;
    private final SecurityUtils securityUtils;
    private final TenantAccessService tenantAccessService;

    public WorkspaceController(WorkspaceRepository workspaceRepository,
                               UserWorkspaceRoleRepository roleRepository,
                               SecurityUtils securityUtils,
                               TenantAccessService tenantAccessService) {
        this.workspaceRepository = workspaceRepository;
        this.roleRepository = roleRepository;
        this.securityUtils = securityUtils;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        User user = securityUtils.currentUser();
        if (user.getOrganization() == null) {
            return ResponseEntity.ok(List.of());
        }

        String activeWorkspaceId = securityUtils.currentWorkspaceId();
        List<Map<String, Object>> workspaces = workspaceRepository.findByOrganizationId(user.getOrganization().getId()).stream()
                .filter(workspace -> tenantAccessService.canAccessWorkspace(user.getEmail(), workspace.getId()))
                .map(workspace -> Map.<String, Object>of(
                        "id", workspace.getId(),
                        "name", workspace.getName(),
                        "organization_id", user.getOrganization().getId(),
                        "role", tenantAccessService.workspaceRole(user.getEmail(), workspace.getId()).orElse("VIEWER"),
                        "active", workspace.getId().equals(activeWorkspaceId),
                        "created_at", workspace.getCreatedAt() != null ? workspace.getCreatedAt().toString() : ""
                ))
                .toList();
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        User user = securityUtils.currentUser();
        if (user.getOrganization() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not attached to an organization"));
        }

        String name = clean(body.get("name"), 120);
        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Client workspace name is required"));
        }

        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setOrganization(user.getOrganization());
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        Workspace saved = workspaceRepository.save(workspace);

        UserWorkspaceRole ownerRole = new UserWorkspaceRole();
        ownerRole.setUser(user);
        ownerRole.setWorkspace(saved);
        ownerRole.setRole("OWNER");
        roleRepository.save(ownerRole);

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "organization_id", user.getOrganization().getId()
        ));
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
