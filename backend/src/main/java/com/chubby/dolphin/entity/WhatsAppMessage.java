package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_messages", indexes = {
    @Index(name = "idx_wa_messages_lead",      columnList = "lead_id"),
    @Index(name = "idx_wa_messages_workspace", columnList = "workspace_id"),
    @Index(name = "idx_wa_messages_status",    columnList = "status")
})
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String workspaceId;
    private String leadId;

    @Column(nullable = false, length = 20)
    private String toNumber;

    @Column(nullable = false, length = 100)
    private String templateName;

    @Column(columnDefinition = "TEXT")
    private String templateParams; // Stores JSON array of parameters

    @Column(length = 100)
    private String messageId;

    @Column(nullable = false, length = 20)
    private String status = "SENT"; // SENT, DELIVERED, READ, FAILED, REPLIED

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;

    @Column(columnDefinition = "TEXT")
    private String replyText;
    private LocalDateTime replyReceivedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WhatsAppMessage() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }
    public String getToNumber() { return toNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getTemplateParams() { return templateParams; }
    public void setTemplateParams(String templateParams) { this.templateParams = templateParams; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
    public LocalDateTime getReplyReceivedAt() { return replyReceivedAt; }
    public void setReplyReceivedAt(LocalDateTime replyReceivedAt) { this.replyReceivedAt = replyReceivedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
