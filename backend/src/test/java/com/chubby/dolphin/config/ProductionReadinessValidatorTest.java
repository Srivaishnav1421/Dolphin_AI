package com.chubby.dolphin.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionReadinessValidatorTest {

    @Test
    void prodRejectsMockAi() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "mockAiEnabled", true);

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    @Test
    void prodRejectsDemoBootstrapVariables() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "demoEmail", "demo@dolphin.test");

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    @Test
    void prodRejectsDemoPasswordVariable() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "demoPassword", "demo-password");

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    @Test
    void prodRejectsDemoUsersEnabled() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "demoUsersEnabled", true);

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    @Test
    void prodRejectsWeakFirstRunOwnerPassword() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "firstRunOwnerEmail", "owner@dolphin.test");
        ReflectionTestUtils.setField(validator, "firstRunOwnerPassword", "short");

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    @Test
    void prodRejectsUnsafeCors() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "http://localhost:4200");

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    @Test
    void prodRejectsH2DatasourceUrl() {
        ProductionReadinessValidator validator = baselineValidator();
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:h2:mem:dolphin");

        assertThrows(IllegalStateException.class, validator::validateProductionProfile);
    }

    private ProductionReadinessValidator baselineValidator() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionReadinessValidator validator = new ProductionReadinessValidator(environment);
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-jwt-secret-that-is-long-enough-for-hs256-validation-2026");
        ReflectionTestUtils.setField(validator, "encryptionKey", "prod-encryption-key-32-characters-minimum");
        ReflectionTestUtils.setField(validator, "metaAppSecret", "prod-meta-app-secret-value");
        ReflectionTestUtils.setField(validator, "metaWebhookVerifyToken", "prod-meta-webhook-verify-token");
        ReflectionTestUtils.setField(validator, "razorpayKeyId", "rzp_live_123");
        ReflectionTestUtils.setField(validator, "razorpayKeySecret", "prod-razorpay-key-secret-value");
        ReflectionTestUtils.setField(validator, "razorpayWebhookSecret", "prod-razorpay-webhook-secret");
        ReflectionTestUtils.setField(validator, "datasourcePassword", "prod-db-password-value");
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://db.prod.example:5432/dolphin");
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://app.dolphin.ai");
        ReflectionTestUtils.setField(validator, "frontendUrl", "https://app.dolphin.ai");
        ReflectionTestUtils.setField(validator, "ddlAuto", "validate");
        ReflectionTestUtils.setField(validator, "contentSecurityPolicy", "default-src 'self'; connect-src 'self' https: wss:");
        ReflectionTestUtils.setField(validator, "publicDocsEnabled", false);
        ReflectionTestUtils.setField(validator, "mockAiEnabled", false);
        ReflectionTestUtils.setField(validator, "demoUsersEnabled", false);
        ReflectionTestUtils.setField(validator, "demoEmail", "");
        ReflectionTestUtils.setField(validator, "demoPassword", "");
        ReflectionTestUtils.setField(validator, "firstRunOwnerEmail", "");
        ReflectionTestUtils.setField(validator, "firstRunOwnerPassword", "");
        return validator;
    }
}
