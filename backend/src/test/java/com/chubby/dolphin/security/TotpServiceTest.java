package com.chubby.dolphin.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    @Test
    void generatedSecretCanProduceAndVerifyCurrentCode() {
        TotpService service = new TotpService();
        String secret = service.generateSecret();
        String code = service.generateCode(secret, Instant.now().getEpochSecond() / 30);

        assertTrue(service.verifyCode(secret, code));
        assertFalse(service.verifyCode(secret, "000000".equals(code) ? "111111" : "000000"));
    }

    @Test
    void provisioningUriUsesOtpAuthFormat() {
        TotpService service = new TotpService();
        String secret = service.generateSecret();
        String uri = service.provisioningUri("DolphinAI", "owner@example.com", secret);

        assertTrue(uri.startsWith("otpauth://totp/DolphinAI%3Aowner%40example.com"));
        assertTrue(uri.contains("issuer=DolphinAI"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }
}
