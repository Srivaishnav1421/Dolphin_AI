package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brain_events", indexes = {
    @Index(name = "idx_brain_events_account", columnList = "account_id, created_at"),
    @Index(name = "idx_brain_events_type",    columnList = "account_id, event_type")
})
public class BrainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    private String eventType;    // CAMPAIGN_PAUSED, BUDGET_REALLOCATED, LEAD_SCORED, ARBITRAGE_RUN, WALLET_FUNDED
    @Column(length = 2000)
    private String message;
    private String severity;     // INFO, WARNING, CRITICAL, SUCCESS

    private LocalDateTime createdAt = LocalDateTime.now();

    public BrainEvent() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
