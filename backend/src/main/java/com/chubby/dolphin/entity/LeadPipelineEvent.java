package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lead_pipeline_events")
public class LeadPipelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String workspaceId;
    private String leadId;

    @Column(nullable = false)
    private String eventType; // WEBHOOK_RECEIVED, WORKSPACE_RESOLVED, LEAD_CREATED, LEAD_SCORED, WHATSAPP_SENT, WHATSAPP_DELIVERED, WHATSAPP_REPLIED, PIPELINE_FAILED

    @Column(nullable = false)
    private String status; // SUCCESS, FAILED

    @Column(length = 2000)
    private String details;

    private LocalDateTime createdAt = LocalDateTime.now();

    public LeadPipelineEvent() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
