package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.LeadPipelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeadPipelineEventRepository extends JpaRepository<LeadPipelineEvent, String> {
    List<LeadPipelineEvent> findByLeadIdOrderByCreatedAtAsc(String leadId);
    List<LeadPipelineEvent> findTop100ByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    long countByWorkspaceIdAndEventType(String workspaceId, String eventType);
    long countByWorkspaceIdAndStatus(String workspaceId, String status);
    long countByWorkspaceId(String workspaceId);
}
