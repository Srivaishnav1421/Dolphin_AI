package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.LeadChatMessage;
import com.chubby.dolphin.repository.LeadChatMessageRepository;
import com.chubby.dolphin.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConversationalSdrServiceTest {

    @Mock
    private LeadRepository leadRepo;

    @Mock
    private LeadChatMessageRepository chatRepo;

    @Mock
    private BusinessLlmFacadeService llmRouter;

    private ConversationalSdrServiceImpl sdrService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sdrService = new ConversationalSdrServiceImpl(leadRepo, chatRepo, llmRouter);
    }

    @Test
    public void testReceiveMessageSuccessfullyAndScoresSignals() {
        String leadId = "lead-999";
        String messageContent = "Hi, I would love to book a call with your sales team asap!";

        Lead mockLead = new Lead();
        mockLead.setId(leadId);
        mockLead.setName("Aravind");
        mockLead.setSource("WHATSAPP");
        mockLead.setMessage("Inbound WhatsApp query");
        mockLead.setStatus("WARM");
        mockLead.setScore(0.5);

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(mockLead));
        when(chatRepo.findByLeadIdOrderByCreatedAtAsc(leadId)).thenReturn(new ArrayList<>());
        
        BusinessLlmFacadeService.LlmResponse mockLlmResponse = new BusinessLlmFacadeService.LlmResponse(
            "Hello Aravind! I would be delighted to schedule a 10-minute demo. Let me know what time works best.",
            "GEMINI",
            "gemini-1.5-pro"
        );
        when(llmRouter.ask(anyString())).thenReturn(mockLlmResponse);

        when(chatRepo.save(any(LeadChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leadRepo.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        LeadChatMessage reply = sdrService.receiveMessage(leadId, messageContent);

        // Assert
        assertNotNull(reply);
        assertEquals("SDR_BOT", reply.getSender());
        assertEquals("Hello Aravind! I would be delighted to schedule a 10-minute demo. Let me know what time works best.", reply.getMessage());

        // Verify intent scoring trigger keywords
        assertEquals("READY_TO_BOOK", mockLead.getIntentSignal());
        assertEquals("HIGH_URGENCY", mockLead.getTimelineSignal());
        assertEquals("HOT", mockLead.getStatus());
        assertEquals(0.95, mockLead.getScore());

        verify(chatRepo, times(2)).save(any(LeadChatMessage.class));
        verify(leadRepo, times(1)).save(any(Lead.class));
    }
}
