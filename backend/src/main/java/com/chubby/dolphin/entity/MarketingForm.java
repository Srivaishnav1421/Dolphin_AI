package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketing_forms", indexes = {
        @Index(name = "idx_marketing_forms_workspace", columnList = "account_id"),
        @Index(name = "idx_marketing_forms_slug", columnList = "account_id, slug")
})
@Data
@NoArgsConstructor
public class MarketingForm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(length = 80)
    private String industryType;

    @Column(length = 36)
    private String campaignId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT"; // DRAFT, ACTIVE, PAUSED, ARCHIVED

    @Column(columnDefinition = "TEXT")
    private String fieldsJson; // field definitions, validation, and lead attribute mapping

    @Column(columnDefinition = "TEXT")
    private String settingsJson; // spam rules, success message, automation trigger config

    private Boolean spamProtectionEnabled = true;
    private Boolean triggerAutomation = true;

    private Long submissionsCount = 0L;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }
}
