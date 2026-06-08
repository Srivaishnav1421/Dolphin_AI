package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.BrainEvent;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Campaign Scheduler — Automated time-based campaign management.
 *
 * Rules:
 *   - Friday midnight IST  → pause campaigns with pauseOnWeekends=true
 *   - Monday 9am IST       → resume + 10% scale-up if ROAS > 3x
 *   - Every hour           → check scheduled end dates
 */
@Service
@Slf4j
public class CampaignSchedulerService {

    private final CampaignRepository   campaignRepo;
    private final BrainEventRepository brainEventRepo;
    private final AlertService         alertService;

    public CampaignSchedulerService(CampaignRepository campaignRepo,
                                    BrainEventRepository brainEventRepo,
                                    AlertService alertService) {
        this.campaignRepo = campaignRepo;
        this.brainEventRepo = brainEventRepo;
        this.alertService = alertService;
    }

    /** Friday 18:30 UTC = Saturday 00:00 IST — pause weekend campaigns */
    @Scheduled(cron = "0 30 18 * * FRI", zone = "UTC")
    public void pauseForWeekend() {
        List<Campaign> active = campaignRepo.findByStatus("ACTIVE");
        int count = 0;
        for (Campaign c : active) {
            if (Boolean.TRUE.equals(c.getPauseOnWeekends())) {
                c.setStatus("PAUSED");
                c.setUpdatedAt(LocalDateTime.now());
                campaignRepo.save(c);
                saveBrainEvent(c.getAccountId(), "SCHEDULER",
                    "📅 Campaign '" + c.getName() + "' paused for weekend — resumes Monday", "INFO");
                count++;
            }
        }
        if (count > 0) log.info("📅 Weekend scheduler: paused {} campaign(s)", count);
    }

    /** Monday 03:30 UTC = Monday 09:00 IST — resume + scale up */
    @Scheduled(cron = "0 30 3 * * MON", zone = "UTC")
    public void resumeForMonday() {
        List<Campaign> paused = campaignRepo.findByStatus("PAUSED");
        int count = 0;
        for (Campaign c : paused) {
            if (Boolean.TRUE.equals(c.getPauseOnWeekends())) {
                c.setStatus("ACTIVE");
                // Monday scale-up: if ROAS > 3x, increase budget 10%
                if (c.getRoas() != null && c.getRoas() >= 3.0 && c.getBudget() != null) {
                    double newBudget = c.getBudget() * 1.10;
                    c.setBudget(newBudget);
                    saveBrainEvent(c.getAccountId(), "SCHEDULER",
                        String.format("📈 '%s' resumed Monday + budget +10%% (ROAS=%.2fx → ₹%.0f)",
                                      c.getName(), c.getRoas(), newBudget), "SUCCESS");
                } else {
                    saveBrainEvent(c.getAccountId(), "SCHEDULER",
                        "▶ Campaign '" + c.getName() + "' resumed for Monday", "INFO");
                }
                c.setUpdatedAt(LocalDateTime.now());
                campaignRepo.save(c);
                count++;
            }
        }
        if (count > 0) log.info("📅 Monday scheduler: resumed {} campaign(s)", count);
    }

    /** Every hour — auto-complete campaigns past their scheduled end date */
    @Scheduled(cron = "0 0 * * * *")
    public void checkScheduledEndDates() {
        List<Campaign> active = campaignRepo.findByStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        for (Campaign c : active) {
            if (c.getScheduledEndAt() != null && now.isAfter(c.getScheduledEndAt())) {
                c.setStatus("COMPLETED");
                c.setUpdatedAt(LocalDateTime.now());
                campaignRepo.save(c);
                saveBrainEvent(c.getAccountId(), "SCHEDULER",
                    "✅ Campaign '" + c.getName() + "' completed — scheduled end date reached", "INFO");
                alertService.notifyCampaignPaused(c.getName(), "Scheduled end date reached");
                log.info("✅ Campaign '{}' marked COMPLETED", c.getName());
            }
        }
    }

    private void saveBrainEvent(String accountId, String type, String message, String severity) {
        BrainEvent evt = new BrainEvent();
        evt.setAccountId(accountId);
        evt.setEventType(type);
        evt.setMessage(message);
        evt.setSeverity(severity);
        evt.setCreatedAt(LocalDateTime.now());
        brainEventRepo.save(evt);
    }
}
