package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_logs", indexes = {
    @Index(name = "idx_ai_usage_workspace", columnList = "workspace_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LlmProvider provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Builder.Default
    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens = 0;

    @Builder.Default
    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens = 0;

    @Builder.Default
    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens = 0;

    @Builder.Default
    @Column(name = "cost_usd", nullable = false)
    private Double costUsd = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiPurpose purpose;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.totalTokens == null || this.totalTokens == 0) {
            this.totalTokens = (this.promptTokens != null ? this.promptTokens : 0) + 
                               (this.completionTokens != null ? this.completionTokens : 0);
        }
    }
}
