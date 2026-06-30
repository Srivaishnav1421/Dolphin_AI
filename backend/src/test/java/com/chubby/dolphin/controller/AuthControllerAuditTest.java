package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.RefreshToken;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.security.JwtUtil;
import com.chubby.dolphin.security.TenantAccessService;
import com.chubby.dolphin.security.TotpService;
import com.chubby.dolphin.service.RateLimiterService;
import com.chubby.dolphin.service.RefreshTokenService;
import com.chubby.dolphin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerAuditTest {

    private AuthenticationManager authManager;
    private JwtUtil jwtUtil;
    private UserRepository userRepo;
    private UserService userService;
    private PasswordEncoder encoder;
    private RefreshTokenService refreshTokenService;
    private RateLimiterService rateLimiter;
    private WorkspaceRepository workspaceRepo;
    private TotpService totpService;
    private TenantAccessService tenantAccessService;
    private JdbcTemplate jdbcTemplate;
    private AuditLogService auditLogService;
    private AuthController controller;
    private User user;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        authManager = mock(AuthenticationManager.class);
        jwtUtil = mock(JwtUtil.class);
        userRepo = mock(UserRepository.class);
        userService = mock(UserService.class);
        encoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        rateLimiter = mock(RateLimiterService.class);
        workspaceRepo = mock(WorkspaceRepository.class);
        totpService = mock(TotpService.class);
        tenantAccessService = mock(TenantAccessService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        auditLogService = mock(AuditLogService.class);

        controller = new AuthController(authManager, jwtUtil, userRepo, userService, encoder,
                refreshTokenService, rateLimiter, workspaceRepo, totpService, tenantAccessService,
                jdbcTemplate, auditLogService);

        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setName("Org 1");
        organization.setPlan("AGENCY");

        workspace = new Workspace();
        workspace.setId("ws-1");
        workspace.setName("Workspace 1");
        workspace.setOrganization(organization);

        user = new User();
        user.setId("user-1");
        user.setEmail("owner@dolphin.test");
        user.setName("Owner");
        user.setRole("OWNER");
        user.setAccountId("ws-1");
        user.setOrganization(organization);
        user.setActive(true);

        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(rateLimiter.isAllowed(anyString(), eq(RateLimiterService.LimitType.LOGIN))).thenReturn(true);
        when(userService.findByEmail(user.getEmail())).thenReturn(user);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tenantAccessService.canAccessWorkspace(user.getEmail(), "ws-1")).thenReturn(true);
        when(workspaceRepo.findByOrganizationId("org-1")).thenReturn(List.of(workspace));
        when(tenantAccessService.workspaceRole(user.getEmail(), "ws-1")).thenReturn(Optional.of("OWNER"));
        when(jwtUtil.generateToken(eq(user.getEmail()), eq("OWNER"), eq("ws-1"), eq("org-1"), eq("user-1")))
                .thenReturn("access-token");
        when(refreshTokenService.create(user)).thenReturn(refreshToken("refresh-token"));
    }

    @Test
    void successfulLoginCreatesSafeAuditLog() {
        ResponseEntity<?> response = controller.login(new AuthController.LoginRequest(user.getEmail(), "password"), request());

        assertEquals(200, response.getStatusCode().value());
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-1"),
                eq("AUTH_LOGIN_SUCCESS"), eq(true), eq("10.0.0.1"), eq("JUnit"), eq("Login succeeded"));
    }

    @Test
    void failedLoginCreatesSafeAuditLog() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<?> response = controller.login(new AuthController.LoginRequest(user.getEmail(), "wrong"), request());

        assertEquals(401, response.getStatusCode().value());
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-1"),
                eq("AUTH_LOGIN_FAILED"), eq(false), eq("10.0.0.1"), eq("JUnit"), eq("Invalid credentials"));
    }

    @Test
    void refreshRotationCreatesAuditLog() {
        RefreshToken oldToken = refreshToken("old-refresh");
        when(refreshTokenService.validate("old-refresh")).thenReturn(Optional.of(oldToken));
        when(refreshTokenService.rotate(oldToken, user)).thenReturn(refreshToken("new-refresh"));

        ResponseEntity<?> response = controller.refresh(Map.of("refresh_token", "old-refresh"), request());

        assertEquals(200, response.getStatusCode().value());
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-1"),
                eq("AUTH_REFRESH_ROTATED"), eq(true), eq("10.0.0.1"), eq("JUnit"), eq("Refresh token rotated"));
    }

    @Test
    void logoutCreatesAuditAndRevocationLogs() {
        when(refreshTokenService.revokeAll("user-1")).thenReturn(2);
        Principal principal = () -> user.getEmail();

        ResponseEntity<?> response = controller.logout(null, principal, request());

        assertEquals(200, response.getStatusCode().value());
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-1"),
                eq("AUTH_LOGOUT"), eq(true), eq("10.0.0.1"), eq("JUnit"), eq("Logout succeeded"));
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-1"),
                eq("AUTH_REFRESH_TOKENS_REVOKED"), eq(true), eq("10.0.0.1"), eq("JUnit"),
                eq("Revoked refresh token count=2"));
    }

    @Test
    void workspaceSwitchRejectsUnauthorizedWorkspaceAndAudits() {
        when(tenantAccessService.canAccessWorkspace(user.getEmail(), "ws-denied")).thenReturn(false);
        Principal principal = () -> user.getEmail();

        ResponseEntity<?> response = controller.switchWorkspace(Map.of("workspace_id", "ws-denied"), principal, request());

        assertEquals(403, response.getStatusCode().value());
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-1"),
                eq("AUTH_WORKSPACE_SWITCH_FAILED"), eq(false), eq("10.0.0.1"), eq("JUnit"),
                eq("Requested workspace denied: ws-denied"));
    }

    @Test
    void workspaceSwitchValidatesMembershipAndAuditsSuccess() {
        Workspace workspaceTwo = new Workspace();
        workspaceTwo.setId("ws-2");
        workspaceTwo.setName("Workspace 2");
        workspaceTwo.setOrganization(user.getOrganization());
        when(tenantAccessService.canAccessWorkspace(user.getEmail(), "ws-2")).thenReturn(true);
        when(workspaceRepo.findByOrganizationId("org-1")).thenReturn(List.of(workspace, workspaceTwo));
        when(tenantAccessService.workspaceRole(user.getEmail(), "ws-2")).thenReturn(Optional.of("MANAGER"));
        when(jwtUtil.generateToken(eq(user.getEmail()), eq("OWNER"), eq("ws-2"), eq("org-1"), eq("user-1")))
                .thenReturn("access-token-ws-2");
        Principal principal = () -> user.getEmail();

        ResponseEntity<?> response = controller.switchWorkspace(Map.of("workspace_id", "ws-2"), principal, request());

        assertEquals(200, response.getStatusCode().value());
        verify(tenantAccessService, atLeastOnce()).canAccessWorkspace(user.getEmail(), "ws-2");
        verify(auditLogService).recordAuthEvent(eq(user), eq(user.getEmail()), eq("org-1"), eq("ws-2"),
                eq("AUTH_WORKSPACE_SWITCHED"), eq(true), eq("10.0.0.1"), eq("JUnit"),
                eq("oldWorkspaceId=ws-1; newWorkspaceId=ws-2"));
    }

    private RefreshToken refreshToken(String token) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setEmail(user.getEmail());
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
        return refreshToken;
    }

    private HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        return request;
    }
}
