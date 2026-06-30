package com.chubby.dolphin.config;

import com.chubby.dolphin.repository.SubscriptionRepository;
import com.chubby.dolphin.security.EdgeAbuseProtectionFilter;
import com.chubby.dolphin.security.JwtFilter;
import com.chubby.dolphin.security.SubscriptionStatusFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.http.HttpMethod;
import java.util.List;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final EdgeAbuseProtectionFilter edgeAbuseProtectionFilter;
    private final SubscriptionRepository subscriptionRepository;

    public SecurityConfig(JwtFilter jwtFilter,
                          EdgeAbuseProtectionFilter edgeAbuseProtectionFilter,
                          SubscriptionRepository subscriptionRepository) {
        this.jwtFilter = jwtFilter;
        this.edgeAbuseProtectionFilter = edgeAbuseProtectionFilter;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.security.public-docs-enabled:true}")
    private boolean publicDocsEnabled;

    @Value("${app.security.content-security-policy}")
    private String contentSecurityPolicy;

    private static final String[] PUBLIC_URLS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/error",
            "/actuator/health",
            "/actuator/info",
            "/api/health/**",
            "/ws/**",
            // Meta webhook endpoints (called by Meta's servers, no JWT)
            "/api/leads/webhook/**",
            "/webhooks/whatsapp/**",
            // Razorpay webhooks — called by Razorpay servers, no JWT required (DA-051)
            "/api/billing/razorpay/webhook",
            // Public landing pages and lead-capture forms
            "/api/public/landing/**",
            "/api/public/forms/**"
    };

    private static final String[] PUBLIC_DOC_URLS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**"
    };

    @Bean
    public SubscriptionStatusFilter subscriptionStatusFilter() {
        return new SubscriptionStatusFilter(subscriptionRepository);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(PUBLIC_URLS).permitAll();
                    if (publicDocsEnabled) {
                        auth.requestMatchers(PUBLIC_DOC_URLS).permitAll();
                    }
                    auth.requestMatchers("/h2-console/**").denyAll()
                            // High-risk routes are authenticated here and permission-checked in controllers/services.
                            .requestMatchers("/api/admin/**").authenticated()
                            .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                            .anyRequest().authenticated();
                })
                // JWT filter runs first to populate TenantContext and SecurityContext
                .addFilterBefore(edgeAbuseProtectionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, EdgeAbuseProtectionFilter.class)
                // Subscription degradation filter runs after JWT (needs workspace ID in TenantContext)
                .addFilterAfter(subscriptionStatusFilter(), JwtFilter.class)
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin()) // H2 console
                        .xssProtection(x -> x.disable()) // handled by Angular
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Use explicit origins from env var — never allow wildcard in production
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
