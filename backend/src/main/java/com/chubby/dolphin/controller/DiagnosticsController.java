package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.BusinessLlmFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostics")
@Slf4j
public class DiagnosticsController {

    private final JdbcTemplate jdbcTemplate;
    private final BusinessLlmFacadeService llmRouter;
    private final OptionalDependencies optionalDependencies;

    public DiagnosticsController(JdbcTemplate jdbcTemplate,
                                 BusinessLlmFacadeService llmRouter,
                                 OptionalDependencies optionalDependencies) {
        this.jdbcTemplate = jdbcTemplate;
        this.llmRouter = llmRouter;
        this.optionalDependencies = optionalDependencies;
    }

    /**
     * Complete pro-active health diagnostic report including DB, Redis, RabbitMQ, and LLM Latencies.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> systemDiagnostics() {
        log.info("🩺 Initiating system-wide health and connectivity diagnostics...");

        Map<String, Object> report = new HashMap<>();
        report.put("timestamp", Instant.now().toString());

        // 1. Database Diagnostic
        boolean dbHealthy = false;
        long dbLatencyMs = -1;
        try {
            Instant start = Instant.now();
            jdbcTemplate.execute("SELECT 1");
            dbLatencyMs = Duration.between(start, Instant.now()).toMillis();
            dbHealthy = true;
        } catch (Exception e) {
            log.error("❌ Database connection test failed: {}", e.getMessage());
        }
        report.put("database", Map.of(
                "status", dbHealthy ? "HEALTHY" : "UNHEALTHY",
                "latency_ms", dbLatencyMs
        ));

        // 2. Redis Diagnostic
        boolean redisHealthy = false;
        try {
            if (optionalDependencies.isRedisConnected()) {
                redisHealthy = true;
            }
        } catch (Exception ignored) {}
        report.put("redis", Map.of(
                "status", redisHealthy ? "CONNECTED" : "DISCONNECTED"
        ));

        // 3. RabbitMQ Diagnostic
        boolean rabbitHealthy = false;
        try {
            if (optionalDependencies.isRabbitConnected()) {
                rabbitHealthy = true;
            }
        } catch (Exception ignored) {}
        report.put("rabbitmq", Map.of(
                "status", rabbitHealthy ? "CONNECTED" : "DISCONNECTED"
        ));

        // 4. LLM Routing Provider Latency Check
        Map<String, Object> llmStatus = new HashMap<>(llmRouter.getProviderStatus());
        long llmLatencyMs = -1;
        boolean llmHealthy = false;
        try {
            Instant start = Instant.now();
            BusinessLlmFacadeService.LlmResponse response = llmRouter.ask("ping");
            if (response != null && response.isAvailable()) {
                llmLatencyMs = Duration.between(start, Instant.now()).toMillis();
                llmHealthy = true;
            }
        } catch (Exception e) {
            log.warn("⚠️ Diagnostics ping to active LLM failed: {}", e.getMessage());
        }
        llmStatus.put("status", llmHealthy ? "HEALTHY" : "DEGRADED");
        llmStatus.put("latency_ms", llmLatencyMs);
        report.put("llm_router", llmStatus);

        // System state summary
        boolean overallHealthy = dbHealthy && llmHealthy;
        report.put("system_status", overallHealthy ? "ALL_SYSTEMS_OPERATIONAL" : "DEGRADED_PERFORMANCE");

        return ResponseEntity.ok(report);
    }

    /**
     * Utility component to handle Rabbit/Redis connectivity safely to prevent startup failure in unit tests.
     */
    @org.springframework.stereotype.Component
    public static class OptionalDependencies {
        private final org.springframework.beans.factory.ObjectProvider<RedisConnectionFactory> redisFactory;

        public OptionalDependencies(org.springframework.beans.factory.ObjectProvider<RedisConnectionFactory> redisFactory) {
            this.redisFactory = redisFactory;
        }

        public boolean isRedisConnected() {
            RedisConnectionFactory factory = redisFactory.getIfAvailable();
            if (factory == null) return false;
            try (var conn = factory.getConnection()) {
                return "PONG".equalsIgnoreCase(conn.ping());
            } catch (Exception e) {
                return false;
            }
        }

        public boolean isRabbitConnected() {
            // Test standard RabbitMQ port 5672 via raw socket to avoid classpath AMQP dependency problems
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("localhost", 5672), 500);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
