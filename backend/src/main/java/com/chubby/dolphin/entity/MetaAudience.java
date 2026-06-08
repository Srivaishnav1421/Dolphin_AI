package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meta_audiences")
public class MetaAudience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String workspaceId;

    private String metaAudienceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String audienceType; // CUSTOM, LOOKALIKE, SUPER_LOOKALIKE

    private String subtype; // CUSTOMER_FILE, WEBSITE, ENGAGEMENT, LOOKALIKE

    private Long sizeEstimate;

    private String sourceAudienceId;

    private Double lookalikeRatio;

    @Column(length = 5)
    private String lookalikeCountry = "IN";

    @Column(nullable = false)
    private String status = "ACTIVE";

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MetaAudience() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getMetaAudienceId() { return metaAudienceId; }
    public void setMetaAudienceId(String metaAudienceId) { this.metaAudienceId = metaAudienceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAudienceType() { return audienceType; }
    public void setAudienceType(String audienceType) { this.audienceType = audienceType; }
    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }
    public Long getSizeEstimate() { return sizeEstimate; }
    public void setSizeEstimate(Long sizeEstimate) { this.sizeEstimate = sizeEstimate; }
    public String getSourceAudienceId() { return sourceAudienceId; }
    public void setSourceAudienceId(String sourceAudienceId) { this.sourceAudienceId = sourceAudienceId; }
    public Double getLookalikeRatio() { return lookalikeRatio; }
    public void setLookalikeRatio(Double lookalikeRatio) { this.lookalikeRatio = lookalikeRatio; }
    public String getLookalikeCountry() { return lookalikeCountry; }
    public void setLookalikeCountry(String lookalikeCountry) { this.lookalikeCountry = lookalikeCountry; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
