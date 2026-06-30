package com.chubby.dolphin.security;

import com.chubby.dolphin.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class EdgeAbuseProtectionFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    @Value("${app.security.edge-rate-limit-enabled:true}")
    private boolean enabled;

    public EdgeAbuseProtectionFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = resolveIdentity(request);
        if (!rateLimiterService.isAllowed(identity, RateLimiterService.LimitType.EDGE)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests\",\"message\":\"Please slow down and try again.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(method)
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/api/health")
                || path.startsWith("/api/billing/razorpay/webhook");
    }

    private String resolveIdentity(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor != null && !forwardedFor.isBlank()
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        String tenant = TenantContext.getCurrentTenant();
        return (tenant != null && !tenant.isBlank()) ? tenant + ":" + ip : ip;
    }
}
