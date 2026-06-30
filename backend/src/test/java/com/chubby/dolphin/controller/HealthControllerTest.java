package com.chubby.dolphin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {

    private JdbcTemplate jdbcTemplate;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new HealthController(jdbcTemplate);
    }

    @Test
    void databaseHealthReturnsUpWhenSelectOnePasses() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = controller.databaseHealth();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("database"));
        assertEquals(true, response.getBody().get("connected"));
    }

    @Test
    void databaseHealthReturnsUnavailableWhenQueryFails() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException("down"));

        ResponseEntity<Map<String, Object>> response = controller.databaseHealth();

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("database"));
        assertEquals(false, response.getBody().get("connected"));
        assertEquals("Database connection required. Please start PostgreSQL to continue.", response.getBody().get("message"));
    }
}
