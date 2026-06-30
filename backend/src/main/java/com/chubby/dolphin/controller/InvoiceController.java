package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.Invoice;
import com.chubby.dolphin.repository.InvoiceRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.GstInvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@Slf4j
public class InvoiceController {

    private final GstInvoiceService invoiceService;
    private final InvoiceRepository invoiceRepo;
    private final SecurityUtils sec;
    private final AccessControlService access;

    public InvoiceController(GstInvoiceService invoiceService,
                             InvoiceRepository invoiceRepo,
                             SecurityUtils sec,
                             AccessControlService access) {
        this.invoiceService = invoiceService;
        this.invoiceRepo = invoiceRepo;
        this.sec = sec;
        this.access = access;
    }

    /**
     * List all billing invoices for the active workspace.
     */
    @GetMapping
    public ResponseEntity<List<Invoice>> getInvoices() {
        access.requireWorkspacePermission(Permission.INVOICE_READ);
        return ResponseEntity.ok(invoiceRepo.findByWorkspaceId(sec.currentWorkspaceId()));
    }

    /**
     * Explicit trigger to generate an active GST Invoice for testing and auditing.
     */
    @PostMapping("/generate")
    public ResponseEntity<Invoice> generateInvoice(@RequestBody Map<String, Object> body) {
        access.requireWorkspacePermission(Permission.BILLING_MANAGE);
        Double amount = Double.valueOf(body.getOrDefault("amount", 999.0).toString());
        String transactionId = (String) body.get("transaction_id");
        
        Invoice invoice = invoiceService.createInvoice(sec.currentWorkspaceId(), transactionId, amount);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Download or view the dynamic commercial PDF invoice in the browser.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.INVOICE_READ);
        Invoice invoice = invoiceRepo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId()).orElse(null);
        if (invoice == null || invoice.getPdfPath() == null) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(invoice.getPdfPath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
