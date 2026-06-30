package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Filter — Upgraded to load full user authorities (roles) from the database.
 * This ensures @PreAuthorize("hasRole('ADMIN')") and similar annotations work correctly.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final TenantAccessService tenantAccessService;
    private static final String[] TENANT_OPTIONAL_PATHS = {
            "/api/auth/logout",
            "/api/auth/change-password",
            "/api/auth/2fa/setup",
            "/api/auth/2fa/enable",
            "/api/auth/2fa/disable",
            "/api/auth/me",
            "/api/system/runtime"
    };

    public JwtFilter(JwtUtil jwtUtil, UserService userService, TenantAccessService tenantAccessService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.tenantAccessService = tenantAccessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                String email = jwtUtil.extractEmail(token);
                String workspaceId = jwtUtil.extractWorkspaceId(token);
                String organizationId = jwtUtil.extractOrganizationId(token);
                String userId = jwtUtil.extractUserId(token);
                User user = userService.findByEmail(email);

                if (requiresTenantContext(request) && (workspaceId == null || workspaceId.isBlank())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Workspace context required\"}");
                    return;
                }

                if (workspaceId != null && !workspaceId.isBlank()) {
                    Workspace workspace;
                    try {
                        workspace = tenantAccessService.requireWorkspaceAccess(email, workspaceId);
                    } catch (TenantAccessService.TenantAccessDeniedException ex) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Workspace access denied\"}");
                        return;
                    }
                    String resolvedOrganizationId = workspace.getOrganization() != null ? workspace.getOrganization().getId() : null;
                    if (organizationId != null && !organizationId.isBlank()
                            && resolvedOrganizationId != null
                            && !organizationId.equals(resolvedOrganizationId)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Organization context mismatch\"}");
                        return;
                    }
                    organizationId = resolvedOrganizationId;
                }
                if ((organizationId == null || organizationId.isBlank()) && user.getOrganization() != null) {
                    organizationId = user.getOrganization().getId();
                }
                if (userId == null || userId.isBlank()) {
                    userId = user.getId();
                }
                TenantContext.setCurrentTenant(organizationId, workspaceId, userId);

                // Load full user with roles from DB for proper RBAC
                UserDetails userDetails = user;
                var auth = new UsernamePasswordAuthenticationToken(
                        email, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean requiresTenantContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return false;
        }
        for (String optionalPath : TENANT_OPTIONAL_PATHS) {
            if (path.equals(optionalPath)) {
                return false;
            }
        }
        return true;
    }
}
