package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@Transactional
public class MarketingAutomationService {

    private final MarketingFormRepository formRepo;
    private final LandingPageRepository landingPageRepo;
    private final FormSubmissionRepository submissionRepo;
    private final LeadRepository leadRepo;
    private final LeadPipelineTrackingService pipelineTrackingService;
    private final WorkflowService workflowService;
    private final LocalApprovalSafetyService localApprovalSafetyService;
    private final ObjectMapper mapper;

    public MarketingAutomationService(MarketingFormRepository formRepo,
                                      LandingPageRepository landingPageRepo,
                                      FormSubmissionRepository submissionRepo,
                                      LeadRepository leadRepo,
                                      LeadPipelineTrackingService pipelineTrackingService,
                                      WorkflowService workflowService,
                                      LocalApprovalSafetyService localApprovalSafetyService,
                                      ObjectMapper mapper) {
        this.formRepo = formRepo;
        this.landingPageRepo = landingPageRepo;
        this.submissionRepo = submissionRepo;
        this.leadRepo = leadRepo;
        this.pipelineTrackingService = pipelineTrackingService;
        this.workflowService = workflowService;
        this.localApprovalSafetyService = localApprovalSafetyService;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> defaultTemplates() {
        return List.of(
                template("real_estate_property", "Real estate property lead page", "REAL_ESTATE", List.of("budget", "location", "property_preference", "visit_date")),
                template("college_admissions", "College admissions lead page", "EDUCATION", List.of("course", "marks", "city", "parent_phone")),
                template("clinic_appointment", "Clinic appointment lead page", "CLINIC", List.of("service", "preferred_date", "symptoms")),
                template("coaching_admission", "Coaching center admission page", "COACHING", List.of("course", "class", "city")),
                template("agency_consultation", "Agency consultation page", "AGENCY", List.of("business_type", "monthly_budget", "goal")),
                template("local_service_enquiry", "Local service enquiry page", "LOCAL_SERVICE", List.of("service", "area", "preferred_time"))
        );
    }

    public MarketingForm createForm(String workspaceId, MarketingForm form) {
        form.setWorkspaceId(workspaceId);
        form.setSlug(slugify(form.getSlug() != null ? form.getSlug() : form.getName()));
        form.setStatus(form.getStatus() == null ? "DRAFT" : normalizeStatus(form.getStatus(), Set.of("DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"), "DRAFT"));
        if (form.getFieldsJson() == null || form.getFieldsJson().isBlank()) {
            form.setFieldsJson(defaultFieldsFor(form.getIndustryType()));
        }
        form.setCreatedAt(LocalDateTime.now());
        form.setUpdatedAt(LocalDateTime.now());
        return formRepo.save(form);
    }

    public MarketingForm updateForm(String workspaceId, String id, MarketingForm body) {
        MarketingForm form = formRepo.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("Form not found"));
        if (body.getName() != null) form.setName(body.getName());
        if (body.getSlug() != null) form.setSlug(slugify(body.getSlug()));
        if (body.getIndustryType() != null) form.setIndustryType(body.getIndustryType());
        if (body.getCampaignId() != null) form.setCampaignId(body.getCampaignId());
        if (body.getStatus() != null) form.setStatus(normalizeStatus(body.getStatus(), Set.of("DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"), form.getStatus()));
        if (body.getFieldsJson() != null) form.setFieldsJson(body.getFieldsJson());
        if (body.getSettingsJson() != null) form.setSettingsJson(body.getSettingsJson());
        if (body.getSpamProtectionEnabled() != null) form.setSpamProtectionEnabled(body.getSpamProtectionEnabled());
        if (body.getTriggerAutomation() != null) form.setTriggerAutomation(body.getTriggerAutomation());
        form.setUpdatedAt(LocalDateTime.now());
        return formRepo.save(form);
    }

    public LandingPage createLandingPage(String workspaceId, LandingPage page) {
        page.setWorkspaceId(workspaceId);
        page.setSlug(slugify(page.getSlug() != null ? page.getSlug() : page.getTitle()));
        page.setStatus(page.getStatus() == null ? "DRAFT" : normalizeStatus(page.getStatus(), Set.of("DRAFT", "PUBLISHED", "UNPUBLISHED", "ARCHIVED"), "DRAFT"));
        if (page.getSectionsJson() == null || page.getSectionsJson().isBlank()) {
            page.setSectionsJson(defaultSectionsFor(page.getIndustryType(), page.getTitle()));
        }
        page.setPublicPath("/p/" + workspaceId + "/" + page.getSlug());
        page.setCreatedAt(LocalDateTime.now());
        page.setUpdatedAt(LocalDateTime.now());
        if ("PUBLISHED".equals(page.getStatus())) page.setPublishedAt(LocalDateTime.now());
        return landingPageRepo.save(page);
    }

