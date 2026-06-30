package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.RuntimeEnvironmentService;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemRuntimeController {

    private final RuntimeEnvironmentService runtimeEnvironmentService;
    private final Environment environment;

    public SystemRuntimeController(RuntimeEnvironmentService runtimeEnvironmentService, Environment environment) {
        this.runtimeEnvironmentService = runtimeEnvironmentService;
        this.environment = environment;
    }

    @GetMapping("/runtime")
    public ResponseEntity<Map<String, Object>> runtime(Authentication authentication) {
        if (isProduction() && !isSystemAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "SYSTEM_ADMIN role required"));
        }
        return ResponseEntity.ok(runtimeEnvironmentService.runtimeIdentity());
    }

    private boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private boolean isSystemAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SYSTEM_ADMIN".equals(authority.getAuthority()));
    }
}
