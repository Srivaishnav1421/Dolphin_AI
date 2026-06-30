package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.RefreshToken;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.security.JwtUtil;
import com.chubby.dolphin.security.TenantAccessService;
import com.chubby.dolphin.security.TotpService;
import com.chubby.dolphin.service.RateLimiterService;
import com.chubby.dolphin.service.RefreshTokenService;
import com.chubby.dolphin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil               jwtUtil;
    private final UserRepository        userRepo;
    private final UserService           userService;
    private final PasswordEncoder       encoder;
    private final RefreshTokenService   refreshTokenService;
    private final RateLimiterService    rateLimiter;
    private final WorkspaceRepository   workspaceRepo;
    private final TotpService           totpService;
    private final TenantAccessService   tenantAccessService;
    private final JdbcTemplate          jdbcTemplate;
    private final AuditLogService       auditLogService;

    public AuthController(AuthenticationManager authManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepo,
                          UserService userService,
                          PasswordEncoder encoder,
                          RefreshTokenService refreshTokenService,
                          RateLimiterService rateLimiter,
                          WorkspaceRepository workspaceRepo,
                          TotpService totpService,
                          TenantAccessService tenantAccessService,
                          JdbcTemplate jdbcTemplate,
                          AuditLogService auditLogService) {
        this.authManager = authManager;
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.userService = userService;
        this.encoder = encoder;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.workspaceRepo = workspaceRepo;
        this.totpService = totpService;
        this.tenantAccessService = tenantAccessService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    // ── Login ─────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        if (!databaseAvailable()) {
            return ResponseEntity.status(503).body(ApiError.of("Database connection required. Please start PostgreSQL to login."));
        }

        // Rate limit: 5 login attempts/minute per IP
        if (!rateLimiter.isAllowed(http.getRemoteAddr(), RateLimiterService.LimitType.LOGIN)) {
            return ResponseEntity.status(429).body(ApiError.of("Too many login attempts. Try again in 1 minute."));
        }
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
            );
        } catch (BadCredentialsException e) {
            userRepo.findByEmail(req.getEmail()).ifPresentOrElse(
                    user -> auditAuth(user, "AUTH_LOGIN_FAILED", false, http, "Invalid credentials"),
                    () -> auditLogService.recordAuthEvent(null, req.getEmail(), null, null,
                            "AUTH_LOGIN_FAILED", false, http.getRemoteAddr(), http.getHeader("User-Agent"), "Invalid credentials")
            );
            return ResponseEntity.status(401).body(ApiError.of("Invalid email or password"));
        }

        User user = userService.findByEmail(req.getEmail());
        ResponseEntity<?> workspaceValidation = validateActiveWorkspace(user);
        if (workspaceValidation != null) {
            return workspaceValidation;
        }
        if (user.isTwoFactorEnabled()) {
            if (req.getTotpCode() == null || req.getTotpCode().isBlank()) {
                return ResponseEntity.status(428).body(Map.of(
                    "error", "Two-factor code required",
                    "two_factor_required", true
                ));
            }
            if (!totpService.verifyCode(user.getTwoFactorSecret(), req.getTotpCode())) {
                auditAuth(user, "AUTH_LOGIN_FAILED", false, http, "Invalid two-factor code");
                return ResponseEntity.status(401).body(ApiError.of("Invalid two-factor code"));
            }
        }

        String accessToken   = generateAccessToken(user, user.getAccountId());
        RefreshToken refresh = refreshTokenService.create(user);
        userService.recordLogin(user.getEmail(), http.getRemoteAddr());
        auditAuth(user, "AUTH_LOGIN_SUCCESS", true, http, "Login succeeded");
        log.info("✅ Login: {} [{}]", user.getEmail(), http.getRemoteAddr());

        return ResponseEntity.ok(buildAuthResponse(user, accessToken, refresh.getToken()));
    }

    // ── Refresh Access Token ──────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body, HttpServletRequest http) {
        String refreshToken = body.get("refresh_token");
        if (refreshToken == null) return ResponseEntity.badRequest().body(ApiError.of("refresh_token required"));

        java.util.Optional<RefreshToken> rtOpt = refreshTokenService.validate(refreshToken);
        if (rtOpt.isPresent()) {
            RefreshToken rt = rtOpt.get();
            User user            = userService.findByEmail(rt.getEmail());
            ResponseEntity<?> workspaceValidation = validateActiveWorkspace(user);
            if (workspaceValidation != null) {
                return workspaceValidation;
            }
            String newAccess     = generateAccessToken(user, user.getAccountId());
            RefreshToken rotated = refreshTokenService.rotate(rt, user); // one-time-use rotation
            auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                    "AUTH_REFRESH_ROTATED", true, http.getRemoteAddr(), http.getHeader("User-Agent"), "Refresh token rotated");
            log.info("🔄 Token refreshed for {}", user.getEmail());
            return ResponseEntity.ok(buildAuthResponse(user, newAccess, rotated.getToken()));
        } else {
            auditLogService.recordAuthEvent(null, "unknown", null, null,
                    "AUTH_REFRESH_FAILED", false, http.getRemoteAddr(), http.getHeader("User-Agent"), "Refresh token invalid or expired");
            return ResponseEntity.status(401).body(ApiError.of("Refresh token invalid or expired. Please login again."));
        }
    }

    // ── Logout ───────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body,
                                    java.security.Principal principal,
                                    HttpServletRequest http) {
        if (principal != null) {
            User user = userService.findByEmail(principal.getName());
            int revoked = refreshTokenService.revokeAll(user.getId());
            rateLimiter.clearUser(user.getId());
            auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                    "AUTH_LOGOUT", true, http.getRemoteAddr(), http.getHeader("User-Agent"), "Logout succeeded");
            auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                    "AUTH_REFRESH_TOKENS_REVOKED", true, http.getRemoteAddr(), http.getHeader("User-Agent"), "Revoked refresh token count=" + revoked);
            log.info("👋 Logout: {}", user.getEmail());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── Current user info ─────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> me(java.security.Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return ResponseEntity.ok(Map.of(
            "id",         user.getId(),
            "name",       user.getName(),
            "email",      user.getEmail(),
            "role",       user.getRole(),
            "account_id", user.getAccountId(),
            "organization_id", user.getOrganization() != null ? user.getOrganization().getId() : "",
            "two_factor_enabled", user.isTwoFactorEnabled(),
            "last_login", user.getLastLogin() != null ? user.getLastLogin().toString() : "",
            "workspaces", workspaceOptions(user)
        ));
    }

    // ── Two-factor authentication ────────────────────────────────
    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setupTwoFactor(java.security.Principal principal) {
        User user = userService.findByEmail(principal.getName());
        String secret = user.getTwoFactorSecret();
        if (secret == null || secret.isBlank() || !user.isTwoFactorEnabled()) {
            secret = totpService.generateSecret();
            user.setTwoFactorSecret(secret);
            userRepo.save(user);
        }

        return ResponseEntity.ok(Map.of(
            "enabled", user.isTwoFactorEnabled(),
            "secret", secret,
            "otpauth_url", totpService.provisioningUri("DolphinAI", user.getEmail(), secret)
        ));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<?> enableTwoFactor(@RequestBody Map<String, String> body,
                                             java.security.Principal principal) {
        User user = userService.findByEmail(principal.getName());
        String secret = user.getTwoFactorSecret();
        if (secret == null || secret.isBlank()) {
            return ResponseEntity.badRequest().body(ApiError.of("Run two-factor setup before enabling it"));
        }
        if (!totpService.verifyCode(secret, body.get("code"))) {
            return ResponseEntity.status(401).body(ApiError.of("Invalid two-factor code"));
        }

        user.setTwoFactorEnabled(true);
        user.setTwoFactorEnabledAt(LocalDateTime.now());
        userRepo.save(user);
        int revoked = refreshTokenService.revokeAll(user.getId());
        auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                "AUTH_REFRESH_TOKENS_REVOKED", true, null, null, "Two-factor enabled; revoked refresh token count=" + revoked);
        return ResponseEntity.ok(Map.of(
            "message", "Two-factor authentication enabled. Please login again.",
            "enabled", true
        ));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disableTwoFactor(@RequestBody Map<String, String> body,
                                              java.security.Principal principal) {
        User user = userService.findByEmail(principal.getName());
        if (user.isTwoFactorEnabled() && !totpService.verifyCode(user.getTwoFactorSecret(), body.get("code"))) {
            return ResponseEntity.status(401).body(ApiError.of("Invalid two-factor code"));
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabledAt(null);
        userRepo.save(user);
        int revoked = refreshTokenService.revokeAll(user.getId());
        auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                "AUTH_REFRESH_TOKENS_REVOKED", true, null, null, "Two-factor disabled; revoked refresh token count=" + revoked);
        return ResponseEntity.ok(Map.of(
            "message", "Two-factor authentication disabled. Please login again.",
            "enabled", false
        ));
    }

    // ── Switch Active Workspace ──────────────────────────────────
    @PostMapping("/switch-workspace")
    public ResponseEntity<?> switchWorkspace(@RequestBody Map<String, String> body,
                                             java.security.Principal principal,
                                             HttpServletRequest http) {
        String newWorkspaceId = body.get("workspace_id");
        if (newWorkspaceId == null || newWorkspaceId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiError.of("workspace_id is required"));
        }

        User user = userService.findByEmail(principal.getName());
        String oldWorkspaceId = user.getAccountId();
        if (!tenantAccessService.canAccessWorkspace(user.getEmail(), newWorkspaceId)) {
            auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), oldWorkspaceId,
                    "AUTH_WORKSPACE_SWITCH_FAILED", false, http.getRemoteAddr(), http.getHeader("User-Agent"),
                    "Requested workspace denied: " + newWorkspaceId);
            return ResponseEntity.status(403).body(ApiError.of("Access denied to this workspace"));
        }

        // Set user's active workspace
        user.setAccountId(newWorkspaceId);
        userRepo.save(user);

        // Generate dynamic workspace JWT claim
        String newAccessToken = generateAccessToken(user, newWorkspaceId);
        RefreshToken refresh  = refreshTokenService.create(user);
        auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), newWorkspaceId,
                "AUTH_WORKSPACE_SWITCHED", true, http.getRemoteAddr(), http.getHeader("User-Agent"),
                "oldWorkspaceId=" + oldWorkspaceId + "; newWorkspaceId=" + newWorkspaceId);

        log.info("🔄 Active workspace switched to {} for user {}", newWorkspaceId, user.getEmail());
        return ResponseEntity.ok(buildAuthResponse(user, newAccessToken, refresh.getToken()));
    }

    // ── Change Password ───────────────────────────────────────────
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body,
                                             java.security.Principal principal) {
        String oldPwd = body.get("old_password");
        String newPwd = body.get("new_password");
        if (newPwd == null || newPwd.length() < 6)
            return ResponseEntity.badRequest().body(ApiError.of("New password must be at least 6 characters"));

        User user = userService.findByEmail(principal.getName());
        if (!encoder.matches(oldPwd, user.getPassword()))
            return ResponseEntity.badRequest().body(ApiError.of("Old password is incorrect"));

        user.setPassword(encoder.encode(newPwd));
        userRepo.save(user);
        // Revoke all refresh tokens — forces re-login everywhere
        int revoked = refreshTokenService.revokeAll(user.getId());
        auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                "AUTH_REFRESH_TOKENS_REVOKED", true, null, null, "Password changed; revoked refresh token count=" + revoked);
        log.info("🔑 Password changed: {}", user.getEmail());
        return ResponseEntity.ok(Map.of("message", "Password updated. Please login again."));
    }

    // ── Helpers ──────────────────────────────────────────────────
    private Map<String, Object> buildAuthResponse(User user, String accessToken, String refreshToken) {
        return Map.of(
            "access_token",  accessToken,
            "refresh_token", refreshToken,
            "token_type",    "Bearer",
            "expires_in",    900, // 15 minutes
            "user", Map.of(
                "id",         user.getId(),
                "name",       user.getName(),
                "email",      user.getEmail(),
                "role",       user.getRole(),
                "account_id", user.getAccountId(),
                "organization_id", user.getOrganization() != null ? user.getOrganization().getId() : "",
                "workspaces", workspaceOptions(user),
                "two_factor_enabled", user.isTwoFactorEnabled()
            )
        );
    }

    private String generateAccessToken(User user, String workspaceId) {
        return jwtUtil.generateToken(user.getEmail(), user.getRole(), workspaceId, organizationId(user), user.getId());
    }

    private void auditAuth(User user, String action, boolean success, HttpServletRequest request, String details) {
        auditLogService.recordAuthEvent(user, user.getEmail(), organizationId(user), user.getAccountId(),
                action, success, request.getRemoteAddr(), request.getHeader("User-Agent"), details);
    }

    private String organizationId(User user) {
        return user.getOrganization() != null ? user.getOrganization().getId() : null;
    }

    private java.util.List<Map<String, Object>> workspaceOptions(User user) {
        if (user.getOrganization() == null) {
            return java.util.List.of();
        }
        return workspaceRepo.findByOrganizationId(user.getOrganization().getId()).stream()
            .filter(workspace -> tenantAccessService.canAccessWorkspace(user.getEmail(), workspace.getId()))
            .map(workspace -> Map.<String, Object>of(
                "id", workspace.getId(),
                "name", workspace.getName(),
                "role", tenantAccessService.workspaceRole(user.getEmail(), workspace.getId()).orElse("VIEWER"),
                "active", workspace.getId().equals(user.getAccountId())
            ))
            .toList();
    }

    private boolean databaseAvailable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            log.error("Login blocked because database health check failed: {}", ex.getMessage());
            return false;
        }
    }

    private ResponseEntity<?> validateActiveWorkspace(User user) {
        String workspaceId = user.getAccountId();
        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.status(403).body(ApiError.of("Active workspace required. Ask an administrator to assign a workspace."));
        }
        if (!tenantAccessService.canAccessWorkspace(user.getEmail(), workspaceId)) {
            return ResponseEntity.status(403).body(ApiError.of("Active workspace is no longer available. Ask an administrator to review workspace access."));
        }
        return null;
    }

    // ── DTOs ─────────────────────────────────────────────────────
    public static class LoginRequest {
        @Email @NotBlank private String email;
        @NotBlank        private String password;
        private String totpCode;

        public LoginRequest() {}
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getTotpCode() { return totpCode; }
        public void setTotpCode(String totpCode) { this.totpCode = totpCode; }
    }

    record ApiError(String error) {
        static ApiError of(String msg) { return new ApiError(msg); }
    }
}
