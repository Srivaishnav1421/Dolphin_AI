package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.FormSubmission;
import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.MarketingForm;
import com.chubby.dolphin.repository.FormSubmissionRepository;
import com.chubby.dolphin.repository.LandingPageRepository;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.MarketingFormRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketingAutomationServiceTest {

    @Mock private MarketingFormRepository formRepo;
    @Mock private LandingPageRepository landingPageRepo;
    @Mock private FormSubmissionRepository submissionRepo;
    @Mock private LeadRepository leadRepo;
    @Mock private LeadPipelineTrackingService pipelineTrackingService;
    @Mock private WorkflowService workflowService;
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private MarketingAutomationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(localApprovalSafetyService.shouldRequireApprovalOnly(anyString())).thenReturn(false);
        service = new MarketingAutomationService(
                formRepo,
                landingPageRepo,
                submissionRepo,
                leadRepo,
                pipelineTrackingService,
                workflowService,
                localApprovalSafetyService,
                new ObjectMapper()
        );
    }

    @Test
    void submitFormBlocksWorkflowTriggerInLocalModeBeforeExternalDispatch() {
        MarketingForm form = new MarketingForm();
        form.setId("form-1");
        form.setWorkspaceId("ws-1");
        form.setName("Local verify form");
        form.setStatus("ACTIVE");
        form.setSpamProtectionEnabled(false);
        form.setTriggerAutomation(true);
        when(formRepo.findByIdAndWorkspaceId("form-1", "ws-1")).thenReturn(Optional.of(form));
        when(localApprovalSafetyService.shouldRequireApprovalOnly("WORKFLOW_FORM_TRIGGER")).thenReturn(true);
        when(leadRepo.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            lead.setId("lead-1");
            return lead;
        });
        when(submissionRepo.save(any(FormSubmission.class))).thenAnswer(invocation -> {
            FormSubmission submission = invocation.getArgument(0);
            submission.setId("submission-1");
            return submission;
        });
        when(formRepo.save(any(MarketingForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FormSubmission saved = service.submitForm(
                "ws-1",
                "form-1",
                null,
                Map.of("name", "Local Lead", "phone", "9999999999"),
                "127.0.0.1",
                "test-agent"
        );

        assertEquals("submission-1", saved.getId());
        verify(workflowService, never()).triggerWorkflow(anyString(), anyString(), anyString(), anyString());
        verify(localApprovalSafetyService).auditBlockedExecution(
                eq("ws-1"),
                eq("WORKFLOW_FORM_TRIGGER"),
                eq("WorkflowExecution"),
                isNull(),
                contains("Blocked form-triggered workflow before workflow row creation")
        );
    }
}
