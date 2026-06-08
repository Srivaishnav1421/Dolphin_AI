package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "landing_pages", indexes = {
        @Index(name = "idx_landing_pages_workspace", columnList = "account_id"),
        @Index(name = "idx_landing_pages_slug", columnList = "account_id, slug"),
        @Index(name = "idx_landing_pages_status", columnList = "account_id, status")
})
@Data
@NoArgsConstructor
public class LandingPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String workspaceId;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(length = 80)
    private String industryType;

    @Column(length = 80)
    private String templateKey;

    @Column(length = 36)
    private String campaignId;

    @Column(length = 36)
    private String formId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT"; // DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED

    @Column(columnDefinition = "TEXT")
    private String sectionsJson; // hero, benefits, details, media, CTA, form, WhatsApp, FAQ

    @Column(columnDefinition = "TEXT")
    private String seoJson;

    @Column(length = 255)
    private String customDomain;

    @Column(length = 255)
    private String publicPath;

    private Long visits = 0L;
    private Long submissions = 0L;

    private LocalDateTime publishedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }
}
