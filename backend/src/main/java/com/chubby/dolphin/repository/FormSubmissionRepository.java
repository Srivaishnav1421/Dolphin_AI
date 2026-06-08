package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, String> {
    List<FormSubmission> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    long countByWorkspaceIdAndCampaignId(String workspaceId, String campaignId);
    long countByWorkspaceIdAndLandingPageId(String workspaceId, String landingPageId);
    long countByWorkspaceIdAndFormId(String workspaceId, String formId);
}
