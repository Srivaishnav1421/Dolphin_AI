package com.chubby.dolphin.contentfactory;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "content_factory_variants", indexes = {
        @Index(name = "idx_content_factory_variants_item", columnList = "item_id, variant_index"),
        @Index(name = "idx_content_factory_variants_workspace_status", columnList = "workspace_id, approval_status"),
        @Index(name = "idx_content_factory_variants_approval_item", columnList = "approval_item_id")
})
@Data
@NoArgsConstructor
public class ContentFactoryVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID itemId;

    private UUID organizationId;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID accountId;

    private UUID createdBy;

    @Column(nullable = false)
    private Integer variantIndex;

    @Column(length = 120)
    private String headline;

    @Column(length = 255)
    private String description;

    @Column(length = 80)
    private String cta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContentGenerationMode generationMode;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String scoreBreakdownJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContentApprovalStatus approvalStatus = ContentApprovalStatus.DRAFT;

    private UUID approvalItemId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
}
