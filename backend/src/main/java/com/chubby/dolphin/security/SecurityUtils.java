package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private final UserRepository userRepo;

    public SecurityUtils(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /** Returns the currently authenticated user's database accountId (Active Workspace ID) */
    public String currentWorkspaceId() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return currentUser().getWorkspaceId();
    }

    @Deprecated
    public String currentAccountId() {
        return currentWorkspaceId();
    }

    /** Returns the currently authenticated User entity */
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));
    }

    public String currentEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
