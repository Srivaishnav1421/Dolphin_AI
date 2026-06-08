package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "event_source", nullable = false)
    private String eventSource;

    @Column(nullable = false)
    private int units;

    @Column(name = "cost_basis", nullable = false)
    @Builder.Default
    private double costBasis = 0.0000;

    @Column(nullable = false)
    @Builder.Default
    private boolean billable = true;

    private String provider;
    private String model;

    @Column(name = "credits_consumed")
    @Builder.Default
    private double creditsConsumed = 0.0000;

    @Column(name = "estimated_cost_inr")
    @Builder.Default
    private double estimatedCostInr = 0.0000;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
