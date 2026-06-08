package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WhatsAppMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, String> {
    List<WhatsAppMessage> findByWorkspaceId(String workspaceId);
    List<WhatsAppMessage> findByLeadId(String leadId);
    Optional<WhatsAppMessage> findByMessageId(String messageId);
}
