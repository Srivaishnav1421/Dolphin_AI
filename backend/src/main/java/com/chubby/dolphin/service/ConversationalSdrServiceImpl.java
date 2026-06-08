package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.LeadChatMessage;
import com.chubby.dolphin.repository.LeadChatMessageRepository;
import com.chubby.dolphin.repository.LeadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ConversationalSdrServiceImpl implements ConversationalSdrService {

    private final LeadRepository leadRepo;
    private final LeadChatMessageRepository chatRepo;
    private final BusinessLlmFacadeService llmRouter;

    public ConversationalSdrServiceImpl(LeadRepository leadRepo,
                                         LeadChatMessageRepository chatRepo,
                                         BusinessLlmFacadeService llmRouter) {
        this.leadRepo = leadRepo;
        this.chatRepo = chatRepo;
        this.llmRouter = llmRouter;
    }

    @Override
    @Transactional
    public LeadChatMessage receiveMessage(String leadId, String messageContent) {
        log.info("💬 SDR Bot received message from lead [Id: {}]: {}", leadId, messageContent);

        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead not found with ID: " + leadId));

        // Resolve conversationId and threadId from existing history if possible
        List<LeadChatMessage> historyBefore = chatRepo.findByLeadIdOrderByCreatedAtAsc(leadId);
        String conversationId;
        String threadId;
        if (historyBefore != null && !historyBefore.isEmpty()) {
            LeadChatMessage lastMsg = historyBefore.get(historyBefore.size() - 1);
            conversationId = lastMsg.getConversationId();
            threadId = lastMsg.getThreadId();
        } else {
            conversationId = java.util.UUID.randomUUID().toString();
            threadId = "thread_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }

        // 1. Save incoming message
        LeadChatMessage incoming = new LeadChatMessage();
        incoming.setLeadId(leadId);
        incoming.setWorkspaceId(lead.getWorkspaceId());
        incoming.setConversationId(conversationId);
        incoming.setThreadId(threadId);
        incoming.setSender("LEAD");
        incoming.setMessage(messageContent);
        incoming.setCreatedAt(LocalDateTime.now());
        chatRepo.save(incoming);

        // 2. Fetch entire conversation history for contextual memory
        List<LeadChatMessage> history = chatRepo.findByLeadIdOrderByCreatedAtAsc(leadId);

        // 3. Compile context prompt
        StringBuilder chatLog = new StringBuilder();
        for (LeadChatMessage msg : history) {
            chatLog.append(String.format("[%s]: %s\n", msg.getSender(), msg.getMessage()));
        }

        String prompt = String.format("""
            You are a professional, helpful, and highly persuasive AI Sales Development Representative (SDR) agent for DolphinAI marketing.
            Your ultimate objective is to converse with the lead, qualify their buying signals (budget, urgency, requirements), and politely direct them to book a quick 10-minute demo session.
            
            Lead Profile Info:
            - Name: %s
            - Traffic Source: %s
            - Original Inbound Message: %s
            
            Current Chronological Back-and-Forth Chat History:
            \"\"\"
            %s
            \"\"\"
            
            Instructions:
            1. Formulate a short, natural, warm, and highly engaging response (maximum 3 sentences).
            2. Never use generic placeholder links like '[booking-link]'. If mentioning booking, simply offer to share the scheduling link or ask what time works best for them next week.
            3. Keep the tone completely human, crisp, and executive. Do not add signature blocks or email layouts. Respond only with the message itself.
            """, lead.getName(), lead.getSource(), lead.getMessage(), chatLog.toString());

        BusinessLlmFacadeService.LlmResponse response = llmRouter.ask(prompt);
        String botReplyText = response.text().trim();

        // 4. Save and return bot response
        LeadChatMessage botResponse = new LeadChatMessage();
        botResponse.setLeadId(leadId);
        botResponse.setWorkspaceId(lead.getWorkspaceId());
        botResponse.setConversationId(conversationId);
        botResponse.setThreadId(threadId);
        botResponse.setSender("SDR_BOT");
        botResponse.setMessage(botReplyText);
        botResponse.setCreatedAt(LocalDateTime.now());
        chatRepo.save(botResponse);

        // 5. Autonomously score/update lead intent signals based on message keywords
        updateLeadIntentSignals(lead, messageContent);

        return botResponse;
    }

    private void updateLeadIntentSignals(Lead lead, String text) {
        String cleanText = text.toLowerCase();
        boolean signalsChanged = false;

        if (cleanText.contains("price") || cleanText.contains("cost") || cleanText.contains("budget") || cleanText.contains("how much")) {
            lead.setBudgetSignal("ASKED_PRICING");
            signalsChanged = true;
        }
        if (cleanText.contains("now") || cleanText.contains("urgent") || cleanText.contains("asap") || cleanText.contains("timeline")) {
            lead.setTimelineSignal("HIGH_URGENCY");
            signalsChanged = true;
        }
        if (cleanText.contains("book") || cleanText.contains("call") || cleanText.contains("meeting") || cleanText.contains("yes") || cleanText.contains("sure")) {
            lead.setIntentSignal("READY_TO_BOOK");
            lead.setStatus("HOT");
            lead.setScore(0.95);
            signalsChanged = true;
        }

        if (signalsChanged) {
            leadRepo.save(lead);
            log.info("🎯 Lead CRM signals updated autonomously based on conversation triggers.");
        }
    }

    @Override
    public List<LeadChatMessage> getConversationHistory(String leadId) {
        return chatRepo.findByLeadIdOrderByCreatedAtAsc(leadId);
    }
}
