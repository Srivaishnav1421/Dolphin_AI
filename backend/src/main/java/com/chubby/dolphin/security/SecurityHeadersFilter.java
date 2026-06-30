package com.chubby.dolphin.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class SecurityHeadersFilter implements Filter {

    @Value("${app.security.content-security-policy}")
    private String contentSecurityPolicy;

    @Value("${app.security.hsts-enabled:false}")
    private boolean hstsEnabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            if (request instanceof HttpServletRequest httpRequest) {
                String path = httpRequest.getRequestURI();
                if (path.contains("/h2-console")) {
                    httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
                } else {
                    httpResponse.setHeader("X-Frame-Options", "DENY");
                }
            } else {
                httpResponse.setHeader("X-Frame-Options", "DENY");
            }
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            httpResponse.setHeader("Content-Security-Policy", contentSecurityPolicy);
            httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(self)");
            if (hstsEnabled) {
                httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
        }
        chain.doFilter(request, response);
    }
}
