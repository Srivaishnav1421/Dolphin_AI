package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "subscription_entitlements", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"plan_id", "feature_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "limit_type", nullable = false)
    private String limitType; // HARD, SOFT, BOOLEAN

    @Column(name = "limit_value")
    private Integer limitValue;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
