package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.LandingPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LandingPageRepository extends JpaRepository<LandingPage, String> {
    List<LandingPage> findByWorkspaceIdOrderByUpdatedAtDesc(String workspaceId);
    Optional<LandingPage> findByIdAndWorkspaceId(String id, String workspaceId);
    Optional<LandingPage> findByWorkspaceIdAndSlug(String workspaceId, String slug);
    Optional<LandingPage> findByWorkspaceIdAndSlugAndStatus(String workspaceId, String slug, String status);
}
