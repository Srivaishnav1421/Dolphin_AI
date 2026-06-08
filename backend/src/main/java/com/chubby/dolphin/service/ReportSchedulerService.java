package com.chubby.dolphin.service;

import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Report Scheduler — Sends automated performance reports to all account owners.
 *
 * Schedule:
 *   - Daily Performance Summary: every day at 9 AM IST
 *   - Weekly ROAS Report:        every Monday at 9 AM IST
 *   - Monthly Client Report:     1st of every month at 8 AM IST
 */
@Service
@Slf4j
public class ReportSchedulerService {

    private final UserRepository userRepo;
    private final CampaignRepository campaignRepo;
    private final ReportService reportService;
    private final AlertService alertService;

    @Value("${reports.enabled:false}")
    private boolean reportsEnabled;

    public ReportSchedulerService(UserRepository userRepo,
                                   CampaignRepository campaignRepo,
                                   ReportService reportService,
                                   AlertService alertService) {
        this.userRepo = userRepo;
        this.campaignRepo = campaignRepo;
        this.reportService = reportService;
        this.alertService = alertService;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Daily Report — Every day at 9 AM IST (3:30 AM UTC)
    // ══════════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 30 3 * * *") // 09:00 IST = 03:30 UTC
    public void sendDailyPerformanceSummary() {
        if (!reportsEnabled) {
            log.debug("📊 Report scheduler disabled — set reports.enabled=true to activate");
            return;
        }

        log.info("📊 Starting daily performance report send...");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();

        List<User> owners = userRepo.findByRole("OWNER");
        for (User owner : owners) {
            if (owner.getAccountId() == null) continue;
            try {
                sendReportEmail(owner, yesterday, today, "Daily Performance Summary");
            } catch (Exception e) {
                log.error("Failed to send daily report to {}: {}", owner.getEmail(), e.getMessage());
            }
        }
        log.info("✅ Daily reports dispatched to {} owners", owners.size());
    }

    // ══════════════════════════════════════════════════════════════════
    //  Weekly Report — Every Monday at 9 AM IST
    // ══════════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 30 3 * * MON") // Monday 09:00 IST = 03:30 UTC
    public void sendWeeklyRoasReport() {
        if (!reportsEnabled) return;

        log.info("📊 Starting weekly ROAS report send...");
        LocalDate weekStart = LocalDate.now().minusDays(7);
        LocalDate today = LocalDate.now();

        List<User> owners = userRepo.findByRole("OWNER");
        for (User owner : owners) {
            if (owner.getAccountId() == null) continue;
            try {
                sendReportEmail(owner, weekStart, today, "Weekly ROAS Report");
            } catch (Exception e) {
                log.error("Failed to send weekly report to {}: {}", owner.getEmail(), e.getMessage());
            }
        }
        log.info("✅ Weekly reports dispatched to {} owners", owners.size());
    }

    // ══════════════════════════════════════════════════════════════════
    //  Monthly Report — 1st of every month at 8 AM IST
    // ══════════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 30 2 1 * *") // 1st of month, 08:00 IST = 02:30 UTC
    public void sendMonthlyClientReport() {
        if (!reportsEnabled) return;

        log.info("📊 Starting monthly client report send...");
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        LocalDate monthEnd   = LocalDate.now().withDayOfMonth(1).minusDays(1);

        List<User> owners = userRepo.findByRole("OWNER");
        for (User owner : owners) {
            if (owner.getAccountId() == null) continue;
            try {
                sendReportEmail(owner, monthStart, monthEnd, "Monthly Performance Report");
            } catch (Exception e) {
                log.error("Failed to send monthly report to {}: {}", owner.getEmail(), e.getMessage());
            }
        }
        log.info("✅ Monthly reports dispatched to {} owners", owners.size());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void sendReportEmail(User owner, LocalDate from, LocalDate to, String reportTitle) {
        boolean hasCampaigns = !campaignRepo.findByAccountId(owner.getAccountId()).isEmpty();
        if (!hasCampaigns) {
            log.debug("Skipping report for {} — no campaigns yet", owner.getEmail());
            return;
        }

        byte[] pdfBytes = reportService.generateCampaignReportPdf(owner.getAccountId(), from, to);
        log.info("📧 [{}] Report ready for {} ({} bytes) — period: {} to {}",
                 reportTitle, owner.getEmail(), pdfBytes.length, from, to);

        // AlertService sends the notification email; PDF is logged for now.
        // When mail is configured, enhance AlertService to support attachments.
        // For now, send an HTML notification with a link to download via API.
        alertService.notifyReportReady(owner.getEmail(), reportTitle, from.toString(), to.toString());
    }
}
