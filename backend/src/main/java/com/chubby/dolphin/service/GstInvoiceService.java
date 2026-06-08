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
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class GstInvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final InvoiceSequenceRepository sequenceRepo;
    private final WorkspaceConfigRepository configRepo;

    @Value("${chubby.corp.state-code:MH}")
    private String corporateStateCode;

    @Value("${chubby.corp.legal-name:DolphinAI Private Limited}")
    private String corporateLegalName;

    @Value("${chubby.corp.gstin:27AAACC4111D1Z5}")
    private String corporateGstin;

    @Value("${chubby.corp.address:102 Alpha Towers, Bandra Kurla Complex, Mumbai, MH - 400051}")
    private String corporateAddress;

    public GstInvoiceService(InvoiceRepository invoiceRepo,
                             InvoiceSequenceRepository sequenceRepo,
                             WorkspaceConfigRepository configRepo) {
        this.invoiceRepo = invoiceRepo;
        this.sequenceRepo = sequenceRepo;
        this.configRepo = configRepo;
    }

    /**
     * Determines the active Indian Financial Year (starts April 1st, ends March 31st).
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
     * Pessimistic write locking on YearKey prevents duplicate invoice numbers.
     */
    public String generateNextInvoiceNumber(LocalDate date) {
        String fyKey = getActiveFinancialYearKey(date); // e.g., "2026-2027"
        
        InvoiceSequence seq = sequenceRepo.findAndLockByYearKey(fyKey)
                .orElseGet(() -> {
                    InvoiceSequence newSeq = new InvoiceSequence(fyKey, 0);
                    return sequenceRepo.saveAndFlush(newSeq);
                });

        int nextNum = seq.getLastNumber() + 1;
        seq.setLastNumber(nextNum);
        sequenceRepo.save(seq);

        // Format short years for visual representation, e.g., 2026-2027 -> 2627
        String[] parts = fyKey.split("-");
        String shortYears = parts[0].substring(2) + parts[1].substring(2);

        return String.format("CD-%s-%04d", shortYears, nextNum);
    }

    /**
     * Creates a new GST invoice for a workspace transaction, calculates CGST/SGST/IGST,
     * generates a premium OpenPDF design, and stores the invoice record.
     */
    public Invoice createInvoice(String workspaceId, String transactionId, double subtotal) {
        log.info("🧾 Creating dynamic GST Invoice for workspace: {} | Amount: ₹{}", workspaceId, subtotal);

        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    WorkspaceConfig c = new WorkspaceConfig();
                    c.setWorkspaceId(workspaceId);
                    c.setLegalName("Enterprise Buyer Inc.");
                    c.setStateCode("DL"); // Default to Delhi (IGST)
                    c.setGstin("07AAAAA1111A1Z0");
                    c.setBillingAddress("Connaught Place, New Delhi, DL - 110001");
                    return configRepo.save(c);
                });

        // Determine GST type based on state code match
        String buyerState = config.getStateCode() != null ? config.getStateCode().trim().toUpperCase() : "DL";
        String sellerState = corporateStateCode.trim().toUpperCase();

        double gstRate = 0.18; // Standard 18% GST for software/AI SaaS services
        double gstAmount = subtotal * gstRate;
        double total = subtotal + gstAmount;
        String gstType = buyerState.equals(sellerState) ? "CGST_SGST" : "IGST";

        LocalDate today = LocalDate.now();
        String invoiceNum = generateNextInvoiceNumber(today);

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

        // Generate dynamic PDF representation
        String pdfPath = generateInvoicePdf(invoice, config);
        invoice.setPdfPath(pdfPath);

        log.info("✅ GST Invoice {} created and PDF saved at: {}", invoiceNum, pdfPath);
        return invoiceRepo.save(invoice);
    }

    /**
     * Generates a beautifully formatted white-label commercial PDF using the OpenPDF engine.
     */
    private String generateInvoicePdf(Invoice invoice, WorkspaceConfig config) {
        try {
            // Guarantee target directory exists
            File storageDir = new File("storage/invoices");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            String filePath = "storage/invoices/" + invoice.getInvoiceNumber() + ".pdf";
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));

            document.open();

            // --- Styling & Colors ---
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(13, 148, 136)); // Sleek Teal
            Font subTitleFont = new Font(Font.HELVETICA, 10, Font.ITALIC, Color.GRAY);
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
            Font bodyFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
            Font boldBodyFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
            Font tableHeaderFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);

            // --- Top Banner Header ---
            Paragraph title = new Paragraph(corporateLegalName, titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            Paragraph tagline = new Paragraph("Autonomous Marketing & Dynamic AI Bidding Suite", subTitleFont);
            tagline.setAlignment(Element.ALIGN_LEFT);
            
            document.add(title);
            document.add(tagline);
            document.add(new Paragraph("\n"));

            // --- Billing and Metadata Blocks ---
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{1f, 1f});

            // Seller Card (Left Column)
            PdfPCell sellerCell = new PdfPCell();
            sellerCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            sellerCell.addElement(new Paragraph("ISSUED BY:", boldBodyFont));
            sellerCell.addElement(new Paragraph(corporateLegalName, boldBodyFont));
            sellerCell.addElement(new Paragraph("GSTIN: " + corporateGstin, bodyFont));
            sellerCell.addElement(new Paragraph(corporateAddress, bodyFont));
            metaTable.addCell(sellerCell);

            // Invoice Info Card (Right Column)
            PdfPCell infoCell = new PdfPCell();
            infoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            infoCell.addElement(new Paragraph("INVOICE SUMMARY:", boldBodyFont));
            infoCell.addElement(new Paragraph("Invoice No: " + invoice.getInvoiceNumber(), boldBodyFont));
            infoCell.addElement(new Paragraph("Date: " + invoice.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")), bodyFont));
            infoCell.addElement(new Paragraph("Transaction ID: " + (invoice.getTransactionId() != null ? invoice.getTransactionId() : "N/A"), bodyFont));
            metaTable.addCell(infoCell);

            document.add(metaTable);
            document.add(new Paragraph("\n"));

            // Buyer Card
            PdfPTable buyerTable = new PdfPTable(1);
            buyerTable.setWidthPercentage(100);
            PdfPCell buyerCell = new PdfPCell();
            buyerCell.setBackgroundColor(new Color(243, 244, 246)); // Slate Gray background
            buyerCell.setPadding(10);
            buyerCell.setBorderColor(new Color(229, 231, 235));
            buyerCell.addElement(new Paragraph("BILLED TO (CLIENT):", boldBodyFont));
            buyerCell.addElement(new Paragraph(config.getLegalName() != null ? config.getLegalName() : "Enterprise Partner", boldBodyFont));
            buyerCell.addElement(new Paragraph("GSTIN: " + (config.getGstin() != null ? config.getGstin() : "Unregistered"), bodyFont));
            buyerCell.addElement(new Paragraph("Billing Address: " + (config.getBillingAddress() != null ? config.getBillingAddress() : "Billing Address Not Configured"), bodyFont));
            buyerCell.addElement(new Paragraph("State Code: " + (config.getStateCode() != null ? config.getStateCode() : "N/A"), bodyFont));
            buyerTable.addCell(buyerCell);

            document.add(buyerTable);
            document.add(new Paragraph("\n"));

            // --- Product / Breakdown Table ---
            PdfPTable itemsTable = new PdfPTable(5);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{3f, 1f, 1f, 1f, 1f});

            // Headers
            String[] headers = {"Description", "Unit Cost", "Subtotal", "GST Breakdown", "Total Amount"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, tableHeaderFont));
                cell.setBackgroundColor(new Color(13, 148, 136)); // Teal Header
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemsTable.addCell(cell);
            }

            // Description Cell
            PdfPCell descCell = new PdfPCell(new Paragraph("DolphinAI Premium Growth Plan - Enterprise Subscription", bodyFont));
            descCell.setPadding(8);
            itemsTable.addCell(descCell);

            // Unit Cost
            PdfPCell costCell = new PdfPCell(new Paragraph(String.format("₹%.2f", invoice.getSubtotal()), bodyFont));
            costCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            costCell.setPadding(8);
            itemsTable.addCell(costCell);

            // Subtotal
            PdfPCell subCell = new PdfPCell(new Paragraph(String.format("₹%.2f", invoice.getSubtotal()), bodyFont));
            subCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            subCell.setPadding(8);
            itemsTable.addCell(subCell);

            // GST Breakdown
            String gstBreakdownText;
            if ("CGST_SGST".equals(invoice.getGstType())) {
                double halfGst = invoice.getGstAmount() / 2.0;
                gstBreakdownText = String.format("CGST (9%%): ₹%.2f\nSGST (9%%): ₹%.2f", halfGst, halfGst);
            } else {
                gstBreakdownText = String.format("IGST (18%%): ₹%.2f", invoice.getGstAmount());
            }
            PdfPCell gstCell = new PdfPCell(new Paragraph(gstBreakdownText, bodyFont));
            gstCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            gstCell.setPadding(8);
            itemsTable.addCell(gstCell);

            // Total Amount
            PdfPCell totalCell = new PdfPCell(new Paragraph(String.format("₹%.2f", invoice.getTotal()), boldBodyFont));
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setPadding(8);
            itemsTable.addCell(totalCell);

            document.add(itemsTable);
            document.add(new Paragraph("\n"));

            // --- Footer Signoff / Notes ---
            Paragraph terms = new Paragraph("Thank you for choosing DolphinAI! This invoice is digitally generated and requires no physical signature.", subTitleFont);
            terms.setAlignment(Element.ALIGN_CENTER);
            document.add(terms);

            document.close();
            return new File(filePath).getAbsolutePath();

        } catch (Exception e) {
            log.error("❌ Failed to compile OpenPDF invoice for number: {}", invoice.getInvoiceNumber(), e);
            return "";
        }
    }
}
