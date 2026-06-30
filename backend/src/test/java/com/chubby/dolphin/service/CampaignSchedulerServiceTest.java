package com.chubby.dolphin.service;

import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampaignSchedulerServiceTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private BrainEventRepository brainEventRepo;
    @Mock private AlertService alertService;
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private CampaignSchedulerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CampaignSchedulerService(campaignRepo, brainEventRepo, alertService, localApprovalSafetyService);
    }

    @Test
    void pauseForWeekendIsBlockedInLocalApprovalFirstModeBeforeCampaignMutation() {
        when(localApprovalSafetyService.shouldRequireApprovalOnly("CAMPAIGN_SCHEDULER_PAUSE_WEEKEND")).thenReturn(true);

        service.pauseForWeekend();

        verify(localApprovalSafetyService).auditBlockedExecution(
                isNull(),
                eq("CAMPAIGN_SCHEDULER_PAUSE_WEEKEND"),
                eq("Campaign"),
                isNull(),
                contains("Scheduled campaign automation blocked")
        );
        verifyNoInteractions(campaignRepo, brainEventRepo, alertService);
    }

    @Test
    void resumeForMondayIsBlockedInLocalApprovalFirstModeBeforeCampaignMutation() {
        when(localApprovalSafetyService.shouldRequireApprovalOnly("CAMPAIGN_SCHEDULER_RESUME_MONDAY")).thenReturn(true);

        service.resumeForMonday();

        verify(localApprovalSafetyService).auditBlockedExecution(
                isNull(),
                eq("CAMPAIGN_SCHEDULER_RESUME_MONDAY"),
                eq("Campaign"),
                isNull(),
                contains("Scheduled campaign automation blocked")
        );
        verifyNoInteractions(campaignRepo, brainEventRepo, alertService);
    }

    @Test
    void scheduledEndDateCheckIsBlockedInLocalApprovalFirstModeBeforeCampaignMutation() {
        when(localApprovalSafetyService.shouldRequireApprovalOnly("CAMPAIGN_SCHEDULER_END_DATE")).thenReturn(true);

        service.checkScheduledEndDates();

        verify(localApprovalSafetyService).auditBlockedExecution(
                isNull(),
                eq("CAMPAIGN_SCHEDULER_END_DATE"),
                eq("Campaign"),
                isNull(),
                contains("Scheduled campaign automation blocked")
        );
        verifyNoInteractions(campaignRepo, brainEventRepo, alertService);
    }
}
