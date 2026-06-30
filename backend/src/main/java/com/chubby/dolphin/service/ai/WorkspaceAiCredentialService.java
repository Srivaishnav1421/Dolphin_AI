package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.entity.IntegrationSetting;
import com.chubby.dolphin.repository.IntegrationSettingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class WorkspaceAiCredentialService {

    private final IntegrationSettingRepository integrationSettingRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceAiCredentialService(IntegrationSettingRepository integrationSettingRepository,
                                        ObjectMapper objectMapper) {
        this.integrationSettingRepository = integrationSettingRepository;
        this.objectMapper = objectMapper;
    }

    public boolean hasCredentials(String workspaceId, String providerId) {
        if (workspaceId == null || workspaceId.isBlank() || providerId == null || providerId.isBlank()) {
            return false;
        }
        return integrationSettingRepository.existsByWorkspaceIdAndProviderId(workspaceId, providerId.toLowerCase());
    }

    public Optional<String> apiKey(String workspaceId, String providerId) {
        return credentials(workspaceId, providerId)
                .map(credentials -> {
                    String value = credentials.get("api_key");
                    if (value == null || value.isBlank()) {
                        value = credentials.get("apiKey");
                    }
                    return value;
                })
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim);
    }

    public Optional<Map<String, String>> credentials(String workspaceId, String providerId) {
        if (workspaceId == null || workspaceId.isBlank() || providerId == null || providerId.isBlank()) {
            return Optional.empty();
        }
        Optional<IntegrationSetting> setting = integrationSettingRepository.findByWorkspaceIdAndProviderId(workspaceId, providerId.toLowerCase());
        if (setting.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(setting.get().getCredentialsJson(), new TypeReference<Map<String, String>>() {}));
        } catch (Exception e) {
            log.warn("Could not parse {} credentials for workspace {}: {}", providerId, workspaceId, e.getMessage());
            return Optional.empty();
        }
    }
}
