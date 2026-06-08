package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.FormSubmission;
import com.chubby.dolphin.entity.LandingPage;
import com.chubby.dolphin.entity.MarketingForm;
import com.chubby.dolphin.repository.FormSubmissionRepository;
import com.chubby.dolphin.repository.LandingPageRepository;
import com.chubby.dolphin.repository.MarketingFormRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.MarketingAutomationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
public class MarketingController {

    private final MarketingAutomationService marketingService;
    private final MarketingFormRepository formRepo;
    private final LandingPageRepository pageRepo;
    private final FormSubmissionRepository submissionRepo;
    private final SecurityUtils sec;

    public MarketingController(MarketingAutomationService marketingService,
                               MarketingFormRepository formRepo,
                               LandingPageRepository pageRepo,
                               FormSubmissionRepository submissionRepo,
                               SecurityUtils sec) {
        this.marketingService = marketingService;
        this.formRepo = formRepo;
        this.pageRepo = pageRepo;
        this.submissionRepo = submissionRepo;
        this.sec = sec;
    }

    @GetMapping("/api/marketing/templates")
    public ResponseEntity<?> templates() {
        return ResponseEntity.ok(marketingService.defaultTemplates());
    }

    @GetMapping("/api/marketing/forms")
    public ResponseEntity<?> forms() {
        return ResponseEntity.ok(formRepo.findByWorkspaceIdOrderByUpdatedAtDesc(sec.currentWorkspaceId()));
    }

    @PostMapping("/api/marketing/forms")
    public ResponseEntity<?> createForm(@RequestBody MarketingForm form) {
        if (form.getName() == null || form.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Form name is required"));
        }
        return ResponseEntity.ok(marketingService.createForm(sec.currentWorkspaceId(), form));
    }

    @PutMapping("/api/marketing/forms/{id}")
    public ResponseEntity<?> updateForm(@PathVariable String id, @RequestBody MarketingForm form) {
        try {
            return ResponseEntity.ok(marketingService.updateForm(sec.currentWorkspaceId(), id, form));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/marketing/landing-pages")
    public ResponseEntity<?> landingPages() {
        return ResponseEntity.ok(pageRepo.findByWorkspaceIdOrderByUpdatedAtDesc(sec.currentWorkspaceId()));
    }

    @PostMapping("/api/marketing/landing-pages")
    public ResponseEntity<?> createLandingPage(@RequestBody LandingPage page) {
        if (page.getTitle() == null || page.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Landing page title is required"));
        }
        return ResponseEntity.ok(marketingService.createLandingPage(sec.currentWorkspaceId(), page));
    }

    @PutMapping("/api/marketing/landing-pages/{id}")
    public ResponseEntity<?> updateLandingPage(@PathVariable String id, @RequestBody LandingPage page) {
        try {
            return ResponseEntity.ok(marketingService.updateLandingPage(sec.currentWorkspaceId(), id, page));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/marketing/submissions")
    public ResponseEntity<?> submissions() {
        return ResponseEntity.ok(submissionRepo.findByWorkspaceIdOrderByCreatedAtDesc(sec.currentWorkspaceId()));
    }

    @GetMapping("/api/marketing/analytics")
    public ResponseEntity<?> analytics(@RequestParam(required = false) String campaignId,
                                       @RequestParam(required = false) String landingPageId,
                                       @RequestParam(required = false) String formId) {
        String workspaceId = sec.currentWorkspaceId();
        long submissions = formId != null && !formId.isBlank()
                ? submissionRepo.countByWorkspaceIdAndFormId(workspaceId, formId)
                : landingPageId != null && !landingPageId.isBlank()
                ? submissionRepo.countByWorkspaceIdAndLandingPageId(workspaceId, landingPageId)
                : campaignId != null && !campaignId.isBlank()
                ? submissionRepo.countByWorkspaceIdAndCampaignId(workspaceId, campaignId)
                : submissionRepo.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).size();
        long visits = landingPageId != null && !landingPageId.isBlank()
                ? pageRepo.findByIdAndWorkspaceId(landingPageId, workspaceId).map(p -> p.getVisits() == null ? 0L : p.getVisits()).orElse(0L)
                : pageRepo.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream().mapToLong(p -> p.getVisits() == null ? 0L : p.getVisits()).sum();
        double conversionRate = visits == 0 ? 0.0 : Math.round(((double) submissions / visits) * 1000.0) / 10.0;
        return ResponseEntity.ok(Map.of(
                "landing_page_visits", visits,
                "form_submissions", submissions,
                "leads_captured", submissions,
                "conversion_rate", conversionRate
        ));
    }

    @GetMapping("/api/public/landing/{workspaceId}/{slug}")
    public ResponseEntity<?> publicLandingPage(@PathVariable String workspaceId, @PathVariable String slug) {
        return marketingService.getPublishedLandingPage(workspaceId, slug)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/public/forms/{workspaceId}/{formId}/submit")
    public ResponseEntity<?> submitForm(@PathVariable String workspaceId,
                                        @PathVariable String formId,
                                        @RequestParam(required = false) String landingPageId,
                                        @RequestBody Map<String, Object> payload,
                                        HttpServletRequest request) {
        try {
            FormSubmission submission = marketingService.submitForm(
                    workspaceId,
                    formId,
                    landingPageId,
                    payload,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );
            if ("SPAM_REJECTED".equals(submission.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Submission rejected"));
            }
            return ResponseEntity.ok(Map.of(
                    "status", "accepted",
                    "submission_id", submission.getId(),
                    "lead_id", submission.getLeadId() != null ? submission.getLeadId() : ""
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
