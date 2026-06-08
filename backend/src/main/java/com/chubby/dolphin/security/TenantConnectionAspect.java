package com.chubby.dolphin.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.Connection;

/**
 * Tenant Connection Aspect — Aspect-oriented binding to inject the active
 * workspace/tenant ID into the PostgreSQL transaction context (app.workspace_id)
 * to enforce Row-Level Security (RLS) policies at the database layer.
 *
 * On H2 (dev profile), the SET LOCAL call is skipped entirely to prevent
 * SQL 42001 syntax errors flooding the log.
 */
@Aspect
@Component
@Order(1)
@Slf4j
public class TenantConnectionAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    // Resolved once at startup — avoids per-request metadata calls
    private boolean isPostgres = false;

    @PostConstruct
    public void detectDialect() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            isPostgres = productName != null && productName.toLowerCase().contains("postgresql");
            log.info("🗄️ TenantConnectionAspect dialect detected: {} — RLS enforcement {}",
                    productName, isPostgres ? "ENABLED" : "DISABLED (H2/dev mode)");
        } catch (Exception e) {
            log.warn("Could not detect database dialect, RLS enforcement disabled: {}", e.getMessage());
        }
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional) || " +
            "execution(* org.springframework.data.repository.Repository+.*(..))")
    public void setTenantSessionVariable() {
        // Skip entirely on H2 — SET LOCAL app.workspace_id is a PostgreSQL-only extension
        if (!isPostgres) return;

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                entityManager.createNativeQuery("SET LOCAL app.workspace_id = :tenantId")
                        .setParameter("tenantId", tenantId)
                        .executeUpdate();
                log.debug("🔑 Bound app.workspace_id = {} to PostgreSQL transaction context.", tenantId);
            } catch (Exception e) {
                log.warn("Failed to bind workspace RLS parameter: {}", e.getMessage());
            }
        }
    }
}
