package com.chubby.dolphin.contentfactory;

import com.chubby.dolphin.approval.*;
import com.chubby.dolphin.approval.dto.ApprovalItemCreateRequest;
import com.chubby.dolphin.approval.dto.ApprovalItemResponse;
import com.chubby.dolphin.contentfactory.dto.*;
import com.chubby.dolphin.entity.AiPurpose;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentFactoryService {

    private static final int VARIANT_COUNT = 3;

    private final ContentFactoryItemRepository itemRepository;
    private final ContentFactoryVariantRepository variantRepository;
    private final ContentFactoryScoringService scoringService;
    private final BusinessLlmFacadeService llmFacadeService;
    private final ApprovalQueueService approvalQueueService;
    private final AccessControlService access;
    private final ObjectMapper objectMapper;

    @Transactional
    public ContentFactoryItemResponse generate(ContentFactoryGenerateRequest request) {
        ContentFactoryContentType contentType = parseContentType(request.contentType(), request.channel());
        ContentFactoryTone tone = parseTone(request.tone());
        SanitizedRequest sanitized = sanitize(request, contentType, tone);
        User actor = access.currentUser();
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        UUID organizationId = uuid(actor.getOrganization() != null ? actor.getOrganization().getId() : null);
        UUID userId = uuid(actor.getId());

        GenerationResult generation = generateDrafts(sanitized);
        LocalDateTime now = LocalDateTime.now();

        ContentFactoryItem item = new ContentFactoryItem();
        item.setOrganizationId(organizationId);
        item.setWorkspaceId(workspaceId);
        item.setAccountId(workspaceId);
        item.setCreatedBy(userId);
        item.setContentType(contentType);
        item.setBusinessName(sanitized.businessName());
        item.setProductService(sanitized.productService());
        item.setTargetAudience(sanitized.targetAudience());
        item.setLocation(sanitized.location());
        item.setOffer(sanitized.offer());
        item.setTone(tone);
        item.setLanguage(sanitized.language());
        item.setChannel(sanitized.channel());
        item.setGoal(sanitized.goal());
        item.setCtaStyle(sanitized.ctaStyle());
        item.setGenerationMode(generation.mode());
        item.setInputRequestJson(json(Map.ofEntries(
                Map.entry("businessName", sanitized.businessName()),
                Map.entry("productService", sanitized.productService()),
                Map.entry("targetAudience", sanitized.targetAudience()),
                Map.entry("location", sanitized.location()),
                Map.entry("offer", sanitized.offer()),
                Map.entry("tone", tone.name()),
                Map.entry("language", sanitized.language()),
                Map.entry("channel", sanitized.channel()),
                Map.entry("goal", sanitized.goal()),
                Map.entry("ctaStyle", sanitized.ctaStyle()),
                Map.entry("contentType", contentType.name()),
                Map.entry("generationMode", generation.mode().name()),
                Map.entry("provider", generation.provider())
        )));
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        ContentFactoryItem savedItem = itemRepository.save(item);

        List<ContentFactoryVariant> savedVariants = new ArrayList<>();
        int index = 1;
        for (GeneratedDraft draft : generation.drafts()) {
            ContentScoreBreakdown score = scoringService.score(
                    draft.headline(), draft.description(), draft.cta(), draft.contentText());

            ContentFactoryVariant variant = new ContentFactoryVariant();
            variant.setItemId(savedItem.getId());
            variant.setOrganizationId(organizationId);
            variant.setWorkspaceId(workspaceId);
            variant.setAccountId(workspaceId);
            variant.setCreatedBy(userId);
            variant.setVariantIndex(index++);
            variant.setHeadline(trimTo(draft.headline(), 40));
            variant.setDescription(trimTo(draft.description(), 125));
            variant.setCta(shortCta(draft.cta()));
            variant.setContentText(cleanRequired(draft.contentText(), 4000, "content text"));
            variant.setGenerationMode(generation.mode());
            variant.setScore(score.score());
            variant.setScoreBreakdownJson(json(score));
            variant.setApprovalStatus(ContentApprovalStatus.DRAFT);
            variant.setCreatedAt(now);
            variant.setUpdatedAt(now);
            savedVariants.add(variantRepository.save(variant));
        }

        return toResponse(savedItem, savedVariants);
    }

    @Transactional(readOnly = true)
    public List<ContentFactoryItemResponse> listItems() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return itemRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(item -> toResponse(item, variantRepository.findByItemIdAndWorkspaceIdOrderByVariantIndexAsc(item.getId(), workspaceId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContentFactoryItemResponse getItem(String id) {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        UUID itemId = requiredUuid(id, "item id");
        ContentFactoryItem item = itemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Content Factory item not found."));
        return toResponse(item, variantRepository.findByItemIdAndWorkspaceIdOrderByVariantIndexAsc(itemId, workspaceId));
    }

    @Transactional
    public ContentFactorySubmitApprovalResponse submitVariantForApproval(String id) {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        UUID variantId = requiredUuid(id, "variant id");
        ContentFactoryVariant variant = variantRepository.findByIdAndWorkspaceId(variantId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Content Factory variant not found."));
        ContentFactoryItem item = itemRepository.findByIdAndWorkspaceId(variant.getItemId(), workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Content Factory item not found."));

        if (variant.getApprovalStatus() == ContentApprovalStatus.SUBMITTED_FOR_APPROVAL
                || variant.getApprovalStatus() == ContentApprovalStatus.APPROVED) {
            throw new IllegalStateException("Variant is already submitted or approved.");
        }

        ApprovalItemResponse approval = approvalQueueService.createApprovalItem(new ApprovalItemCreateRequest(
                id(item.getOrganizationId()),
                id(item.getWorkspaceId()),
                id(item.getAccountId()),
                ApprovalSourceModule.CONTENT_FACTORY,
                "ContentFactoryVariant",
                id(variant.getId()),
                ApprovalActionType.APPROVE_CREATIVE,
                "Approve content variant: " + trimTo(nonBlank(variant.getHeadline(), item.getContentType().name()), 80),
                nonBlank(variant.getDescription(), variant.getContentText()),
                recommendationJson(item, variant),
                mathSnapshotJson(item, variant),
                variant.getScore() != null && variant.getScore() >= 70 ? ApprovalSeverity.LOW : ApprovalSeverity.MEDIUM,
                false,
                null
        ));

        variant.setApprovalStatus(ContentApprovalStatus.SUBMITTED_FOR_APPROVAL);
        variant.setApprovalItemId(requiredUuid(approval.id(), "approval id"));
        variant.setUpdatedAt(LocalDateTime.now());
        ContentFactoryVariant saved = variantRepository.save(variant);

        return new ContentFactorySubmitApprovalResponse(ContentFactoryVariantResponse.from(saved), approval);
    }

    @Transactional
    public void markApprovalDecision(UUID approvalItemId, ApprovalStatus status) {
        variantRepository.findByApprovalItemId(approvalItemId).ifPresent(variant -> {
            LocalDateTime now = LocalDateTime.now();
            if (status == ApprovalStatus.APPROVED) {
                variant.setApprovalStatus(ContentApprovalStatus.APPROVED);
                variant.setApprovedAt(now);
            } else if (status == ApprovalStatus.REJECTED) {
                variant.setApprovalStatus(ContentApprovalStatus.REJECTED);
                variant.setRejectedAt(now);
            } else {
                return;
            }
            variant.setUpdatedAt(now);
            variantRepository.save(variant);
        });
    }

    private GenerationResult generateDrafts(SanitizedRequest request) {
        BusinessLlmFacadeService.LlmResponse response = llmFacadeService.askForTask(
                prompt(request),
                0.35,
                900,
                AiPurpose.CREATIVE_GENERATION,
                "CONTENT_FACTORY"
        );

        if (isRealAiResponse(response)) {
            List<GeneratedDraft> aiDrafts = parseAiDrafts(response.text(), request);
            if (!aiDrafts.isEmpty()) {
                return new GenerationResult(ContentGenerationMode.AI_GENERATED, response.provider(), aiDrafts);
            }
        }

        return new GenerationResult(ContentGenerationMode.TEMPLATE_GENERATED, "TEMPLATE", templateDrafts(request));
    }

    private boolean isRealAiResponse(BusinessLlmFacadeService.LlmResponse response) {
        if (response == null) return false;
        String provider = response.provider();
        String text = response.text();
        return provider != null
                && !provider.equalsIgnoreCase("NONE")
                && !provider.equalsIgnoreCase("MOCK")
                && text != null
                && !text.isBlank()
                && !text.startsWith("AI analysis is temporarily unavailable");
    }

    private List<GeneratedDraft> parseAiDrafts(String text, SanitizedRequest request) {
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode variants = root.has("variants") ? root.path("variants") : root.path("variations");
            if (!variants.isArray()) {
                return List.of();
            }
            List<GeneratedDraft> drafts = new ArrayList<>();
            for (JsonNode variant : variants) {
                if (drafts.size() >= VARIANT_COUNT) break;
                String headline = trimTo(variant.path("headline").asText(""), 40);
                String description = trimTo(nonBlank(
                        variant.path("description").asText(""),
                        variant.path("body").asText("")
                ), 125);
                String cta = shortCta(nonBlank(variant.path("cta").asText(""), request.ctaStyle()));
                String contentText = cleanRequired(nonBlank(
                        variant.path("content_text").asText(""),
                        variant.path("contentText").asText(""),
                        headline + "\n" + description + "\n" + cta
                ), 4000, "content text");
                drafts.add(new GeneratedDraft(headline, description, cta, contentText));
            }
            return drafts;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<GeneratedDraft> templateDrafts(SanitizedRequest request) {
        String product = trimTo(request.productService(), 36);
        String audience = trimTo(request.targetAudience(), 42);
        String location = request.location().isBlank() ? "your area" : trimTo(request.location(), 28);
        String offer = request.offer().isBlank() ? "a trusted new offer" : trimTo(request.offer(), 44);
        String cta = shortCta(request.ctaStyle().isBlank() ? defaultCta(request.contentType()) : request.ctaStyle());

        String h1 = trimTo("Limited " + product + " Offer", 40);
        String d1 = trimTo("Save today with " + offer + " for " + audience + " in " + location + ".", 125);
        String h2 = trimTo("Trusted " + product + " Today", 40);
        String d2 = trimTo("New offer for " + audience + ". Clear next steps, fast response, and local support.", 125);
        String h3 = trimTo("Exclusive " + product + " Deal", 40);
        String d3 = trimTo("Only few slots today. Get a trusted solution for " + audience + " in " + location + ".", 125);

        return List.of(
                withContentText(request.contentType(), h1, d1, cta),
                withContentText(request.contentType(), h2, d2, cta),
                withContentText(request.contentType(), h3, d3, cta)
        );
    }

    private GeneratedDraft withContentText(ContentFactoryContentType type, String headline, String description, String cta) {
        String text = switch (type) {
            case META_AD_COPY -> headline + "\n" + description + "\nCTA: " + cta;
            case INSTAGRAM_POST -> headline + "\n\n" + description + "\n\n" + cta;
            case WHATSAPP_MESSAGE -> headline + "\n" + description + "\nReply to " + cta + ".";
            case REEL_SCRIPT -> "Hook: " + headline + "\nScene 1: Show the problem.\nScene 2: Present the offer.\nClose: " + description + "\nCTA: " + cta;
            case LANDING_PAGE_HEADLINE -> headline + "\nSubheadline: " + description + "\nCTA: " + cta;
        };
        return new GeneratedDraft(headline, description, cta, text);
    }

    private String prompt(SanitizedRequest request) {
        return """
                You are DolphinAI Content Factory.
                Generate exactly 3 marketing copy variants for the requested content type.
                Do not include confidence, predicted CTR, publishing instructions, or external platform actions.

                Business name: %s
                Product/service: %s
                Target audience: %s
                Location: %s
                Offer: %s
                Tone: %s
                Language: %s
                Channel: %s
                Goal: %s
                CTA style: %s
                Content type: %s

                Creative constraints:
                - headline max 40 characters
                - description max 125 characters
                - CTA short and clear

                Respond with ONLY JSON:
                {
                  "variants": [
                    {
                      "headline": "max 40 chars",
                      "description": "max 125 chars",
                      "cta": "short CTA",
                      "content_text": "full copy or script"
                    }
                  ]
                }
                """.formatted(
                request.businessName(),
                request.productService(),
                request.targetAudience(),
                request.location(),
                request.offer(),
                request.tone().name(),
                request.language(),
                request.channel(),
                request.goal(),
                request.ctaStyle(),
                request.contentType().name()
        );
    }

    private ContentFactoryItemResponse toResponse(ContentFactoryItem item, List<ContentFactoryVariant> variants) {
        return ContentFactoryItemResponse.from(
                item,
                variants.stream().map(ContentFactoryVariantResponse::from).toList()
        );
    }

    private String recommendationJson(ContentFactoryItem item, ContentFactoryVariant variant) {
        return json(Map.of(
                "module", "CONTENT_FACTORY",
                "action", "APPROVE_CREATIVE",
                "contentType", item.getContentType().name(),
                "generationMode", variant.getGenerationMode().name(),
                "headline", nonBlank(variant.getHeadline(), ""),
                "description", nonBlank(variant.getDescription(), ""),
                "cta", nonBlank(variant.getCta(), ""),
                "contentText", variant.getContentText(),
                "score", variant.getScore(),
                "publishesAutomatically", false
        ));
    }

    private String mathSnapshotJson(ContentFactoryItem item, ContentFactoryVariant variant) {
        return json(Map.of(
                "formulaVersion", ContentFactoryScoringService.FORMULA_VERSION,
                "contentFactoryItemId", id(item.getId()),
                "contentFactoryVariantId", id(variant.getId()),
                "score", variant.getScore(),
                "scoreBreakdown", parseJsonMap(variant.getScoreBreakdownJson()),
                "constraints", Map.of(
                        "headlineMaxCharacters", 40,
                        "descriptionMaxCharacters", 125,
                        "ctaShortAndClear", true
                )
        ));
    }

    private Map<String, Object> parseJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private SanitizedRequest sanitize(ContentFactoryGenerateRequest request,
                                      ContentFactoryContentType contentType,
                                      ContentFactoryTone tone) {
        return new SanitizedRequest(
                cleanRequired(request.businessName(), 255, "business name"),
                cleanRequired(request.productService(), 500, "product/service"),
                cleanRequired(request.targetAudience(), 500, "target audience"),
                clean(request.location(), 255),
                clean(request.offer(), 500),
                tone,
                nonBlank(clean(request.language(), 80), "English"),
                nonBlank(clean(request.channel(), 80), label(contentType)),
                nonBlank(clean(request.goal(), 255), "Generate interest"),
                clean(request.ctaStyle(), 80),
                contentType
        );
    }

    private ContentFactoryContentType parseContentType(String contentType, String channel) {
        String value = nonBlank(contentType, channel);
        if (value.isBlank()) {
            return ContentFactoryContentType.META_AD_COPY;
        }
        try {
            return ContentFactoryContentType.valueOf(normalizeEnum(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported content type.");
        }
    }

    private ContentFactoryTone parseTone(String tone) {
        if (tone == null || tone.isBlank()) {
            return ContentFactoryTone.FRIENDLY;
        }
        try {
            return ContentFactoryTone.valueOf(normalizeEnum(tone));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported tone.");
        }
    }

    private String normalizeEnum(String value) {
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String defaultCta(ContentFactoryContentType type) {
        return switch (type) {
            case META_AD_COPY, INSTAGRAM_POST -> "Learn More";
            case WHATSAPP_MESSAGE -> "Reply Now";
            case REEL_SCRIPT -> "Book Now";
            case LANDING_PAGE_HEADLINE -> "Get Started";
        };
    }

    private String label(ContentFactoryContentType type) {
        return switch (type) {
            case META_AD_COPY -> "Meta ad copy";
            case INSTAGRAM_POST -> "Instagram post";
            case WHATSAPP_MESSAGE -> "WhatsApp message";
            case REEL_SCRIPT -> "Reel script";
            case LANDING_PAGE_HEADLINE -> "Landing page headline";
        };
    }

    private String shortCta(String value) {
        return trimTo(nonBlank(value, "Learn More"), 24);
    }

    private String cleanRequired(String value, int maxLength, String field) {
        String cleaned = clean(value, maxLength);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return cleaned;
    }

    private String clean(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return trimTo(cleaned, maxLength);
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength).trim();
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize Content Factory JSON.");
        }
    }

    private UUID requiredUuid(String value, String field) {
        UUID parsed = uuid(value);
        if (parsed == null) {
            throw new IllegalArgumentException(field + " must be a valid UUID.");
        }
        return parsed;
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String id(UUID value) {
        return value == null ? null : value.toString();
    }

    private record SanitizedRequest(
            String businessName,
            String productService,
            String targetAudience,
            String location,
            String offer,
            ContentFactoryTone tone,
            String language,
            String channel,
            String goal,
            String ctaStyle,
            ContentFactoryContentType contentType
    ) {}

    private record GeneratedDraft(String headline, String description, String cta, String contentText) {}

    private record GenerationResult(ContentGenerationMode mode, String provider, List<GeneratedDraft> drafts) {}
}
