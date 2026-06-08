package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportService {

    private final CampaignRepository campaignRepo;
    private final MetricSnapshotRepository snapshotRepo;

    public ReportService(CampaignRepository campaignRepo, MetricSnapshotRepository snapshotRepo) {
        this.campaignRepo = campaignRepo;
        this.snapshotRepo = snapshotRepo;
    }

    /**
     * Generates a beautifully-designed, professional marketing analytics PDF report
     * for all campaigns belonging to a specific workspace within a given date range.
     */
    public byte[] generateCampaignReportPdf(String accountId, LocalDate start, LocalDate end) {
        log.info("📄 Compiling PDF Analytics Report for workspace: {} | range: {} to {}", accountId, start, end);

        // Fetch live metrics and active campaigns
        List<Campaign> campaigns = campaignRepo.findByAccountId(accountId);
        List<MetricSnapshot> snapshots = snapshotRepo.findByAccountIdAndDateRange(accountId, start, end);

        // Map snapshots by campaignId for easy calculation
        Map<String, List<MetricSnapshot>> campaignMetricsMap = snapshots.stream()
                .collect(Collectors.groupingBy(MetricSnapshot::getCampaignId));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // ── Colors & Fonts (Aesthetics) ──────────────────────────
            Color primaryColor = new Color(30, 41, 59);    // Slate Blue
            Color secondaryColor = new Color(13, 148, 136); // Teal
            Color lightGrayRow = new Color(248, 250, 252);  // Alternate Row Background
            Color accentColor = new Color(224, 242, 254);   // Sky Accent Blue

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, primaryColor);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            Font sectionHeadingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, primaryColor);
            Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 9, primaryColor);
            Font tdBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, primaryColor);
            Font cardTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, secondaryColor);
            Font cardValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, primaryColor);

            // ── Header Banner ──────────────────────────────────────────
            Paragraph title = new Paragraph("🐬 Chubby Dolphin AI", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            document.add(title);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
            Paragraph subtitle = new Paragraph("Autonomous Marketing Brain — Performance Analytics Report\n" +
                    "Reporting Period: " + start.format(formatter) + " - " + end.format(formatter), subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(24);
            document.add(subtitle);

            // ── Aggregate Data Calculations ────────────────────────────
            double totalSpend = 0.0;
            long totalImpressions = 0L;
            long totalClicks = 0L;
            long totalConversions = 0L;
            double totalRevenue = 0.0;

            for (MetricSnapshot s : snapshots) {
                totalSpend += (s.getSpend() != null ? s.getSpend() : 0.0);
                totalImpressions += (s.getImpressions() != null ? s.getImpressions() : 0L);
                totalClicks += (s.getClicks() != null ? s.getClicks() : 0L);
                totalConversions += (s.getConversions() != null ? s.getConversions() : 0L);
                totalRevenue += (s.getRevenue() != null ? s.getRevenue() : 0.0);
            }

            double overallCtr = totalImpressions > 0 ? ((double) totalClicks / totalImpressions) * 100 : 0.0;
            double overallCpc = totalClicks > 0 ? totalSpend / totalClicks : 0.0;
            double overallRoas = totalSpend > 0 ? totalRevenue / totalSpend : 0.0;

            // ── Executive KPI Dashboard Cards (2x2 Grid Table) ─────────
            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(24);
            kpiTable.setWidths(new float[]{1, 1, 1, 1});

            addKpiCard(kpiTable, "Total Spend", String.format("₹%,.2f", totalSpend), cardTitleFont, cardValFont, accentColor);
            addKpiCard(kpiTable, "Impressions", String.format("%,d", totalImpressions), cardTitleFont, cardValFont, accentColor);
            addKpiCard(kpiTable, "Conversions", String.format("%,d", totalConversions), cardTitleFont, cardValFont, accentColor);
            addKpiCard(kpiTable, "ROAS", String.format("%.2fx", overallRoas), cardTitleFont, cardValFont, accentColor);

            document.add(kpiTable);

            // ── Campaign Performance Overview Section ──────────────────
            Paragraph sectionHeading = new Paragraph("Campaign Performance Overview", sectionHeadingFont);
            sectionHeading.setSpacingAfter(10);
            document.add(sectionHeading);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.0f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f, 1.0f});
            table.setSpacingAfter(24);

            // Headers
            String[] headers = {"Campaign Name", "Status", "Spend", "Impressions", "CTR", "Conversions", "ROAS"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, thFont));
                cell.setBackgroundColor(primaryColor);
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cell);
            }

            // Populate rows
            boolean isAlt = false;
            for (Campaign c : campaigns) {
                List<MetricSnapshot> cMetrics = campaignMetricsMap.get(c.getId());
                double cSpend = 0.0;
                long cImps = 0L;
                long cClicks = 0L;
                long cConvs = 0L;
                double cRev = 0.0;

                if (cMetrics != null) {
                    for (MetricSnapshot s : cMetrics) {
                        cSpend += (s.getSpend() != null ? s.getSpend() : 0.0);
                        cImps += (s.getImpressions() != null ? s.getImpressions() : 0L);
                        cClicks += (s.getClicks() != null ? s.getClicks() : 0L);
                        cConvs += (s.getConversions() != null ? s.getConversions() : 0L);
                        cRev += (s.getRevenue() != null ? s.getRevenue() : 0.0);
                    }
                }

                double cCtr = cImps > 0 ? ((double) cClicks / cImps) * 100 : 0.0;
                double cRoas = cSpend > 0 ? cRev / cSpend : 0.0;

                Color rowBg = isAlt ? lightGrayRow : Color.WHITE;
                isAlt = !isAlt;

                table.addCell(createCell(c.getName(), tdFont, Element.ALIGN_LEFT, rowBg));
                table.addCell(createCell(c.getStatus(), tdBoldFont, Element.ALIGN_CENTER, rowBg));
                table.addCell(createCell(String.format("₹%,.2f", cSpend), tdFont, Element.ALIGN_RIGHT, rowBg));
                table.addCell(createCell(String.format("%,d", cImps), tdFont, Element.ALIGN_RIGHT, rowBg));
                table.addCell(createCell(String.format("%.2f%%", cCtr), tdFont, Element.ALIGN_RIGHT, rowBg));
                table.addCell(createCell(String.format("%,d", cConvs), tdFont, Element.ALIGN_RIGHT, rowBg));
                table.addCell(createCell(String.format("%.2fx", cRoas), tdBoldFont, Element.ALIGN_RIGHT, rowBg));
            }

            document.add(table);

            // ── AI Optimization Summary Card ───────────────────────────
            Paragraph aiSummaryHeading = new Paragraph("AI Brain Autonomous Strategy Log", sectionHeadingFont);
            aiSummaryHeading.setSpacingAfter(8);
            document.add(aiSummaryHeading);

            PdfPTable aiCardTable = new PdfPTable(1);
            aiCardTable.setWidthPercentage(100);
            PdfPCell aiCell = new PdfPCell();
            aiCell.setBackgroundColor(lightGrayRow);
            aiCell.setPadding(12);
            aiCell.setBorderColor(new Color(203, 213, 225)); // Slate-200 border

            Paragraph aiParagraph = new Paragraph();
            aiParagraph.add(new Chunk("🤖 Active AI Strategy Model: ", tdBoldFont));
            aiParagraph.add(new Chunk("Autonomous Decision Multi-LLM Router (Ollama/Mistral-7B Primary)\n", tdFont));
            aiParagraph.add(new Chunk("🛡️ Safety Constraints Enforced: ", tdBoldFont));
            aiParagraph.add(new Chunk("Daily Campaign Budgets checked under pre-flight Rules Engine. All scaling operations strictly limited to 30% per cycle to guard advertising capital against volatility.\n", tdFont));
            aiParagraph.add(new Chunk("💡 Optimizer Insights: ", tdBoldFont));
            
            if (overallRoas >= 2.0) {
                aiParagraph.add(new Chunk("Strong campaign health detected with positive ROI trends. Recommend continuing autonomous budget scaling on top-performing asset configurations.", tdFont));
            } else {
                aiParagraph.add(new Chunk("ROAS threshold indicates minor optimization adjustments required. The AI Brain is actively running micro-adjustments and CTR copy A/B tests to lower CPA.", tdFont));
            }

            aiCell.addElement(aiParagraph);
            aiCardTable.addCell(aiCell);
            document.add(aiCardTable);

            document.close();
        } catch (Exception e) {
            log.error("Failed to generate PDF Report: ", e);
            throw new RuntimeException("Could not generate analytics PDF report", e);
        }

        return out.toByteArray();
    }

    private void addKpiCard(PdfPTable table, String label, String val, Font labelFont, Font valFont, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setPadding(12);
        cell.setBorderColor(new Color(186, 230, 253)); // Light blue border
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph labelPara = new Paragraph(label.toUpperCase(), labelFont);
        labelPara.setAlignment(Element.ALIGN_CENTER);
        labelPara.setSpacingAfter(6);
        cell.addElement(labelPara);

        Paragraph valPara = new Paragraph(val, valFont);
        valPara.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(valPara);

        table.addCell(cell);
    }

    private PdfPCell createCell(String text, Font font, int alignment, Color bg) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setPadding(6);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(new Color(226, 232, 240)); // Slate-100 border
        return cell;
    }
}
