package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.MarketingForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketingFormRepository extends JpaRepository<MarketingForm, String> {
    List<MarketingForm> findByWorkspaceIdOrderByUpdatedAtDesc(String workspaceId);
    Optional<MarketingForm> findByIdAndWorkspaceId(String id, String workspaceId);
    Optional<MarketingForm> findByWorkspaceIdAndSlug(String workspaceId, String slug);
}
