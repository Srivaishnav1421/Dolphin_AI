package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final SecurityUtils securityUtils;
    private final AccessControlService access;
    private final AuditLogService auditLogService;

    public ReportController(ReportService reportService, SecurityUtils securityUtils,
                            AccessControlService access, AuditLogService auditLogService) {
        this.reportService = reportService;
        this.securityUtils = securityUtils;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    /**
     * Downloads a professional campaign performance PDF report for the active workspace.
     * Enforces strict security by fetching data bounded by securityUtils.currentAccountId().
     */
    @GetMapping("/campaigns/pdf")
    public ResponseEntity<byte[]> downloadCampaignReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        access.requireWorkspacePermission(Permission.REPORT_EXPORT);
        String activeAccountId = securityUtils.currentWorkspaceId();
        
        // Default range: Last 30 Days if not specified
        LocalDate startDate = start != null ? start : LocalDate.now().minusDays(30);
        LocalDate endDate = end != null ? end : LocalDate.now();

        log.info("📥 PDF Download Request — User: {} | Workspace: {} | Range: {} to {}",
                securityUtils.currentEmail(), activeAccountId, startDate, endDate);

        byte[] pdfBytes = reportService.generateCampaignReportPdf(activeAccountId, startDate, endDate);
        auditLogService.record(access.currentUser(), access.currentUser().getOrganization(), activeAccountId,
                "REPORT_EXPORTED", "CampaignReport", activeAccountId,
                "format=pdf; start=" + startDate + "; end=" + endDate);

        String filename = String.format("chubby_dolphin_report_%s_to_%s.pdf", startDate, endDate);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
