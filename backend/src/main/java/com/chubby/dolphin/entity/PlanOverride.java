package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "plan_overrides", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "feature_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "limit_value")
    private Long limitValue;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
