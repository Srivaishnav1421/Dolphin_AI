package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.PixelConfig;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.PixelConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@Transactional
public class MetaCapiService {

    private final PixelConfigRepository pixelRepo;
    private final LeadRepository leadRepo;
    private final WebClient webClient;

    public MetaCapiService(PixelConfigRepository pixelRepo, LeadRepository leadRepo) {
        this.pixelRepo = pixelRepo;
        this.leadRepo = leadRepo;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Dispatches a standard server-side event (e.g. Lead, Purchase) to Meta CAPI.
     */
    public boolean sendServerEvent(String leadId, String eventName, Double value, String currency) {
        log.info("📡 Dispatching Meta Conversions API (CAPI) event '{}' for lead: {}", eventName, leadId);

        Lead lead = leadRepo.findById(leadId).orElse(null);
        if (lead == null) {
            log.warn("Lead {} not found, aborting CAPI event dispatch.", leadId);
            return false;
        }

        String workspaceId = lead.getWorkspaceId() != null ? lead.getWorkspaceId() : lead.getAccountId();
        PixelConfig config = pixelRepo.findByWorkspaceId(workspaceId).orElse(null);

        if (config == null || !Boolean.TRUE.equals(config.getIsActive())) {
            log.info("Meta Conversions API is not active or configured for workspace: {}", workspaceId);
            return false;
        }

        try {
            // Hash personal identifiable details via SHA-256
            Map<String, Object> userData = new HashMap<>();
            if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
                userData.put("em", List.of(hashSha256(lead.getEmail().trim().toLowerCase())));
            }
            if (lead.getPhone() != null && !lead.getPhone().isBlank()) {
                // Ensure international standard format without plus sign or leading zeros
                String rawPhone = lead.getPhone().replaceAll("[^0-9]", "");
                userData.put("ph", List.of(hashSha256(rawPhone)));
            }

            if (lead.getIpAddress() != null && !lead.getIpAddress().isBlank()) {
                userData.put("client_ip_address", lead.getIpAddress());
            }
            if (lead.getUserAgent() != null && !lead.getUserAgent().isBlank()) {
                userData.put("client_user_agent", lead.getUserAgent());
            }

            Map<String, Object> customData = new HashMap<>();
            if (value != null) {
                customData.put("value", value);
            }
            if (currency != null) {
                customData.put("currency", currency);
            }

            Map<String, Object> event = new HashMap<>();
            event.put("event_name", eventName);
            event.put("event_time", Instant.now().getEpochSecond());
            event.put("action_source", "website");
            event.put("user_data", userData);
            if (!customData.isEmpty()) {
                event.put("custom_data", customData);
            }
            if (lead.getSourceUrl() != null && !lead.getSourceUrl().isBlank()) {
                event.put("event_source_url", lead.getSourceUrl());
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("data", List.of(event));

            // Support Meta CAPI test sandbox
            if (config.getTestEventCode() != null && !config.getTestEventCode().isBlank()) {
                payload.put("test_event_code", config.getTestEventCode());
            }

            String url = "https://graph.facebook.com/v21.0/" + config.getPixelId() + "/events";

            webClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(config.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            lead.setCapiSent(true);
            leadRepo.save(lead);
            log.info("✅ Meta CAPI event '{}' successfully dispatched for lead {}", eventName, leadId);
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to dispatch Meta CAPI event: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Standard SHA-256 hashing utility as mandated by Meta privacy compliance guidelines.
     */
    private String hashSha256(String rawInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawInput.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 digest engine unavailable", e);
        }
    }
}
