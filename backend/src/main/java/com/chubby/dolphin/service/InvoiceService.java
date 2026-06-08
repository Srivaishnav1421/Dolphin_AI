package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Invoice;
import com.chubby.dolphin.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Value("${dolphin.billing.seller.gstin:29AAAAA1111A1Z1}")
    private String sellerGstin;

    @Value("${dolphin.billing.seller.state:KA}")
    private String sellerState; // Default Merchant State: Karnataka (KA)

    @Transactional
    public Invoice generateInvoice(String workspaceId, double amountSubtotal, String buyerGstin, String placeOfSupply) {
        if (amountSubtotal < 0) {
            throw new IllegalArgumentException("Subtotal must be non-negative.");
        }

        // India GST logic: 18% standard rate for software licensing (SAC 997331)
        double gstRate = 18.00;
        double cgst = 0.0;
        double sgst = 0.0;
        double igst = 0.0;

        // Determine if supply is intra-state or inter-state
        if (sellerState.equalsIgnoreCase(placeOfSupply)) {
            cgst = Math.round((amountSubtotal * 0.09) * 100.0) / 100.0;
            sgst = Math.round((amountSubtotal * 0.09) * 100.0) / 100.0;
        } else {
            igst = Math.round((amountSubtotal * 0.18) * 100.0) / 100.0;
        }

        double amountTotal = amountSubtotal + cgst + sgst + igst;
        String invoiceNumber = "INV-" + LocalDateTime.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String pdfUrl = "https://dolphin-billing.s3.ap-south-1.amazonaws.com/invoices/" + invoiceNumber + ".pdf";

        Invoice invoice = Invoice.builder()
                .workspaceId(workspaceId)
                .invoiceNumber(invoiceNumber)
                .status("UNPAID")
                .amountSubtotal(amountSubtotal)
                .gstRate(gstRate)
                .cgst(cgst)
                .sgst(sgst)
                .igst(igst)
                .amountTotal(amountTotal)
                .sellerGstin(sellerGstin)
                .buyerGstin(buyerGstin)
                .gstinVerified(buyerGstin != null && buyerGstin.length() == 15)
                .sacCode("997331")
                .invoiceDate(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(15))
                .invoiceVersion(1)
                .pdfUrl(pdfUrl)
                .currency("INR")
                .placeOfSupply(placeOfSupply)
                .createdAt(LocalDateTime.now())
                .build();

        log.info("GST compliant invoice generated: number={}, subtotal={}, total={}, state={}, pdf={}", 
                invoiceNumber, amountSubtotal, amountTotal, placeOfSupply, pdfUrl);

        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<Invoice> getWorkspaceInvoices(String workspaceId) {
        return invoiceRepository.findByWorkspaceId(workspaceId);
    }

    @Transactional
    public void markAsPaid(String workspaceId, String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found or workspace mismatch."));
        invoice.setStatus("PAID");
        invoiceRepository.save(invoice);
        log.info("Invoice marked as PAID: id={}, workspace={}", invoiceId, workspaceId);
    }
}
