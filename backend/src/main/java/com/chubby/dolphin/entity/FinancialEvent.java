package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "event_type", nullable = false)
    private String eventType; // REVENUE, REFUND, TAX_DEPOSIT

    @Column(nullable = false)
    private double amount;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "INR";

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
