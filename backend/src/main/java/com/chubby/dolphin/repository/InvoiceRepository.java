package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    List<Invoice> findByWorkspaceId(String workspaceId);
    Optional<Invoice> findByIdAndWorkspaceId(String id, String workspaceId);
    Optional<Invoice> findByInvoiceNumberAndWorkspaceId(String invoiceNumber, String workspaceId);
}
