package com.chubby.dolphin.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ProductionReadinessValidator {

    private static final String DEV_JWT_SECRET = "DolphinAI_SuperSecretKey_2026_HS256_MinLength32BytesRequired!!";
    private static final String OLD_DEV_JWT_SECRET = "ChubbyDolphinAI_SuperSecretKey_2024_HS256_MinLength32BytesRequired!!";
    private static final String DEV_ENCRYPTION_KEY = "DolphinAIEncryptionKeySecret32Chars!";

    private final Environment environment;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${meta.encryption.key:}")
    private String encryptionKey;

    @Value("${meta.app.secret:}")
    private String metaAppSecret;

    @Value("${meta.webhook.verify-token:}")
    private String metaWebhookVerifyToken;

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:}")
    private String razorpayWebhookSecret;

    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Value("${spring.jpa.hibernate.ddl-auto:}")
    private String ddlAuto;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${app.security.content-security-policy:}")
    private String contentSecurityPolicy;

    @Value("${app.security.public-docs-enabled:false}")
    private boolean publicDocsEnabled;

    @Value("${demo.password:}")
    private String demoPassword;

    @Value("${demo.email:}")
    private String demoEmail;

    @Value("${first-run.owner.email:}")
    private String firstRunOwnerEmail;

    @Value("${first-run.owner.password:}")
    private String firstRunOwnerPassword;

    @Value("${ai.mock.enabled:false}")
    private boolean mockAiEnabled;

    @Value("${app.seed.demo-users-enabled:false}")
    private boolean demoUsersEnabled;

    public ProductionReadinessValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProductionProfile() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) return;

        requireStrongSecret("JWT_SECRET", jwtSecret, 48);
        rejectKnownDevelopmentSecret("JWT_SECRET", jwtSecret, DEV_JWT_SECRET);
        rejectKnownDevelopmentSecret("JWT_SECRET", jwtSecret, OLD_DEV_JWT_SECRET);

        requireStrongSecret("META_ENCRYPTION_KEY", encryptionKey, 32);
        rejectKnownDevelopmentSecret("META_ENCRYPTION_KEY", encryptionKey, DEV_ENCRYPTION_KEY);
        requireStrongSecret("META_APP_SECRET", metaAppSecret, 24);
        requireStrongSecret("META_WEBHOOK_VERIFY_TOKEN", metaWebhookVerifyToken, 24);
        rejectKnownDevelopmentSecret("META_WEBHOOK_VERIFY_TOKEN", metaWebhookVerifyToken, "chubby_dolphin_verify_2024");

        requireConfigured("RAZORPAY_KEY_ID", razorpayKeyId);
        requireStrongSecret("RAZORPAY_KEY_SECRET", razorpayKeySecret, 24);
        requireStrongSecret("RAZORPAY_WEBHOOK_SECRET", razorpayWebhookSecret, 24);
        requireStrongSecret("SPRING_DATASOURCE_PASSWORD", datasourcePassword, 16);
        rejectKnownDevelopmentSecret("SPRING_DATASOURCE_PASSWORD", datasourcePassword, "dolphin123");
        if (datasourceUrl == null || datasourceUrl.isBlank()
                || !datasourceUrl.startsWith("jdbc:postgresql://")
                || datasourceUrl.toLowerCase().contains("jdbc:h2:")
                || datasourceUrl.contains("localhost")
                || datasourceUrl.contains("127.0.0.1")) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL must point to a production PostgreSQL host in prod.");
        }

        if (corsAllowedOrigins.contains("*") || corsAllowedOrigins.contains("localhost")) {
            throw new IllegalStateException("CORS_ORIGINS must be explicit production origins in prod.");
        }
        if (frontendUrl == null || frontendUrl.isBlank() || frontendUrl.contains("localhost")) {
            throw new IllegalStateException("APP_FRONTEND_URL must be a production URL in prod.");
        }
        if ("create".equalsIgnoreCase(ddlAuto) || "create-drop".equalsIgnoreCase(ddlAuto) || "update".equalsIgnoreCase(ddlAuto)) {
            throw new IllegalStateException("spring.jpa.hibernate.ddl-auto must be validate or none in prod.");
        }
        if (mockAiEnabled) {
            throw new IllegalStateException("ai.mock.enabled must be false in prod.");
        }
        if (demoUsersEnabled) {
            throw new IllegalStateException("app.seed.demo-users-enabled must be false in prod.");
        }
        if (demoEmail != null && !demoEmail.isBlank()) {
            throw new IllegalStateException("DEMO_EMAIL must not be configured in prod. Use FIRST_RUN_OWNER_EMAIL for first-run owner bootstrap.");
        }
        if (demoPassword != null && !demoPassword.isBlank()) {
            throw new IllegalStateException("DEMO_PASSWORD must not be configured in prod. Use FIRST_RUN_OWNER_PASSWORD for first-run owner bootstrap.");
        }
        if (firstRunOwnerEmail != null && !firstRunOwnerEmail.isBlank()) {
            requireStrongSecret("FIRST_RUN_OWNER_PASSWORD", firstRunOwnerPassword, 16);
            rejectKnownDevelopmentSecret("FIRST_RUN_OWNER_PASSWORD", firstRunOwnerPassword, "dolphin123");
        }
        if (publicDocsEnabled) {
            throw new IllegalStateException("app.security.public-docs-enabled must be false in prod.");
        }
        if (contentSecurityPolicy == null || contentSecurityPolicy.isBlank()
                || contentSecurityPolicy.contains("localhost")
                || contentSecurityPolicy.contains("127.0.0.1")
                || contentSecurityPolicy.contains("ws://")) {
            throw new IllegalStateException("CONTENT_SECURITY_POLICY must be production-safe in prod.");
        }
    }

    private void requireConfigured(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required in prod.");
        }
    }

    private void requireStrongSecret(String name, String value, int minLength) {
        requireConfigured(name, value);
        if (value.length() < minLength) {
            throw new IllegalStateException(name + " must be at least " + minLength + " characters in prod.");
        }
    }

    private void rejectKnownDevelopmentSecret(String name, String value, String developmentValue) {
        if (developmentValue.equals(value)) {
            throw new IllegalStateException(name + " is using a development default in prod.");
        }
    }
}
