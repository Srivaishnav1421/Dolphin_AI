package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lead_interactions", indexes = {
    @Index(name = "idx_interactions_lead", columnList = "lead_id")
})
public class LeadInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String leadId;

    private String workspaceId;

    @Column(nullable = false, length = 50)
    private String type; // POSITIVE_REPLY, NEGATIVE_REPLY, CALL_BOOKED

    @Column(nullable = false, length = 30)
    private String channel = "WHATSAPP";

    @Column(columnDefinition = "TEXT")
    private String details;

    private LocalDateTime createdAt = LocalDateTime.now();

    public LeadInteraction() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
