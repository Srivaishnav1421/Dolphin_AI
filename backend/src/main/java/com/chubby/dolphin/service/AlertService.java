package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Alert Service — Sends email notifications for critical events.
 * JavaMailSender is optional — if not configured, alerts are logged only.
 */
@Service
@Slf4j
public class AlertService {

    /** Optional — null if spring.mail.username is not configured */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${alerts.enabled:false}")         private boolean alertsEnabled;
    @Value("${alerts.email:admin@dolphin.ai}") private String  alertEmail;
    @Value("${alerts.roas.threshold:1.5}")    private double  roasThreshold;
    @Value("${app.frontend-url:http://localhost:4200}") private String frontendUrl;

    @Async
    public void notifyHotLead(String accountId, String leadName, double score) {
        log.info("🔥 HOT Lead: {} (score={}) for account {}", leadName, score, accountId);
        if (!canSend()) return;
        sendHtml(
            "🔥 HOT Lead Alert — " + leadName,
            """
            <div style="font-family:sans-serif;padding:24px;background:#0d1b2a;color:#f1f5f9">
              <h2 style="color:#ef4444">🔥 New HOT Lead!</h2>
              <p><strong>Name:</strong> %s</p>
              <p><strong>AI Score:</strong> <span style="color:#ef4444">%.2f / 1.0</span></p>
              <br/>
              <a href="%s"
                 style="background:#00d4ff;color:#000;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:700">
                View Lead →
              </a>
            </div>
            """.formatted(leadName, score, appLink("/leads"))
        );
    }

    @Async
    public void notifyLowRoas(String campaignName, double roas) {
        log.warn("⚠️ Low ROAS: {} → {}x", campaignName, String.format("%.2f", roas));
        if (!canSend() || roas > roasThreshold) return;
        sendHtml(
            "⚠️ Low ROAS — " + campaignName,
            """
            <div style="font-family:sans-serif;padding:24px;background:#0d1b2a;color:#f1f5f9">
              <h2 style="color:#f59e0b">⚠️ Campaign ROAS Below Threshold</h2>
              <p><strong>Campaign:</strong> %s</p>
              <p><strong>ROAS:</strong> <span style="color:#ef4444">%.2fx</span> (threshold: %.1fx)</p>
              <a href="%s"
                 style="background:#f59e0b;color:#000;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:700">
                Review →
              </a>
            </div>
            """.formatted(campaignName, roas, roasThreshold, appLink("/campaigns"))
        );
    }

    @Async
    public void notifyCampaignPaused(String campaignName, String reason) {
        log.info("⏸ Campaign paused: {} — {}", campaignName, reason);
        if (!canSend()) return;
        sendHtml(
            "⏸ Campaign Auto-Paused — " + campaignName,
            """
            <div style="font-family:sans-serif;padding:24px;background:#0d1b2a;color:#f1f5f9">
              <h2 style="color:#f59e0b">⏸ Brain Auto-Paused a Campaign</h2>
              <p><strong>Campaign:</strong> %s</p>
              <p><strong>Reason:</strong> %s</p>
              <a href="%s"
                 style="background:#6366f1;color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:700">
                View →
              </a>
            </div>
            """.formatted(campaignName, reason, appLink("/campaigns"))
        );
    }

    @Async
    public void notifyLowBalance(double balance) {
        log.warn("💸 Low wallet balance: ₹{}", balance);
        if (!canSend()) return;
        sendHtml(
            "💸 Low Wallet Balance",
            """
            <div style="font-family:sans-serif;padding:24px;background:#0d1b2a;color:#f1f5f9">
              <h2 style="color:#ef4444">💸 Wallet Balance is Low</h2>
              <p>Balance: <strong style="color:#ef4444">₹%.0f</strong></p>
              <a href="%s"
                 style="background:#00d4ff;color:#000;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:700">
                Add Funds →
              </a>
            </div>
            """.formatted(balance, appLink("/settings"))
        );
    }

    @Async
    public void notifyReportReady(String toEmail, String reportTitle, String from, String to) {
        log.info("📊 Report ready: {} | {} to {} → {}", reportTitle, from, to, toEmail);
        if (!canSend()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[DolphinAI] " + reportTitle + " Ready");
            helper.setText("""
                <div style="font-family:sans-serif;padding:24px;background:#0d1b2a;color:#f1f5f9">
                  <h2 style="color:#00d4ff">📊 Your %s is Ready</h2>
                  <p>Reporting Period: <strong>%s to %s</strong></p>
                  <p>Your autonomous AI marketing report has been generated. Download it from the dashboard.</p>
                  <br/>
                  <a href="%s"
                     style="background:#00d4ff;color:#000;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:700">
                    View Dashboard →
                  </a>
                  <br/><br/>
                  <p style="color:#64748b;font-size:12px">This report was generated autonomously by the DolphinAI Brain.</p>
                </div>
                """.formatted(reportTitle, from, to, appLink("/analytics")), true);
            helper.setFrom("noreply@dolphin.ai");
            mailSender.send(msg);
            log.info("📧 Report notification sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send report email: {}", e.getMessage());
        }
    }

    private boolean canSend() {
        if (!alertsEnabled) { log.debug("Alerts disabled — set alerts.enabled=true to enable emails"); return false; }
        if (mailSender == null) { log.debug("Mail not configured — set MAIL_USER and MAIL_PASS to enable emails"); return false; }
        return true;
    }

    private void sendHtml(String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(alertEmail);
            helper.setSubject("[DolphinAI] " + subject);
            helper.setText(html, true);
            helper.setFrom("noreply@dolphin.ai");
            mailSender.send(msg);
            log.info("📧 Alert sent: {}", subject);
        } catch (MessagingException e) {
            log.error("Failed to send alert email: {}", e.getMessage());
        }
    }

    private String appLink(String path) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:4200" : frontendUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
