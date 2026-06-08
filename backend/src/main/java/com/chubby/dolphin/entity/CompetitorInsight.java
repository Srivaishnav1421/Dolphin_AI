package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "competitor_insights")
@Data
@NoArgsConstructor
public class CompetitorInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "competitor_url", length = 1000, nullable = false)
    private String competitorUrl;

    @Column(name = "value_proposition", columnDefinition = "TEXT", nullable = false)
    private String valueProposition;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "competitor_hooks", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "hook", length = 1000)
    private List<String> extractedHooks;

    @Column(name = "target_demographics", columnDefinition = "TEXT", nullable = false)
    private String targetDemographics;

    @Column(name = "pricing_model")
    private String pricingModel;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
