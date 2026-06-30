package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.IntegrationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationSettingRepository extends JpaRepository<IntegrationSetting, String> {
    List<IntegrationSetting> findAllByWorkspaceId(String workspaceId);
    Optional<IntegrationSetting> findByWorkspaceIdAndProviderId(String workspaceId, String providerId);
    boolean existsByWorkspaceIdAndProviderId(String workspaceId, String providerId);
    void deleteByWorkspaceIdAndProviderId(String workspaceId, String providerId);
}
