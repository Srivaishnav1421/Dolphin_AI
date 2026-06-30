package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Invoice;
import com.chubby.dolphin.entity.InvoiceSequence;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.InvoiceRepository;
import com.chubby.dolphin.repository.InvoiceSequenceRepository;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GstInvoiceService — GST-compliant PDF invoice generation using OpenPDF.
 *
 * DA-052: Storage abstracted via FileStorageService (no hardcoded paths).
 * Generates CGST/SGST for intra-state and IGST for inter-state transactions.
 * Invoice numbers are sequential and gapless per Indian FY (FY starts April 1st).
 */
@Service
@Slf4j
@Transactional
public class GstInvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final InvoiceSequenceRepository sequenceRepo;
    private final WorkspaceConfigRepository configRepo;
    private final FileStorageService fileStorageService;

    @Value("${chubby.corp.state-code:MH}")
    private String corporateStateCode;

    @Value("${chubby.corp.legal-name:DolphinAI Private Limited}")
    private String corporateLegalName;

    @Value("${chubby.corp.gstin:27AAACC4111D1Z5}")
    private String corporateGstin;

    @Value("${chubby.corp.address:102 Alpha Towers, Bandra Kurla Complex, Mumbai, MH - 400051}")
    private String corporateAddress;

    @Value("${chubby.corp.sac-code:997331}")
    private String sacCode;

    public GstInvoiceService(InvoiceRepository invoiceRepo,
                             InvoiceSequenceRepository sequenceRepo,
                             WorkspaceConfigRepository configRepo,
                             FileStorageService fileStorageService) {
        this.invoiceRepo = invoiceRepo;
        this.sequenceRepo = sequenceRepo;
        this.configRepo = configRepo;
        this.fileStorageService = fileStorageService;
    }

    // ── Financial Year Helpers ────────────────────────────────────────────────

    /**
     * Determines the active Indian Financial Year key (April 1 → March 31).
     * e.g., date=2026-05-15 → "2026-2027"
     */
    public String getActiveFinancialYearKey(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= 4) {
            return String.format("%d-%d", year, year + 1);
        } else {
            return String.format("%d-%d", year - 1, year);
        }
    }

    /**
     * Generates a unique, gapless, sequential invoice number.
     * Pessimistic write lock prevents concurrent duplicate numbers.
     * Format: CD-YYZZ-NNNN (e.g., CD-2627-0015)
     */
    public String generateNextInvoiceNumber(LocalDate date) {
        String fyKey = getActiveFinancialYearKey(date);

        InvoiceSequence seq = sequenceRepo.findAndLockByYearKey(fyKey)
                .orElseGet(() -> {
                    InvoiceSequence newSeq = new InvoiceSequence(fyKey, 0);
                    return sequenceRepo.saveAndFlush(newSeq);
                });

        int nextNum = seq.getLastNumber() + 1;
        seq.setLastNumber(nextNum);
        sequenceRepo.save(seq);

        String[] parts = fyKey.split("-");
        String shortYears = parts[0].substring(2) + parts[1].substring(2);
        return String.format("CD-%s-%04d", shortYears, nextNum);
    }

    // ── Invoice Creation ──────────────────────────────────────────────────────

    /**
     * Creates a GST-compliant invoice: calculates CGST/SGST or IGST,
     * generates an OpenPDF document, stores it via FileStorageService,
     * and persists the invoice record with the pdf_url path.
     */
    public Invoice createInvoice(String workspaceId, String transactionId, double subtotal) {
        log.info("🧾 Creating GST Invoice: workspace={}, transactionId={}, amount=₹{}", workspaceId, transactionId, subtotal);

        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    WorkspaceConfig c = new WorkspaceConfig();
                    c.setWorkspaceId(workspaceId);
                    c.setLegalName("Enterprise Buyer Inc.");
                    c.setStateCode("DL");
                    c.setGstin("07AAAAA1111A1Z0");
                    c.setBillingAddress("Connaught Place, New Delhi, DL - 110001");
                    return configRepo.save(c);
                });

        // Determine GST type: intra-state (CGST+SGST) vs inter-state (IGST)
        String buyerState  = config.getStateCode() != null ? config.getStateCode().trim().toUpperCase() : "DL";
        String sellerState = corporateStateCode.trim().toUpperCase();
        boolean isIntraState = buyerState.equals(sellerState);

        double gstRate   = 0.18; // 18% standard for software/SaaS (SAC 997331)
        double gstAmount = Math.round(subtotal * gstRate * 100.0) / 100.0;
        double total     = subtotal + gstAmount;
        String gstType   = isIntraState ? "CGST_SGST" : "IGST";

        LocalDate today      = LocalDate.now();
        String invoiceNum    = generateNextInvoiceNumber(today);

        // Build Invoice entity
        Invoice invoice = new Invoice();
        invoice.setWorkspaceId(workspaceId);
        invoice.setInvoiceNumber(invoiceNum);
        invoice.setTransactionId(transactionId);
        invoice.setSubtotal(subtotal);
        invoice.setGstRate(gstRate);
        invoice.setGstAmount(gstAmount);
        invoice.setGstType(gstType);
        invoice.setTotal(total);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setStatus("UNPAID");
        invoice.setSacCode(sacCode);
        invoice.setSellerGstin(corporateGstin);
        invoice.setBuyerGstin(config.getGstin());
        invoice.setGstinVerified(config.getGstin() != null && config.getGstin().length() == 15);
        invoice.setCurrency("INR");
        invoice.setPlaceOfSupply(buyerState);
        invoice.setInvoiceDate(LocalDateTime.now());
        invoice.setDueDate(LocalDateTime.now().plusDays(15));

        // Generate PDF and store via abstracted storage service (DA-052)
        String pdfPath = generateAndStorePdf(invoice, config);
        invoice.setPdfPath(pdfPath);

        Invoice saved = invoiceRepo.save(invoice);
        log.info("✅ GST Invoice {} created | Type={} | Total=₹{} | PDF={}", invoiceNum, gstType, total, pdfPath);
        return saved;
    }

    // ── PDF Generation ────────────────────────────────────────────────────────

    /**
     * Generates a professional GST-compliant PDF and stores it via FileStorageService.
     * Returns the resolved storage path or empty string on error.
     */
    private String generateAndStorePdf(Invoice invoice, WorkspaceConfig config) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // ── Fonts & Colors ──────────────────────────────────────────────
            Font titleFont       = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(13, 148, 136));
            Font subTitleFont    = new Font(Font.HELVETICA, 10, Font.ITALIC, Color.GRAY);
            Font sectionFont     = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
            Font bodyFont        = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Font boldBodyFont    = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
            Font tableHeaderFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font bigTotalFont    = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(13, 148, 136));

            // ── Header ──────────────────────────────────────────────────────
            Paragraph title   = new Paragraph(corporateLegalName, titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            Paragraph tagline = new Paragraph("Autonomous Marketing & Growth AI Suite", subTitleFont);
            tagline.setAlignment(Element.ALIGN_LEFT);
            document.add(title);
            document.add(tagline);
            document.add(new Paragraph("\n"));

            // ── Seller / Invoice Meta ────────────────────────────────────────
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{1f, 1f});

            PdfPCell sellerCell = new PdfPCell();
            sellerCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            sellerCell.addElement(new Paragraph("ISSUED BY:", boldBodyFont));
            sellerCell.addElement(new Paragraph(corporateLegalName, boldBodyFont));
            sellerCell.addElement(new Paragraph("GSTIN: " + corporateGstin, bodyFont));
            sellerCell.addElement(new Paragraph("SAC Code: " + sacCode, bodyFont));
            sellerCell.addElement(new Paragraph(corporateAddress, bodyFont));
            metaTable.addCell(sellerCell);

            PdfPCell infoCell = new PdfPCell();
            infoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            infoCell.addElement(new Paragraph("TAX INVOICE", boldBodyFont));
            infoCell.addElement(new Paragraph("Invoice No: " + invoice.getInvoiceNumber(), boldBodyFont));
            infoCell.addElement(new Paragraph("Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), bodyFont));
            infoCell.addElement(new Paragraph("Due Date: " + LocalDate.now().plusDays(15).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), bodyFont));
            infoCell.addElement(new Paragraph("Currency: INR (Indian Rupees)", bodyFont));
            if (invoice.getTransactionId() != null) {
                infoCell.addElement(new Paragraph("Transaction: " + invoice.getTransactionId(), bodyFont));
            }
            metaTable.addCell(infoCell);
            document.add(metaTable);
            document.add(new Paragraph("\n"));

            // ── Buyer Card ───────────────────────────────────────────────────
            PdfPTable buyerTable = new PdfPTable(1);
            buyerTable.setWidthPercentage(100);
            PdfPCell buyerCell = new PdfPCell();
            buyerCell.setBackgroundColor(new Color(243, 244, 246));
            buyerCell.setPadding(10);
            buyerCell.setBorderColor(new Color(229, 231, 235));
            buyerCell.addElement(new Paragraph("BILLED TO:", boldBodyFont));
            buyerCell.addElement(new Paragraph(config.getLegalName() != null ? config.getLegalName() : "Enterprise Partner", boldBodyFont));
            buyerCell.addElement(new Paragraph("GSTIN: " + (config.getGstin() != null ? config.getGstin() : "Unregistered"), bodyFont));
            buyerCell.addElement(new Paragraph("Place of Supply: " + invoice.getPlaceOfSupply(), bodyFont));
            buyerCell.addElement(new Paragraph("Address: " + (config.getBillingAddress() != null ? config.getBillingAddress() : "N/A"), bodyFont));
            buyerTable.addCell(buyerCell);
            document.add(buyerTable);
            document.add(new Paragraph("\n"));

            // ── Line Items Table ─────────────────────────────────────────────
            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{4f, 1.5f, 2f, 1.5f});

            for (String h : new String[]{"Description", "SAC Code", "Amount (₹)", "HSN/SAC"}) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, tableHeaderFont));
                cell.setBackgroundColor(new Color(13, 148, 136));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemsTable.addCell(cell);
            }

            addItemRow(itemsTable, "DolphinAI Subscription — " + determinePlanLabel(invoice.getSubtotal()), sacCode,
                    String.format("₹%.2f", invoice.getSubtotal()), sacCode, bodyFont);
            document.add(itemsTable);
            document.add(new Paragraph("\n"));

            // ── GST Breakdown Table ──────────────────────────────────────────
            PdfPTable gstTable = new PdfPTable(2);
            gstTable.setWidthPercentage(50);
            gstTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            gstTable.setWidths(new float[]{2f, 1f});

            addSummaryRow(gstTable, "Subtotal:", String.format("₹%.2f", invoice.getSubtotal()), bodyFont, boldBodyFont);

            double halfGst = invoice.getGstAmount() / 2.0;
            if ("CGST_SGST".equals(invoice.getGstType())) {
                addSummaryRow(gstTable, "CGST (9%):", String.format("₹%.2f", halfGst), bodyFont, bodyFont);
                addSummaryRow(gstTable, "SGST (9%):", String.format("₹%.2f", halfGst), bodyFont, bodyFont);
            } else {
                addSummaryRow(gstTable, "IGST (18%):", String.format("₹%.2f", invoice.getGstAmount()), bodyFont, bodyFont);
            }

            addSummaryRow(gstTable, "TOTAL:", String.format("₹%.2f", invoice.getTotal()), bigTotalFont, bigTotalFont);
            document.add(gstTable);
            document.add(new Paragraph("\n"));

            // ── Legal Footer ─────────────────────────────────────────────────
            Paragraph terms = new Paragraph(
                    "This is a computer-generated GST Tax Invoice under Section 31 of the CGST Act 2017. " +
                    "No physical signature required. | " + corporateLegalName + " | GSTIN: " + corporateGstin,
                    subTitleFont);
            terms.setAlignment(Element.ALIGN_CENTER);
            document.add(terms);

            document.close();

            // Store via abstracted storage service (DA-052)
            String fileKey = "invoices/" + invoice.getInvoiceNumber() + ".pdf";
            byte[] pdfBytes = baos.toByteArray();
            return fileStorageService.store(fileKey, new ByteArrayInputStream(pdfBytes), "application/pdf");

        } catch (Exception e) {
            log.error("❌ PDF generation failed for invoice: {}", invoice.getInvoiceNumber(), e);
            return "";
        }
    }

    private void addItemRow(PdfPTable table, String desc, String sac, String amount, String hsn, Font font) {
        for (String val : new String[]{desc, sac, amount, hsn}) {
            PdfPCell cell = new PdfPCell(new Paragraph(val, font));
            cell.setPadding(8);
            cell.setHorizontalAlignment(val.startsWith("₹") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, labelFont));
        labelCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value, valueFont));
        valueCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        valueCell.setPadding(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String determinePlanLabel(double subtotal) {
        if (subtotal <= 1999) return "Starter Plan";
        if (subtotal <= 4999) return "Growth Plan";
        if (subtotal <= 9999) return "Pro Plan";
        return "Enterprise Plan";
    }
}
