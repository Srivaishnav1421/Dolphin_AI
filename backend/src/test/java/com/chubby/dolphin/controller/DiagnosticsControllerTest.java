package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.BusinessLlmFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DiagnosticsControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private BusinessLlmFacadeService llmRouter;
    @Mock private DiagnosticsController.OptionalDependencies optionalDependencies;

    private DiagnosticsController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new DiagnosticsController(jdbcTemplate, llmRouter, optionalDependencies);
    }

    @Test
    public void testDiagnosticsReturnsCompleteReport() {
        when(optionalDependencies.isRedisConnected()).thenReturn(true);
        when(optionalDependencies.isRabbitConnected()).thenReturn(true);

        BusinessLlmFacadeService.LlmResponse llmResponse = new BusinessLlmFacadeService.LlmResponse("pong text", "GEMINI", "gemini-1.5-pro");
        when(llmRouter.ask("ping")).thenReturn(llmResponse);
        when(llmRouter.getProviderStatus()).thenReturn(Map.of("active_provider", "GEMINI"));

        ResponseEntity<Map<String, Object>> response = controller.systemDiagnostics();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("database"));
        assertTrue(body.containsKey("redis"));
        assertTrue(body.containsKey("rabbitmq"));
        assertTrue(body.containsKey("llm_router"));
        assertEquals("ALL_SYSTEMS_OPERATIONAL", body.get("system_status"));

        verify(jdbcTemplate, times(1)).execute("SELECT 1");
        verify(llmRouter, times(1)).ask("ping");
    }
}
