package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_ai_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAiConfig {

    @Id
    @Column(name = "workspace_id")
    private String workspaceId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "active_provider", nullable = false, length = 50)
    private LlmProvider activeProvider = LlmProvider.OLLAMA;

    @Builder.Default
    @Column(nullable = false)
    private Double temperature = 0.3;

    @Builder.Default
    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 1024;

    @Builder.Default
    @Column(name = "enable_caching", nullable = false)
    private Boolean enableCaching = true;

    @Builder.Default
    @Column(name = "enable_fallback_routing", nullable = false)
    private Boolean enableFallbackRouting = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.activeProvider == null) this.activeProvider = LlmProvider.OLLAMA;
        if (this.temperature == null) this.temperature = 0.3;
        if (this.maxTokens == null) this.maxTokens = 1024;
        if (this.enableCaching == null) this.enableCaching = true;
        if (this.enableFallbackRouting == null) this.enableFallbackRouting = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
