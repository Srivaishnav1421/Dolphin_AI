package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.LeadChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeadChatMessageRepository extends JpaRepository<LeadChatMessage, String> {
    List<LeadChatMessage> findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(String leadId, String workspaceId);
}
