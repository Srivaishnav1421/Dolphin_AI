package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.PixelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PixelConfigRepository extends JpaRepository<PixelConfig, String> {
    Optional<PixelConfig> findByWorkspaceId(String workspaceId);
    Optional<PixelConfig> findByIdAndWorkspaceId(String id, String workspaceId);
}
