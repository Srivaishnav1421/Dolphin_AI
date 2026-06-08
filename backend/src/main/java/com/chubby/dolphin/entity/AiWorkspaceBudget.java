package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_workspace_budgets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkspaceBudget {

    @Id
    @Column(name = "workspace_id")
    private String workspaceId;

    @Builder.Default
    @Column(name = "monthly_usd_budget", nullable = false)
    private Double monthlyUsdBudget = 200.0;

    @Builder.Default
    @Column(name = "monthly_tokens_budget", nullable = false)
    private Integer monthlyTokensBudget = 20000000;

    @Builder.Default
    @Column(name = "warning_threshold_percent", nullable = false)
    private Double warningThresholdPercent = 80.0;

    @Builder.Default
    @Column(name = "hard_stop_enabled", nullable = false)
    private Boolean hardStopEnabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.monthlyUsdBudget == null) this.monthlyUsdBudget = 200.0;
        if (this.monthlyTokensBudget == null) this.monthlyTokensBudget = 20000000;
        if (this.warningThresholdPercent == null) this.warningThresholdPercent = 80.0;
        if (this.hardStopEnabled == null) this.hardStopEnabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
