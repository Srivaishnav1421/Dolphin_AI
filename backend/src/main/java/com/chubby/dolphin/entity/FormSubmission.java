package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "form_submissions", indexes = {
        @Index(name = "idx_form_submissions_workspace", columnList = "account_id, created_at"),
        @Index(name = "idx_form_submissions_form", columnList = "form_id"),
        @Index(name = "idx_form_submissions_lead", columnList = "lead_id")
})
@Data
@NoArgsConstructor
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(name = "form_id", nullable = false, length = 36)
    private String formId;

    @Column(name = "landing_page_id", length = 36)
    private String landingPageId;

    @Column(name = "campaign_id", length = 36)
    private String campaignId;

    @Column(name = "lead_id", length = 36)
    private String leadId;

    @Column(length = 80)
    private String source = "LANDING_PAGE";

    @Column(nullable = false, length = 30)
    private String status = "ACCEPTED"; // ACCEPTED, SPAM_REJECTED, FAILED

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(length = 255)
    private String ipAddress;

    @Column(length = 1000)
    private String userAgent;

    private LocalDateTime createdAt = LocalDateTime.now();

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }
}
