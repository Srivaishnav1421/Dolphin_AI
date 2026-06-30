package com.chubby.dolphin.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        Instant startedAt = Instant.now();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            boolean connected = result != null && result == 1;
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of(
                    "database", connected ? "UP" : "DOWN",
                    "connected", connected,
                    "latency_ms", latencyMs
            ));
        } catch (Exception e) {
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.error("Database health check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of(
                    "database", "DOWN",
                    "connected", false,
                    "latency_ms", latencyMs,
                    "message", "Database connection required. Please start PostgreSQL to continue."
            ));
        }
    }
}
