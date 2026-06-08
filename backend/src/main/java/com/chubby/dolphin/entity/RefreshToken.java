package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Refresh Token — long-lived token stored in DB.
 * Allows issuing new short-lived access tokens without re-login.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token_hash", columnList = "token_hash"),
    @Index(name = "idx_refresh_token_user_id", columnList = "user_id")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 512)
    @Convert(converter = com.chubby.dolphin.security.EncryptionConverter.class)
    private String token;

    @Column(nullable = true, length = 256)
    private String tokenHash;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String email;

    private boolean revoked = false;

    private LocalDateTime createdAt  = LocalDateTime.now();
    private LocalDateTime expiresAt;

    public RefreshToken() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
