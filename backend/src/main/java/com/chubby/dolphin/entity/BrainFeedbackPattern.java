package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brain_feedback_patterns", indexes = {
    @Index(name = "idx_feedback_patterns_product", columnList = "product")
})
public class BrainFeedbackPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private String tone;

    @Column(nullable = false)
    private String audience;

    @Column(nullable = false)
    private String platform;

    @Column(name = "pattern_status", nullable = false)
    private String patternStatus; // 'HIGH_PERFORMING' or 'LOW_PERFORMING'

    @Column(nullable = false)
    private Double roas;

    private LocalDateTime createdAt = LocalDateTime.now();

    public BrainFeedbackPattern() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPatternStatus() { return patternStatus; }
    public void setPatternStatus(String patternStatus) { this.patternStatus = patternStatus; }

    public Double getRoas() { return roas; }
    public void setRoas(Double roas) { this.roas = roas; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
