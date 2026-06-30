package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.UserWorkspaceRoleRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
public class TenantAccessService {

    private static final Set<String> ORGANIZATION_WIDE_ROLES = Set.of(
            "PLATFORM_ADMIN", "SYSTEM_ADMIN", "ORG_OWNER", "ORG_ADMIN", "OWNER", "ADMIN"
    );

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserWorkspaceRoleRepository userWorkspaceRoleRepository;

    public TenantAccessService(UserRepository userRepository,
                               WorkspaceRepository workspaceRepository,
                               UserWorkspaceRoleRepository userWorkspaceRoleRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.userWorkspaceRoleRepository = userWorkspaceRoleRepository;
    }

    public boolean canAccessWorkspace(String email, String workspaceId) {
        if (email == null || email.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return false;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isActive() || user.getOrganization() == null) {
            return false;
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null || workspace.getOrganization() == null) {
            return false;
        }

        if (!user.getOrganization().getId().equals(workspace.getOrganization().getId())) {
            return false;
        }

        String globalRole = normalizeRole(user.getRole());
        if (ORGANIZATION_WIDE_ROLES.contains(globalRole)) {
            return true;
        }

        if (workspaceId.equals(user.getWorkspaceId())) {
            return true;
        }

        return userWorkspaceRoleRepository.findByUserIdAndWorkspaceId(user.getId(), workspaceId).isPresent();
    }

    public Optional<String> workspaceRole(String email, String workspaceId) {
        if (!canAccessWorkspace(email, workspaceId)) {
            return Optional.empty();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        return userWorkspaceRoleRepository.findByUserIdAndWorkspaceId(user.getId(), workspaceId)
                .map(role -> normalizeRole(role.getRole()))
                .or(() -> Optional.of(normalizeRole(user.getRole())));
    }

    public Workspace requireWorkspaceAccess(String email, String workspaceId) {
        if (!canAccessWorkspace(email, workspaceId)) {
            throw new TenantAccessDeniedException("Workspace access denied");
        }
        return workspaceRepository.findById(workspaceId).orElseThrow();
    }

    public static class TenantAccessDeniedException extends RuntimeException {
        public TenantAccessDeniedException(String message) {
            super(message);
        }
    }

    private String normalizeRole(String role) {
        return role == null ? "VIEWER" : role.trim().toUpperCase();
    }
}
