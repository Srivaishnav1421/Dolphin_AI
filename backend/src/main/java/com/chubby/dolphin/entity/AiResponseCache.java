package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_response_caches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseCache {

    @Id
    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LlmProvider provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "cached_response", nullable = false, columnDefinition = "TEXT")
    private String cachedResponse;

    @Builder.Default
    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens = 0;

    @Builder.Default
    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens = 0;

    @Builder.Default
    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.totalTokens == null || this.totalTokens == 0) {
            this.totalTokens = (this.promptTokens != null ? this.promptTokens : 0) + 
                               (this.completionTokens != null ? this.completionTokens : 0);
        }
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
