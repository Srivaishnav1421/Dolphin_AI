package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.LeadChatMessage;
import java.util.List;

public interface ConversationalSdrService {
    /**
     * Ingests a message for a lead that has already been loaded and tenant-checked
     * by the caller.
     */
    LeadChatMessage receiveMessage(Lead lead, String messageContent);

    /**
     * Retrieves tenant-scoped conversation history for a lead already verified by the caller.
     */
    List<LeadChatMessage> getConversationHistory(String leadId, String workspaceId);
}
