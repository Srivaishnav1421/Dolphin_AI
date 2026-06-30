package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.RuntimeEnvironmentService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemRuntimeControllerTest {

    @Test
    void devAllowsAuthenticatedUsers() {
        RuntimeEnvironmentService service = mock(RuntimeEnvironmentService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(service.runtimeIdentity()).thenReturn(Map.of("profile", "dev"));

        SystemRuntimeController controller = new SystemRuntimeController(service, environment);
        ResponseEntity<Map<String, Object>> response = controller.runtime(auth("ROLE_OWNER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("dev", response.getBody().get("profile"));
    }

    @Test
    void productionRejectsNonSystemAdmin() {
        RuntimeEnvironmentService service = mock(RuntimeEnvironmentService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        SystemRuntimeController controller = new SystemRuntimeController(service, environment);
        ResponseEntity<Map<String, Object>> response = controller.runtime(auth("ROLE_OWNER"));

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void productionAllowsSystemAdmin() {
        RuntimeEnvironmentService service = mock(RuntimeEnvironmentService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(service.runtimeIdentity()).thenReturn(Map.of("profile", "prod"));

        SystemRuntimeController controller = new SystemRuntimeController(service, environment);
        ResponseEntity<Map<String, Object>> response = controller.runtime(auth("ROLE_SYSTEM_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("prod", response.getBody().get("profile"));
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken("user@example.com", null, List.of(new SimpleGrantedAuthority(role)));
    }
}
