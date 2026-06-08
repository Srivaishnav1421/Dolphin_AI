package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.RefreshToken;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.security.JwtUtil;
import com.chubby.dolphin.service.RateLimiterService;
import com.chubby.dolphin.service.RefreshTokenService;
import com.chubby.dolphin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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

    public AuthController(AuthenticationManager authManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepo,
                          UserService userService,
                          PasswordEncoder encoder,
                          RefreshTokenService refreshTokenService,
                          RateLimiterService rateLimiter,
                          WorkspaceRepository workspaceRepo) {
        this.authManager = authManager;
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.userService = userService;
        this.encoder = encoder;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.workspaceRepo = workspaceRepo;
    }

    // ── Login ─────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        // Rate limit: 5 login attempts/minute per IP
        if (!rateLimiter.isAllowed(http.getRemoteAddr(), RateLimiterService.LimitType.LOGIN)) {
            return ResponseEntity.status(429).body(ApiError.of("Too many login attempts. Try again in 1 minute."));
        }
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(ApiError.of("Invalid email or password"));
        }

        User user = userService.findByEmail(req.getEmail());
        String accessToken   = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getAccountId());
        RefreshToken refresh = refreshTokenService.create(user);
        userService.recordLogin(user.getEmail(), http.getRemoteAddr());
        log.info("✅ Login: {} [{}]", user.getEmail(), http.getRemoteAddr());

        return ResponseEntity.ok(buildAuthResponse(user, accessToken, refresh.getToken()));
    }

    // ── Refresh Access Token ──────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh_token");
        if (refreshToken == null) return ResponseEntity.badRequest().body(ApiError.of("refresh_token required"));

        java.util.Optional<RefreshToken> rtOpt = refreshTokenService.validate(refreshToken);
        if (rtOpt.isPresent()) {
            RefreshToken rt = rtOpt.get();
            User user            = userService.findByEmail(rt.getEmail());
            String newAccess     = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getAccountId());
            RefreshToken rotated = refreshTokenService.rotate(rt, user); // one-time-use rotation
            log.info("🔄 Token refreshed for {}", user.getEmail());
            return ResponseEntity.ok(buildAuthResponse(user, newAccess, rotated.getToken()));
        } else {
            return ResponseEntity.status(401).body(ApiError.of("Refresh token invalid or expired. Please login again."));
        }
    }

    // ── Logout ───────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body,
                                    java.security.Principal principal) {
        if (principal != null) {
            User user = userService.findByEmail(principal.getName());
            refreshTokenService.revokeAll(user.getId());
            rateLimiter.clearUser(user.getId());
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
            "last_login", user.getLastLogin() != null ? user.getLastLogin().toString() : ""
        ));
    }

    // ── Switch Active Workspace ──────────────────────────────────
    @PostMapping("/switch-workspace")
    public ResponseEntity<?> switchWorkspace(@RequestBody Map<String, String> body,
                                             java.security.Principal principal) {
        String newWorkspaceId = body.get("workspace_id");
        if (newWorkspaceId == null || newWorkspaceId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiError.of("workspace_id is required"));
        }

        User user = userService.findByEmail(principal.getName());
        Workspace workspace = workspaceRepo.findById(newWorkspaceId).orElse(null);

        // Security check: Verify that the workspace belongs to the user's parent organization
        if (workspace == null || user.getOrganization() == null ||
            !workspace.getOrganization().getId().equals(user.getOrganization().getId())) {
            return ResponseEntity.status(403).body(ApiError.of("Access denied to this workspace"));
        }

        // Set user's active workspace
        user.setAccountId(newWorkspaceId);
        userRepo.save(user);

        // Generate dynamic workspace JWT claim
        String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), newWorkspaceId);
        RefreshToken refresh  = refreshTokenService.create(user);

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
        refreshTokenService.revokeAll(user.getId());
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
                "account_id", user.getAccountId()
            )
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────
    public static class LoginRequest {
        @Email @NotBlank private String email;
        @NotBlank        private String password;

        public LoginRequest() {}
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    record ApiError(String error) {
        static ApiError of(String msg) { return new ApiError(msg); }
    }
}
