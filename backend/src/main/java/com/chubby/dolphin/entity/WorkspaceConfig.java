package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import com.chubby.dolphin.security.EncryptionConverter;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_configs")
public class WorkspaceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String workspaceId;

    private String whatsappPhoneId;

    @Column(length = 1000)
    @Convert(converter = EncryptionConverter.class)
    private String whatsappToken;

    @Column(length = 1000)
    @Convert(converter = EncryptionConverter.class)
    private String whatsappVerifyToken;

    private String whatsappVerifyTokenHash;

    private Boolean whatsappWebhookEnabled = false;

    private Double minRoasThreshold = 2.0;
    private Double maxSpendLimit = 10000.0;
    private Double targetCpl = 500.0;
    private Boolean autoOptimizationEnabled = false;

    private String brandName;
    private String brandLogoUrl;
    private String billingEmail;
    private String gstin;
    private String legalName;

    @Column(columnDefinition = "TEXT")
    private String billingAddress;

    private String stateCode;
    private String panNumber;

    @Column(columnDefinition = "TEXT")
    private String bankDetails;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WorkspaceConfig() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getWhatsappPhoneId() { return whatsappPhoneId; }
    public void setWhatsappPhoneId(String whatsappPhoneId) { this.whatsappPhoneId = whatsappPhoneId; }
    public String getWhatsappToken() { return whatsappToken; }
    public void setWhatsappToken(String whatsappToken) { this.whatsappToken = whatsappToken; }
    public String getWhatsappVerifyToken() { return whatsappVerifyToken; }
    public void setWhatsappVerifyToken(String whatsappVerifyToken) { this.whatsappVerifyToken = whatsappVerifyToken; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getBrandLogoUrl() { return brandLogoUrl; }
    public void setBrandLogoUrl(String brandLogoUrl) { this.brandLogoUrl = brandLogoUrl; }
    public String getBillingEmail() { return billingEmail; }
    public void setBillingEmail(String billingEmail) { this.billingEmail = billingEmail; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
    public String getBankDetails() { return bankDetails; }
    public void setBankDetails(String bankDetails) { this.bankDetails = bankDetails; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getWhatsappVerifyTokenHash() { return whatsappVerifyTokenHash; }
    public void setWhatsappVerifyTokenHash(String whatsappVerifyTokenHash) { this.whatsappVerifyTokenHash = whatsappVerifyTokenHash; }

    public Boolean getWhatsappWebhookEnabled() { return whatsappWebhookEnabled; }
    public void setWhatsappWebhookEnabled(Boolean whatsappWebhookEnabled) { this.whatsappWebhookEnabled = whatsappWebhookEnabled; }

    public Double getMinRoasThreshold() { return minRoasThreshold; }
    public void setMinRoasThreshold(Double minRoasThreshold) { this.minRoasThreshold = minRoasThreshold; }

    public Double getMaxSpendLimit() { return maxSpendLimit; }
    public void setMaxSpendLimit(Double maxSpendLimit) { this.maxSpendLimit = maxSpendLimit; }

    public Double getTargetCpl() { return targetCpl; }
    public void setTargetCpl(Double targetCpl) { this.targetCpl = targetCpl; }

    public Boolean getAutoOptimizationEnabled() { return autoOptimizationEnabled; }
    public void setAutoOptimizationEnabled(Boolean autoOptimizationEnabled) { this.autoOptimizationEnabled = autoOptimizationEnabled; }
}
