package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MetaCapiServiceTest {

    @Mock private PixelConfigRepository pixelRepo;
    @Mock private LeadRepository leadRepo;

    private MetaCapiService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MetaCapiService(pixelRepo, leadRepo);
    }

    @Test
    public void testSendServerEvent_WithoutActivePixelConfigReturnsFalse() {
        String leadId = "lead-123";
        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setAccountId("acc-456");
        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));

        // Mock empty config
        when(pixelRepo.findByWorkspaceId("acc-456")).thenReturn(Optional.empty());

        boolean result = service.sendServerEvent(leadId, "Lead", 10.0, "INR");
        assertFalse(result);
    }
}
