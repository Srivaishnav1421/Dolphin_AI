package com.chubby.dolphin.contentfactory;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "content_factory_items", indexes = {
        @Index(name = "idx_content_factory_items_workspace_created", columnList = "workspace_id, created_at"),
        @Index(name = "idx_content_factory_items_account_created", columnList = "account_id, created_at")
})
@Data
@NoArgsConstructor
public class ContentFactoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private UUID accountId;

    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private ContentFactoryContentType contentType;

    @Column(nullable = false, length = 255)
    private String businessName;

    @Column(nullable = false, length = 500)
    private String productService;

    @Column(nullable = false, length = 500)
    private String targetAudience;

    @Column(length = 255)
    private String location;

    @Column(length = 500)
    private String offer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContentFactoryTone tone;

    @Column(length = 80)
    private String language;

    @Column(nullable = false, length = 80)
    private String channel;

    @Column(length = 255)
    private String goal;

    @Column(length = 80)
    private String ctaStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContentGenerationMode generationMode;

    @Column(columnDefinition = "TEXT")
    private String inputRequestJson;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