    public LandingPage updateLandingPage(String workspaceId, String id, LandingPage body) {
        LandingPage page = landingPageRepo.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("Landing page not found"));
        if (body.getTitle() != null) page.setTitle(body.getTitle());
        if (body.getSlug() != null) {
            page.setSlug(slugify(body.getSlug()));
            page.setPublicPath("/p/" + workspaceId + "/" + page.getSlug());
        }
        if (body.getIndustryType() != null) page.setIndustryType(body.getIndustryType());
        if (body.getTemplateKey() != null) page.setTemplateKey(body.getTemplateKey());
        if (body.getCampaignId() != null) page.setCampaignId(body.getCampaignId());
        if (body.getFormId() != null) page.setFormId(body.getFormId());
        if (body.getSectionsJson() != null) page.setSectionsJson(body.getSectionsJson());
        if (body.getSeoJson() != null) page.setSeoJson(body.getSeoJson());
        if (body.getCustomDomain() != null) page.setCustomDomain(body.getCustomDomain());
        if (body.getStatus() != null) {
            String status = normalizeStatus(body.getStatus(), Set.of("DRAFT", "PUBLISHED", "UNPUBLISHED", "ARCHIVED"), page.getStatus());
            page.setStatus(status);
            if ("PUBLISHED".equals(status) && page.getPublishedAt() == null) page.setPublishedAt(LocalDateTime.now());
        }
        page.setUpdatedAt(LocalDateTime.now());
        return landingPageRepo.save(page);
    }

    public Optional<LandingPage> getPublishedLandingPage(String workspaceId, String slug) {
        Optional<LandingPage> pageOpt = landingPageRepo.findByWorkspaceIdAndSlugAndStatus(workspaceId, slug, "PUBLISHED");
        pageOpt.ifPresent(page -> {
            page.setVisits((page.getVisits() == null ? 0 : page.getVisits()) + 1);
            landingPageRepo.save(page);
        });
        return pageOpt;
    }

    public FormSubmission submitForm(String workspaceId, String formId, String landingPageId, Map<String, Object> payload,
                                     String ipAddress, String userAgent) {
        MarketingForm form = formRepo.findByIdAndWorkspaceId(formId, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("Form not found"));
        if (!"ACTIVE".equals(form.getStatus())) throw new IllegalStateException("This form is not accepting submissions");
        if (isSpam(form, payload)) {
            FormSubmission rejected = baseSubmission(workspaceId, form, landingPageId, payload, ipAddress, userAgent);
            rejected.setStatus("SPAM_REJECTED");
            return submissionRepo.save(rejected);
        }

        Lead lead = buildLead(workspaceId, form, payload);
        Lead savedLead = leadRepo.save(lead);

        FormSubmission submission = baseSubmission(workspaceId, form, landingPageId, payload, ipAddress, userAgent);
        submission.setLeadId(savedLead.getId());
        submission.setStatus("ACCEPTED");
        FormSubmission savedSubmission = submissionRepo.save(submission);

        form.setSubmissionsCount((form.getSubmissionsCount() == null ? 0 : form.getSubmissionsCount()) + 1);
        form.setUpdatedAt(LocalDateTime.now());
        formRepo.save(form);

        if (landingPageId != null && !landingPageId.isBlank()) {
            landingPageRepo.findByIdAndWorkspaceId(landingPageId, workspaceId).ifPresent(page -> {
                page.setSubmissions((page.getSubmissions() == null ? 0 : page.getSubmissions()) + 1);
                page.setUpdatedAt(LocalDateTime.now());
                landingPageRepo.save(page);
            });
        }

        pipelineTrackingService.recordLeadCreated(workspaceId, savedLead.getId(), "Lead captured from form: " + form.getName());
        if (Boolean.TRUE.equals(form.getTriggerAutomation())) {
            if (localApprovalSafetyService.shouldRequireApprovalOnly("WORKFLOW_FORM_TRIGGER")) {
                localApprovalSafetyService.auditBlockedExecution(
                        workspaceId,
                        "WORKFLOW_FORM_TRIGGER",
                        "WorkflowExecution",
                        null,
                        "Blocked form-triggered workflow before workflow row creation or n8n webhook call."
                );
                return savedSubmission;
            }
            workflowService.triggerWorkflow("system", "form-" + savedSubmission.getId(),
                    "New lead submitted form " + form.getName() + ". Follow configured automation only.", workspaceId);
        }
        return savedSubmission;
    }

    private Lead buildLead(String workspaceId, MarketingForm form, Map<String, Object> payload) {
        Lead lead = new Lead();
        lead.setWorkspaceId(workspaceId);
        lead.setCampaignId(asString(payload.getOrDefault("campaign_id", form.getCampaignId())));
        lead.setName(firstNonBlank(payload, List.of("name", "full_name", "student_name", "patient_name", "customer_name"), "Unknown Lead"));
        lead.setPhone(firstNonBlank(payload, List.of("phone", "mobile", "phone_number", "parent_number", "parent_phone"), null));
        lead.setEmail(firstNonBlank(payload, List.of("email", "email_id"), null));
        lead.setSource(firstNonBlank(payload, List.of("source", "utm_source"), "LANDING_PAGE"));
        lead.setMessage(buildLeadMessage(form, payload));
        lead.setStatus("COLD");
        lead.setPipelineStage("NEW_LEAD");
        lead.setPriority("MEDIUM");
        lead.setScore(0.5);
        lead.setBudget(toDouble(payload.get("budget")));
        lead.setInterestCategory(firstNonBlank(payload, List.of("course", "service", "property_preference", "interest", "category"), null));
        lead.setLocation(firstNonBlank(payload, List.of("city", "location", "area"), null));
        lead.setExpectedRevenue(toDouble(payload.get("expected_revenue")));
        lead.setTags("form," + safe(form.getIndustryType(), "general").toLowerCase());
        lead.setCreatedAt(LocalDateTime.now());
        return lead;
    }

    private FormSubmission baseSubmission(String workspaceId, MarketingForm form, String landingPageId, Map<String, Object> payload,
                                          String ipAddress, String userAgent) {
        FormSubmission submission = new FormSubmission();
        submission.setWorkspaceId(workspaceId);
        submission.setFormId(form.getId());
        submission.setLandingPageId(landingPageId);
        submission.setCampaignId(asString(payload.getOrDefault("campaign_id", form.getCampaignId())));
        submission.setSource(firstNonBlank(payload, List.of("source", "utm_source"), landingPageId != null ? "LANDING_PAGE" : "FORM"));
        submission.setIpAddress(ipAddress);
        submission.setUserAgent(userAgent);
        submission.setCreatedAt(LocalDateTime.now());
        try { submission.setPayloadJson(mapper.writeValueAsString(payload)); } catch (Exception e) { submission.setPayloadJson("{}"); }
        return submission;
    }

    private boolean isSpam(MarketingForm form, Map<String, Object> payload) {
        return Boolean.TRUE.equals(form.getSpamProtectionEnabled()) &&
                (payload.containsKey("website") && asString(payload.get("website")) != null && !asString(payload.get("website")).isBlank());
    }

    private Map<String, Object> template(String key, String name, String industry, List<String> fields) {
        return Map.of("key", key, "name", name, "industry", industry, "recommended_fields", fields);
    }

    private String defaultFieldsFor(String industry) {
        String normalized = safe(industry, "GENERAL").toUpperCase();
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("name", "Name", "TEXT", true, "name"));
        fields.add(field("phone", "Phone", "PHONE", true, "phone"));
        fields.add(field("email", "Email", "EMAIL", false, "email"));
        if ("REAL_ESTATE".equals(normalized)) {
            fields.add(field("budget", "Budget", "NUMBER", false, "budget"));
            fields.add(field("location", "Preferred location", "TEXT", false, "location"));
            fields.add(field("visit_date", "Visit date", "DATE", false, "next_follow_up_at"));
        } else if ("EDUCATION".equals(normalized) || "COACHING".equals(normalized)) {
            fields.add(field("course", "Course", "TEXT", true, "interest_category"));
            fields.add(field("city", "City", "TEXT", false, "location"));
            fields.add(field("parent_phone", "Parent phone", "PHONE", false, "custom"));
        } else if ("CLINIC".equals(normalized)) {
            fields.add(field("service", "Service", "TEXT", true, "interest_category"));
            fields.add(field("preferred_date", "Preferred date", "DATE", false, "next_follow_up_at"));
            fields.add(field("symptoms", "Symptoms / notes", "TEXT", false, "notes"));
        }
        try { return mapper.writeValueAsString(fields); } catch (Exception e) { return "[]"; }
    }

    private Map<String, Object> field(String key, String label, String type, boolean required, String mapsTo) {
        return Map.of("key", key, "label", label, "type", type, "required", required, "maps_to", mapsTo);
    }

    private String defaultSectionsFor(String industry, String title) {
        Map<String, Object> sections = Map.of(
                "hero", Map.of("headline", safe(title, "Grow your business with DolphinAI"), "subheadline", "Share your details and our team will contact you shortly."),
                "benefits", List.of("Quick response", "WhatsApp-first follow-up", "Clear guidance from our team"),
                "cta", Map.of("label", "Get a callback"),
                "faq", List.of(Map.of("q", "When will I get a response?", "a", "Usually within one business day."))
        );
        try { return mapper.writeValueAsString(sections); } catch (Exception e) { return "{}"; }
    }

    private String buildLeadMessage(MarketingForm form, Map<String, Object> payload) {
        return "Form: " + form.getName() + " | Details: " + payload;
    }

    private String firstNonBlank(Map<String, Object> payload, List<String> keys, String fallback) {
        for (String key : keys) {
            String value = asString(payload.get(key));
            if (value != null && !value.isBlank()) return value;
        }
        return fallback;
    }

    private String slugify(String value) {
        return safe(value, "page").toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalizeStatus(String value, Set<String> allowed, String fallback) {
        String normalized = safe(value, fallback).toUpperCase();
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String asString(Object value) { return value == null ? null : value.toString().trim(); }
    private Double toDouble(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return null; }
    }
}
