package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lead_chat_messages", indexes = {
    @Index(name = "idx_chat_messages_lead", columnList = "lead_id")
})
public class LeadChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String leadId;

    @Column(name = "workspace_id", length = 36)
    private String workspaceId;

    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    @Column(name = "thread_id", length = 100)
    private String threadId;

    @Column(nullable = false)
    private String sender; // 'LEAD' or 'SDR_BOT'

    @Column(nullable = false, length = 4000)
    private String message;

    private LocalDateTime createdAt = LocalDateTime.now();

    public LeadChatMessage() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
