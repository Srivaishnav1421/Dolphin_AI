package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false)
    @Builder.Default
    private String status = "UNPAID"; // PAID, UNPAID, VOID

    // Legacy fields from V7 schema
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "subtotal")
    @Builder.Default
    private Double subtotal = 0.00;

    @Column(name = "gst_amount")
    @Builder.Default
    private Double gstAmount = 0.00;

    @Column(name = "gst_type")
    private String gstType; // CGST_SGST, IGST

    @Column(name = "total")
    @Builder.Default
    private Double total = 0.00;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // New billing engine fields from V23 schema
    @Column(name = "amount_subtotal")
    @Builder.Default
    private Double amountSubtotal = 0.00;

    @Column(name = "gst_rate")
    @Builder.Default
    private Double gstRate = 18.00;

    @Column(nullable = false)
    @Builder.Default
    private Double cgst = 0.00;

    @Column(nullable = false)
    @Builder.Default
    private Double sgst = 0.00;

    @Column(nullable = false)
    @Builder.Default
    private Double igst = 0.00;

    @Column(name = "amount_total")
    @Builder.Default
    private Double amountTotal = 0.00;

    @Column(name = "seller_gstin")
    private String sellerGstin;

    @Column(name = "buyer_gstin")
    private String buyerGstin;

    @Column(name = "customer_gstin_verified")
    @Builder.Default
    private Boolean gstinVerified = false;

    @Column(name = "sac_code")
    @Builder.Default
    private String sacCode = "997331";

    @Column(name = "invoice_date")
    private LocalDateTime invoiceDate;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "invoice_version")
    @Builder.Default
    private Integer invoiceVersion = 1;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "place_of_supply")
    private String placeOfSupply;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getter/Setter compatibility sync layer
    public Double getSubtotal() {
        return subtotal != null ? subtotal : amountSubtotal;
    }

    public void setSubtotal(Double subtotal) {
        this.subtotal = subtotal;
        if (subtotal != null) {
            this.amountSubtotal = subtotal;
        }
    }

    public Double getTotal() {
        return total != null ? total : amountTotal;
    }

    public void setTotal(Double total) {
        this.total = total;
        if (total != null) {
            this.amountTotal = total;
        }
    }

    public String getPdfPath() {
        return pdfPath != null ? pdfPath : pdfUrl;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
        this.pdfUrl = pdfPath;
    }
}
