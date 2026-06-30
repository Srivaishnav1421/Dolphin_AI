package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.Subscription;
import com.chubby.dolphin.repository.SubscriptionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * DA-050 — Subscription Degradation Filter.
 *
 * PAST_DUE: Allows CRM, Leads, Analytics, existing automations.
 *           Blocks: campaign launch, WhatsApp bulk, new automation creation, team expansion.
 *
 * SUSPENDED: Full read-only mode.
 *            Blocks ALL mutation methods: POST, PUT, PATCH, DELETE.
 */
@Slf4j
public class SubscriptionStatusFilter extends OncePerRequestFilter {

    private final SubscriptionRepository subscriptionRepository;

    // Mutation-triggering HTTP methods that are blocked for SUSPENDED workspaces
    private static final Set<String> MUTATION_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name()
    );

    // Paths blocked for PAST_DUE workspaces (growth-expansion operations)
    private static final Set<String> PAST_DUE_BLOCKED_PREFIXES = Set.of(
            "/api/campaigns",         // New campaign launches
            "/api/automation",        // New automation creation
            "/api/whatsapp/bulk",     // WhatsApp bulk sends
            "/api/users/invite"       // Team expansion
    );

    // Public endpoints that are always allowed through regardless of subscription status
    private static final Set<String> ALWAYS_ALLOWED_PREFIXES = Set.of(
            "/api/auth",
            "/api/health",
            "/api/billing/razorpay/webhook",
            "/api/invoices",
            "/actuator"
    );

    public SubscriptionStatusFilter(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Always let public paths through
        if (isAlwaysAllowed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only check authenticated requests (workspaceId in context)
        String workspaceId = TenantContext.getCurrentTenant();
        if (workspaceId == null || workspaceId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByWorkspaceId(workspaceId);
        if (subscriptionOpt.isEmpty()) {
            // No subscription record — allow through; entitlements engine will handle feature gating
            filterChain.doFilter(request, response);
            return;
        }

        String status = subscriptionOpt.get().getStatus();

        if ("SUSPENDED".equalsIgnoreCase(status)) {
            if (MUTATION_METHODS.contains(method)) {
                log.warn("🚫 [SUSPENDED] Mutation blocked for workspace={}, method={}, path={}", workspaceId, method, path);
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Workspace suspended. Account is in read-only mode. Please contact support to reactivate.");
                return;
            }
        } else if ("PAST_DUE".equalsIgnoreCase(status)) {
            if (isPastDueBlocked(path, method)) {
                log.warn("⚠️ [PAST_DUE] Growth feature blocked for workspace={}, path={}", workspaceId, path);
                writeError(response, HttpServletResponse.SC_PAYMENT_REQUIRED,
                        "Subscription payment overdue. Renew to continue growth automation.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAlwaysAllowed(String path) {
        return ALWAYS_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isPastDueBlocked(String path, String method) {
        // Only block write operations on restricted paths
        if (!MUTATION_METHODS.contains(method)) {
            return false;
        }
        return PAST_DUE_BLOCKED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writeError(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"" + message + "\", \"upgradeRequired\": true}");
    }
}
