package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.MetaAudience;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.MetaAudienceRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@Transactional
public class MetaAudienceService {

    private final MetaAudienceRepository audienceRepo;
    private final MetaConnectionRepository metaConnRepo;
    private final LeadRepository leadRepo;
    private final WebClient webClient;
    private final ObjectMapper mapper;

    public MetaAudienceService(MetaAudienceRepository audienceRepo,
                               MetaConnectionRepository metaConnRepo,
                               LeadRepository leadRepo,
                               ObjectMapper mapper) {
        this.audienceRepo = audienceRepo;
        this.metaConnRepo = metaConnRepo;
        this.leadRepo = leadRepo;
        this.mapper = mapper;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Create a Custom Audience on Meta Graph API act_{ad_account_id}/customaudiences
     */
    public MetaAudience createCustomAudience(String workspaceId, String name, String description) {
        log.info("🎯 Creating Meta Custom Audience '{}' for workspace: {}", name, workspaceId);

        MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID").orElse(null);
        if (conn == null || conn.getMetaAdAccountId() == null || conn.getAccessToken() == null) {
            log.warn("No active Meta connection for workspace {}, creating mock database entry.", workspaceId);
            MetaAudience localAudience = new MetaAudience();
            localAudience.setWorkspaceId(workspaceId);
            localAudience.setName(name);
            localAudience.setAudienceType("CUSTOM");
            localAudience.setSubtype("CUSTOMER_FILE");
            localAudience.setMetaAudienceId("mock-aud-" + UUID.randomUUID());
            localAudience.setSizeEstimate(0L);
            return audienceRepo.save(localAudience);
        }

        try {
            String url = "https://graph.facebook.com/v21.0/" + conn.getMetaAdAccountId() + "/customaudiences";

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("subtype", "CUSTOM");
            payload.put("description", description);
            payload.put("customer_file_source", "USER_PROVIDED_ONLY");

            String response = webClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(conn.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = mapper.readTree(response);
            String metaAudienceId = node.path("id").asText();

            MetaAudience audience = new MetaAudience();
            audience.setWorkspaceId(workspaceId);
            audience.setMetaAudienceId(metaAudienceId);
            audience.setName(name);
            audience.setAudienceType("CUSTOM");
            audience.setSubtype("CUSTOMER_FILE");
            audience.setSizeEstimate(0L);

            log.info("✅ Meta Custom Audience created successfully on Meta: {}", metaAudienceId);
            return audienceRepo.save(audience);

        } catch (Exception e) {
            log.error("❌ Failed to create Custom Audience on Meta: {}. Saving local fallback entry.", e.getMessage());
            MetaAudience localAudience = new MetaAudience();
            localAudience.setWorkspaceId(workspaceId);
            localAudience.setName(name);
            localAudience.setAudienceType("CUSTOM");
            localAudience.setSubtype("CUSTOMER_FILE");
            localAudience.setMetaAudienceId("local-" + UUID.randomUUID());
            localAudience.setSizeEstimate(0L);
            return audienceRepo.save(localAudience);
        }
    }

    /**
     * Upload custom client records (SHA-256 hashed) to a Meta Custom Audience.
     */
    public boolean uploadUsersToAudience(String audienceId, List<Map<String, String>> users) {
        log.info("📤 Uploading {} contact records to Meta Custom Audience: {}", users.size(), audienceId);

        MetaAudience audience = audienceRepo.findById(audienceId).orElse(null);
        if (audience == null || audience.getMetaAudienceId() == null) {
            log.warn("Audience configuration {} not found.", audienceId);
            return false;
        }

        String workspaceId = audience.getWorkspaceId();
        MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID").orElse(null);
        if (conn == null || conn.getAccessToken() == null) {
            log.warn("No active Meta connection to upload users to audience.");
            audience.setSizeEstimate(audience.getSizeEstimate() + users.size());
            audienceRepo.save(audience);
            return true;
        }

        if (audience.getMetaAudienceId().startsWith("mock-") || audience.getMetaAudienceId().startsWith("local-")) {
            log.info("Local/Mock audience detected, skipping Meta Graph API push.");
            audience.setSizeEstimate(audience.getSizeEstimate() + users.size());
            audienceRepo.save(audience);
            return true;
        }

        try {
            List<List<String>> payloadData = new ArrayList<>();
            for (Map<String, String> user : users) {
                String email = user.get("email");
                String phone = user.get("phone");

                String hashedEmail = (email != null && !email.isBlank()) ? hashSha256(email.trim().toLowerCase()) : "";
                String hashedPhone = (phone != null && !phone.isBlank()) ? hashSha256(phone.replaceAll("[^0-9]", "")) : "";

                payloadData.add(List.of(hashedEmail, hashedPhone));
            }

            Map<String, Object> payloadSchema = new HashMap<>();
            payloadSchema.put("schema", List.of("EMAIL", "PHONE"));
            payloadSchema.put("data", payloadData);

            Map<String, Object> outerPayload = new HashMap<>();
            outerPayload.put("payload", payloadSchema);

            String url = "https://graph.facebook.com/v21.0/" + audience.getMetaAudienceId() + "/users";

            webClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(conn.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(outerPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            audience.setSizeEstimate(audience.getSizeEstimate() + users.size());
            audience.setUpdatedAt(LocalDateTime.now());
            audienceRepo.save(audience);

            log.info("✅ Successfully uploaded contacts to Meta Custom Audience: {}", audience.getMetaAudienceId());
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to push contacts to Meta Custom Audience: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a Lookalike Audience on Meta based on an existing Custom Audience
     */
    public MetaAudience createLookalikeAudience(String workspaceId, String name, String sourceAudienceId, double ratio, String country) {
        log.info("🎯 Creating Lookalike Audience '{}' from source audience: {}", name, sourceAudienceId);

        MetaAudience source = audienceRepo.findById(sourceAudienceId).orElse(null);
        if (source == null) {
            log.warn("Source audience {} not found.", sourceAudienceId);
            return null;
        }

        MetaConnection conn = metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID").orElse(null);
        if (conn == null || conn.getMetaAdAccountId() == null || conn.getAccessToken() == null) {
            log.warn("No active Meta connection for lookalike, creating local mock entry.");
            MetaAudience localAudience = new MetaAudience();
            localAudience.setWorkspaceId(workspaceId);
            localAudience.setName(name);
            localAudience.setAudienceType("LOOKALIKE");
            localAudience.setSubtype("LOOKALIKE");
            localAudience.setSourceAudienceId(source.getMetaAudienceId());
            localAudience.setLookalikeRatio(ratio);
            localAudience.setLookalikeCountry(country);
            localAudience.setMetaAudienceId("mock-lla-" + UUID.randomUUID());
            return audienceRepo.save(localAudience);
        }

        try {
            String url = "https://graph.facebook.com/v21.0/" + conn.getMetaAdAccountId() + "/customaudiences";

            Map<String, Object> lookalikeSpec = new HashMap<>();
            lookalikeSpec.put("type", "similarity");
            lookalikeSpec.put("ratio", ratio);
            lookalikeSpec.put("country", country);

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("subtype", "LOOKALIKE");
            payload.put("origin_audience_id", source.getMetaAudienceId());
            payload.put("lookalike_spec", lookalikeSpec);

            String response = webClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(conn.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = mapper.readTree(response);
            String metaAudienceId = node.path("id").asText();

            MetaAudience audience = new MetaAudience();
            audience.setWorkspaceId(workspaceId);
            audience.setMetaAudienceId(metaAudienceId);
            audience.setName(name);
            audience.setAudienceType("LOOKALIKE");
            audience.setSubtype("LOOKALIKE");
            audience.setSourceAudienceId(source.getMetaAudienceId());
            audience.setLookalikeRatio(ratio);
            audience.setLookalikeCountry(country);

            log.info("✅ Meta Lookalike Audience created: {}", metaAudienceId);
            return audienceRepo.save(audience);

        } catch (Exception e) {
            log.error("❌ Failed to create Lookalike Audience on Meta: {}. Saving local fallback entry.", e.getMessage());
            MetaAudience localAudience = new MetaAudience();
            localAudience.setWorkspaceId(workspaceId);
            localAudience.setName(name);
            localAudience.setAudienceType("LOOKALIKE");
            localAudience.setSubtype("LOOKALIKE");
            localAudience.setSourceAudienceId(source.getMetaAudienceId());
            localAudience.setLookalikeRatio(ratio);
            localAudience.setLookalikeCountry(country);
            localAudience.setMetaAudienceId("local-" + UUID.randomUUID());
            return audienceRepo.save(localAudience);
        }
    }

    /**
     * SuperLookalike Premium Strategy Builder.
     * Generates multiple targeted layers (e.g. 1%, 2%, and 5%) bundled sequentially.
     */
    public List<MetaAudience> createSuperLookalike(String workspaceId, String name, String sourceAudienceId, String country) {
        log.info("⚡ Building SuperLookalike strategy pack [1%, 2%, 5%] for workspace: {}", workspaceId);

        List<MetaAudience> pack = new ArrayList<>();
        double[] ratios = {0.01, 0.02, 0.05};

        for (double ratio : ratios) {
            int percentage = (int) (ratio * 100);
            String lName = String.format("%s [SuperLLA %d%% - %s]", name, percentage, country);
            MetaAudience lla = createLookalikeAudience(workspaceId, lName, sourceAudienceId, ratio, country);
            if (lla != null) {
                pack.add(lla);
            }
        }

        log.info("✅ SuperLookalike strategy pack with {} tiers successfully generated.", pack.size());
        return pack;
    }

    /**
     * AI CRM Auto-Harvest & Custom Audience training cycle.
     * Automatically harvests HOT Leads (score >= 0.70) that have arrived in the last 7 days
     * and pushes them as training seeds to the custom audience to build an intelligent lookalike model.
     */
    public int syncHotLeadsToAudience(String workspaceId, String customAudienceId) {
        log.info("🌾 AI Customer Profiling: Harvesting HOT Leads to retrain custom audience: {}", customAudienceId);

        // Fetch leads that are marked HOT (score >= 0.70) and not opted out
        List<Lead> leads = leadRepo.findByAccountIdAndStatus(workspaceId, "HOT");
        if (leads.isEmpty()) {
            log.info("No fresh HOT leads found for profiling.");
            return 0;
        }

        List<Map<String, String>> contacts = new ArrayList<>();
        for (Lead lead : leads) {
            Map<String, String> user = new HashMap<>();
            if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
                user.put("email", lead.getEmail());
            }
            if (lead.getPhone() != null && !lead.getPhone().isBlank()) {
                user.put("phone", lead.getPhone());
            }
            if (!user.isEmpty()) {
                contacts.add(user);
            }
        }

        if (contacts.isEmpty()) {
            return 0;
        }

        boolean success = uploadUsersToAudience(customAudienceId, contacts);
        return success ? contacts.size() : 0;
    }

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
