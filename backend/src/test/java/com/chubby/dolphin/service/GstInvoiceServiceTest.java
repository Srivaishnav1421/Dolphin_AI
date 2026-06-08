package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Invoice;
import com.chubby.dolphin.entity.InvoiceSequence;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.InvoiceRepository;
import com.chubby.dolphin.repository.InvoiceSequenceRepository;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GstInvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepo;
    @Mock private InvoiceSequenceRepository sequenceRepo;
    @Mock private WorkspaceConfigRepository configRepo;

    private GstInvoiceService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GstInvoiceService(invoiceRepo, sequenceRepo, configRepo);
        
        // Inject corporate properties using ReflectionTestUtils to match production values
        ReflectionTestUtils.setField(service, "corporateStateCode", "MH");
        ReflectionTestUtils.setField(service, "corporateLegalName", "DolphinAI Private Limited");
        ReflectionTestUtils.setField(service, "corporateGstin", "27AAACC4111D1Z5");
        ReflectionTestUtils.setField(service, "corporateAddress", "102 Alpha Towers, Mumbai, MH");
    }

    @Test
    public void testGetActiveFinancialYearKey_CalculatesCorrectFY() {
        LocalDate aprilFirst = LocalDate.of(2026, 4, 1);
        LocalDate marchThirtyFirst = LocalDate.of(2026, 3, 31);

        String fyApril = service.getActiveFinancialYearKey(aprilFirst);
        String fyMarch = service.getActiveFinancialYearKey(marchThirtyFirst);

        assertEquals("2026-2027", fyApril);
        assertEquals("2025-2026", fyMarch);
    }

    @Test
    public void testGenerateNextInvoiceNumber_PessimisticWriteSequentialFormat() {
        LocalDate date = LocalDate.of(2026, 5, 30);
        InvoiceSequence mockSeq = new InvoiceSequence("2026-2027", 14);

        when(sequenceRepo.findAndLockByYearKey("2026-2027")).thenReturn(Optional.of(mockSeq));

        String invoiceNum = service.generateNextInvoiceNumber(date);

        assertEquals("CD-2627-0015", invoiceNum);
        assertEquals(15, mockSeq.getLastNumber());
        verify(sequenceRepo, times(1)).save(mockSeq);
    }

    @Test
    public void testCreateInvoice_CalculatesCgstSgstWhenStateMatches() {
        String workspaceId = "ws-999";
        WorkspaceConfig config = new WorkspaceConfig();
        config.setWorkspaceId(workspaceId);
        config.setLegalName("Local Client MH");
        config.setStateCode("MH"); // Matches corporateStateCode = MH
        config.setGstin("27BBBBB1111B1Z0");

        InvoiceSequence mockSeq = new InvoiceSequence("2026-2027", 0);

        when(configRepo.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(config));
        when(sequenceRepo.findAndLockByYearKey(anyString())).thenReturn(Optional.of(mockSeq));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = service.createInvoice(workspaceId, "tx-abc", 1000.0);

        assertNotNull(result);
        assertEquals("CGST_SGST", result.getGstType());
        assertEquals(180.0, result.getGstAmount());
        assertEquals(1180.0, result.getTotal());
        assertNotNull(result.getPdfPath());

        // Cleanup the created PDF test artifact file
        File pdfFile = new File(result.getPdfPath());
        if (pdfFile.exists()) {
            pdfFile.delete();
        }
    }

    @Test
    public void testCreateInvoice_CalculatesIgstWhenStateDiffers() {
        String workspaceId = "ws-999";
        WorkspaceConfig config = new WorkspaceConfig();
        config.setWorkspaceId(workspaceId);
        config.setLegalName("Out of State Client DL");
        config.setStateCode("DL"); // Differs from corporateStateCode = MH
        config.setGstin("07BBBBB1111B1Z0");

        InvoiceSequence mockSeq = new InvoiceSequence("2026-2027", 0);

        when(configRepo.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(config));
        when(sequenceRepo.findAndLockByYearKey(anyString())).thenReturn(Optional.of(mockSeq));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        Invoice result = service.createInvoice(workspaceId, "tx-abc", 1000.0);

        assertNotNull(result);
        assertEquals("IGST", result.getGstType());
        assertEquals(180.0, result.getGstAmount());
        assertEquals(1180.0, result.getTotal());
        assertNotNull(result.getPdfPath());

        // Cleanup the created PDF test artifact file
        File pdfFile = new File(result.getPdfPath());
        if (pdfFile.exists()) {
            pdfFile.delete();
        }
    }
}
