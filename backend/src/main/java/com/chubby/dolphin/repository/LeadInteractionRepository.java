package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.LeadInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeadInteractionRepository extends JpaRepository<LeadInteraction, String> {
    List<LeadInteraction> findByLeadIdOrderByCreatedAtAsc(String leadId);
    List<LeadInteraction> findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(String leadId, String workspaceId);
    List<LeadInteraction> findByWorkspaceId(String workspaceId);
}
