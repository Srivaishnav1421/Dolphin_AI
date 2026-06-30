package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import com.chubby.dolphin.security.EncryptionConverter;
import java.time.LocalDateTime;

@Entity
@Table(name = "integration_settings")
public class IntegrationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String workspaceId;

    @Column(nullable = false)
    private String providerId;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptionConverter.class)
    private String credentialsJson; // JSON representation of secrets/keys, encrypted at rest

    @Column(nullable = false, length = 30)
    private String validationStatus = "PENDING_VALIDATION";

    private LocalDateTime lastValidatedAt;

    @Column(length = 1000)
    private String lastValidationMessage;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public IntegrationSetting() {}

    public IntegrationSetting(String workspaceId, String providerId, String credentialsJson) {
        this.workspaceId = workspaceId;
        this.providerId = providerId;
        this.credentialsJson = credentialsJson;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.validationStatus == null || this.validationStatus.isBlank()) {
            this.validationStatus = "PENDING_VALIDATION";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getCredentialsJson() { return credentialsJson; }
    public void setCredentialsJson(String credentialsJson) { this.credentialsJson = credentialsJson; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public LocalDateTime getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(LocalDateTime lastValidatedAt) { this.lastValidatedAt = lastValidatedAt; }

    public String getLastValidationMessage() { return lastValidationMessage; }
    public void setLastValidationMessage(String lastValidationMessage) { this.lastValidationMessage = lastValidationMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
