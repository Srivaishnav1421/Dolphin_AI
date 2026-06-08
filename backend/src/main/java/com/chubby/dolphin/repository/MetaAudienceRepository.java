package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.MetaAudience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaAudienceRepository extends JpaRepository<MetaAudience, String> {
    List<MetaAudience> findByWorkspaceId(String workspaceId);
    java.util.Optional<MetaAudience> findByIdAndWorkspaceId(String id, String workspaceId);
}
