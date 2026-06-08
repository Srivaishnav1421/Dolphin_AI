package com.chubby.dolphin.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class SecurityHeadersFilter implements Filter {

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
            httpResponse.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:; connect-src 'self' http://localhost:* ws://localhost:*");
        }
        chain.doFilter(request, response);
    }
}
