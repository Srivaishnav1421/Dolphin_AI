package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.RefreshToken;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final long REFRESH_TOKEN_DAYS = 7;

    private final RefreshTokenRepository repo;
    private final String jwtSecret;

    public RefreshTokenService(RefreshTokenRepository repo, 
                               @org.springframework.beans.factory.annotation.Value("${jwt.secret}") String jwtSecret) {
        this.repo = repo;
        this.jwtSecret = jwtSecret;
    }

    private String hashToken(String token) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing refresh token", e);
        }
    }

    /** Create and persist a new refresh token for a user */
    @Transactional
    public RefreshToken create(User user) {
        RefreshToken rt = new RefreshToken();
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        rt.setToken(rawToken);
        rt.setTokenHash(hashToken(rawToken));
        rt.setUserId(user.getId());
        rt.setEmail(user.getEmail());
        rt.setRevoked(false);
        rt.setCreatedAt(LocalDateTime.now());
        rt.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS));
        return repo.save(rt);
    }

    /** Validate a refresh token — returns it if valid, empty if expired/revoked/missing */
    public Optional<RefreshToken> validate(String token) {
        String tokenHash = hashToken(token);
        return repo.findByTokenHashAndRevokedFalse(tokenHash)
                .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    /** Revoke ALL tokens for a user (on logout or password change) */
    @Transactional
    public void revokeAll(String userId) {
        repo.revokeAllByUserId(userId);
    }

    /** Rotate — revoke old token, issue new one (one-time-use pattern) */
    @Transactional
    public RefreshToken rotate(RefreshToken old, User user) {
        old.setRevoked(true);
        repo.save(old);
        return create(user);
    }
}
