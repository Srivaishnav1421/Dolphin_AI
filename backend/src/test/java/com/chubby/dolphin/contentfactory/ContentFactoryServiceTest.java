package com.chubby.dolphin.contentfactory;

import com.chubby.dolphin.approval.*;
import com.chubby.dolphin.approval.dto.ApprovalItemCreateRequest;
import com.chubby.dolphin.approval.dto.ApprovalItemResponse;
import com.chubby.dolphin.contentfactory.dto.ContentFactoryGenerateRequest;
import com.chubby.dolphin.entity.AiPurpose;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentFactoryServiceTest {

    private ContentFactoryItemRepository itemRepository;
    private ContentFactoryVariantRepository variantRepository;
    private BusinessLlmFacadeService llmFacadeService;
    private ApprovalQueueService approvalQueueService;
    private AccessControlService access;
    private ContentFactoryService service;
    private UUID workspaceId;
    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ContentFactoryItemRepository.class);
        variantRepository = mock(ContentFactoryVariantRepository.class);
        llmFacadeService = mock(BusinessLlmFacadeService.class);
        approvalQueueService = mock(ApprovalQueueService.class);
        access = mock(AccessControlService.class);
        service = new ContentFactoryService(
                itemRepository,
                variantRepository,
                new ContentFactoryScoringService(),
                llmFacadeService,
                approvalQueueService,
                access,
                new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        );

        workspaceId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId.toString());
        User user = new User();
        user.setId(userId.toString());
        user.setEmail("owner@test.local");
        user.setOrganization(org);

        when(access.currentWorkspaceId()).thenReturn(workspaceId.toString());
        when(access.currentUser()).thenReturn(user);
        when(itemRepository.save(any(ContentFactoryItem.class))).thenAnswer(inv -> {
            ContentFactoryItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(variantRepository.save(any(ContentFactoryVariant.class))).thenAnswer(inv -> {
            ContentFactoryVariant variant = inv.getArgument(0);
            if (variant.getId() == null) {
                variant.setId(UUID.randomUUID());
            }
            return variant;
        });
    }

    @Test
    void missingRealAiProviderUsesTemplateGenerationAndPersistsVariants() {
        when(llmFacadeService.askForTask(anyString(), anyDouble(), anyInt(), eq(AiPurpose.CREATIVE_GENERATION), eq("CONTENT_FACTORY")))
                .thenReturn(new BusinessLlmFacadeService.LlmResponse("[MOCK RESPONSE]", "MOCK", "mock"));

        var response = service.generate(request());

        assertEquals(ContentGenerationMode.TEMPLATE_GENERATED, response.generationMode());
        assertEquals(3, response.variants().size());
        assertTrue(response.variants().stream().allMatch(v -> v.generationMode() == ContentGenerationMode.TEMPLATE_GENERATED));
        assertTrue(response.variants().stream().allMatch(v -> v.scoreBreakdownJson().contains("length_score")));
        verify(itemRepository).save(any(ContentFactoryItem.class));
        verify(variantRepository, times(3)).save(any(ContentFactoryVariant.class));
    }

    @Test
    void realAiProviderLabelsVariantsAsAiGenerated() {
        String json = """
                {"variants":[
                  {"headline":"Save Today","description":"Trusted offer for local buyers.","cta":"Book Now","content_text":"Save Today\\nTrusted offer for local buyers.\\nBook Now"}
                ]}
                """;
        when(llmFacadeService.askForTask(anyString(), anyDouble(), anyInt(), eq(AiPurpose.CREATIVE_GENERATION), eq("CONTENT_FACTORY")))
                .thenReturn(new BusinessLlmFacadeService.LlmResponse(json, "GEMINI", "gemini"));

        var response = service.generate(request());

        assertEquals(ContentGenerationMode.AI_GENERATED, response.generationMode());
        assertEquals(1, response.variants().size());
        assertEquals(ContentGenerationMode.AI_GENERATED, response.variants().get(0).generationMode());
    }

    @Test
    void submitApprovalCreatesGlobalApprovalItemAndDoesNotPublish() {
        UUID itemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        ContentFactoryItem item = item(itemId);
        ContentFactoryVariant variant = variant(itemId, variantId);
        UUID approvalId = UUID.randomUUID();

        when(itemRepository.findByIdAndWorkspaceId(itemId, workspaceId)).thenReturn(Optional.of(item));
        when(variantRepository.findByIdAndWorkspaceId(variantId, workspaceId)).thenReturn(Optional.of(variant));
        when(approvalQueueService.createApprovalItem(any())).thenReturn(new ApprovalItemResponse(
                approvalId.toString(),
                orgId.toString(),
                workspaceId.toString(),
                workspaceId.toString(),
                ApprovalSourceModule.CONTENT_FACTORY,
                "ContentFactoryVariant",
                variantId.toString(),
                ApprovalActionType.APPROVE_CREATIVE,
                "Approve content variant",
                "Review content",
                "{}",
                "{}",
                ApprovalSeverity.LOW,
                ApprovalStatus.PENDING,
                false,
                null,
                null,
                userId.toString(),
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                false
        ));

        var response = service.submitVariantForApproval(variantId.toString());

        assertEquals(ContentApprovalStatus.SUBMITTED_FOR_APPROVAL, response.variant().approvalStatus());
        assertEquals(ApprovalStatus.PENDING, response.approval().status());
        assertEquals(ApprovalSourceModule.CONTENT_FACTORY, response.approval().sourceModule());
        assertEquals(ApprovalActionType.APPROVE_CREATIVE, response.approval().actionType());
        assertFalse(response.approval().requiresExecution());

        ArgumentCaptor<ApprovalItemCreateRequest> captor = ArgumentCaptor.forClass(ApprovalItemCreateRequest.class);
        verify(approvalQueueService).createApprovalItem(captor.capture());
        assertEquals(ApprovalSourceModule.CONTENT_FACTORY, captor.getValue().sourceModule());
        assertEquals(ApprovalActionType.APPROVE_CREATIVE, captor.getValue().actionType());
        assertFalse(captor.getValue().requiresExecution());
        assertTrue(captor.getValue().recommendationJson().contains("\"publishesAutomatically\":false"));
    }

    @Test
    void approvalDecisionMarksLinkedVariantApprovedOrRejected() {
        UUID approvalId = UUID.randomUUID();
        ContentFactoryVariant variant = variant(UUID.randomUUID(), UUID.randomUUID());
        variant.setApprovalItemId(approvalId);
        when(variantRepository.findByApprovalItemId(approvalId)).thenReturn(Optional.of(variant));

        service.markApprovalDecision(approvalId, ApprovalStatus.APPROVED);

        assertEquals(ContentApprovalStatus.APPROVED, variant.getApprovalStatus());
        assertNotNull(variant.getApprovedAt());
        verify(variantRepository).save(variant);
    }

    private ContentFactoryGenerateRequest request() {
        return new ContentFactoryGenerateRequest(
                "Dolphin Dental",
                "Implant consultation",
                "Working professionals",
                "Hyderabad",
                "Free scan today",
                "FRIENDLY",
                "English",
                "Meta",
                "Generate qualified leads",
                "Book Now",
                "META_AD_COPY"
        );
    }

    private ContentFactoryItem item(UUID id) {
        ContentFactoryItem item = new ContentFactoryItem();
        item.setId(id);
        item.setOrganizationId(orgId);
        item.setWorkspaceId(workspaceId);
        item.setAccountId(workspaceId);
        item.setCreatedBy(userId);
        item.setContentType(ContentFactoryContentType.META_AD_COPY);
        item.setBusinessName("Dolphin Dental");
        item.setProductService("Implant consultation");
        item.setTargetAudience("Working professionals");
        item.setTone(ContentFactoryTone.FRIENDLY);
        item.setChannel("Meta");
        item.setGenerationMode(ContentGenerationMode.TEMPLATE_GENERATED);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }

    private ContentFactoryVariant variant(UUID itemId, UUID id) {
        ContentFactoryVariant variant = new ContentFactoryVariant();
        variant.setId(id);
        variant.setItemId(itemId);
        variant.setOrganizationId(orgId);
        variant.setWorkspaceId(workspaceId);
        variant.setAccountId(workspaceId);
        variant.setCreatedBy(userId);
        variant.setVariantIndex(1);
        variant.setHeadline("Limited Offer Today");
        variant.setDescription("Save now with trusted local support.");
        variant.setCta("Book Now");
        variant.setContentText("Limited Offer Today\nSave now with trusted local support.\nBook Now");
        variant.setGenerationMode(ContentGenerationMode.TEMPLATE_GENERATED);
        variant.setScore(82);
        variant.setScoreBreakdownJson("{\"score\":82}");
        variant.setApprovalStatus(ContentApprovalStatus.DRAFT);
        variant.setCreatedAt(LocalDateTime.now());
        variant.setUpdatedAt(LocalDateTime.now());
        return variant;
    }
}
