package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.LeadChatMessage;
import java.util.List;

public interface ConversationalSdrService {
    /**
     * Ingests a new message from a lead, formulates an automated AI SDR response,
     * logs both messages, and returns the chatbot's response message.
     */
    LeadChatMessage receiveMessage(String leadId, String messageContent);

    /**
     * Retrieves the sequential chat history for a specific lead.
     */
    List<LeadChatMessage> getConversationHistory(String leadId);
}
