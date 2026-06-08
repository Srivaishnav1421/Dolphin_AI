package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.chubby.dolphin.repository.MetricSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Meta Marketing API Service — The heartbeat of the autonomous ad management system.
 *
 * Handles:
 *   - OAuth2 token exchange (short-lived → long-lived)
 *   - Campaign CRUD via Meta Marketing API v21.0
 *   - Real-time metric sync from Meta → local DB
 *   - Execute AI decisions (pause, resume, budget changes) on actual Meta campaigns
 *   - Lead form webhook ingestion
 *   - Token health monitoring and auto-refresh
 */
@Service
@Slf4j
public class MetaAdsService {

    private final WebClient graphClient;
    private final MetaConnectionRepository metaConnRepo;
    private final CampaignRepository campaignRepo;
    private final MetricSnapshotRepository metricRepo;
    private final AdCreativeRepository adCreativeRepo;
    private final ObjectMapper mapper;

    @Value("${meta.app.id}")      private String appId;
    @Value("${meta.app.secret}")  private String appSecret;
    @Value("${meta.api.version}") private String apiVersion;
    @Value("${meta.redirect-uri}") private String redirectUri;

    public MetaAdsService(MetaConnectionRepository metaConnRepo,
                          CampaignRepository campaignRepo,
                          MetricSnapshotRepository metricRepo,
                          AdCreativeRepository adCreativeRepo,
                          ObjectMapper mapper,
                          @Value("${meta.api.base-url}") String baseUrl) {
        this.metaConnRepo = metaConnRepo;
        this.campaignRepo = campaignRepo;
        this.metricRepo = metricRepo;
        this.adCreativeRepo = adCreativeRepo;
        this.mapper = mapper;
        this.graphClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  OAuth2 Flow
    // ══════════════════════════════════════════════════════════════════

    /**
     * Generate the Meta OAuth2 authorization URL.
     * User opens this in their browser to grant permissions.
     */
    public String getAuthorizationUrl(String state) {
        return "https://www.facebook.com/" + apiVersion + "/dialog/oauth?" +
               "client_id=" + appId +
               "&redirect_uri=" + redirectUri +
               "&state=" + state +
               "&scope=ads_management,ads_read,pages_show_list,leads_retrieval,pages_read_engagement" +
               "&response_type=code";
    }

    @CircuitBreaker(name = "metaGraphApi")
    @Retry(name = "metaGraphApi")
    public MetaConnection exchangeCodeForToken(String code, String accountId) {
        log.info("🔑 Exchanging OAuth code for Meta token...");

        // Step 1: Exchange code → short-lived token
        String shortTokenJson = graphClient.get()
                .uri(u -> u.path("/" + apiVersion + "/oauth/access_token")
                        .queryParam("client_id", appId)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("client_secret", appSecret)
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String shortToken = extractField(shortTokenJson, "access_token");

        // Step 2: Exchange short-lived → long-lived token
        String longTokenJson = graphClient.get()
                .uri(u -> u.path("/" + apiVersion + "/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("fb_exchange_token", shortToken)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String longToken = extractField(longTokenJson, "access_token");
        long expiresIn = Long.parseLong(extractField(longTokenJson, "expires_in"));

        // Step 3: Get user info + ad accounts
        String meJson = graphClient.get()
                .uri(u -> u.path("/" + apiVersion + "/me")
                        .queryParam("fields", "id,name")
                        .queryParam("access_token", longToken)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String metaUserId = extractField(meJson, "id");

        // Step 4: Get ad accounts
        String adAccountsJson = graphClient.get()
                .uri(u -> u.path("/" + apiVersion + "/me/adaccounts")
                        .queryParam("fields", "id,name,currency,timezone_name,account_status")
                        .queryParam("access_token", longToken)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Parse first ad account (user can connect more later)
        JsonNode adAccounts = parseJson(adAccountsJson).path("data");
        if (adAccounts.isEmpty()) {
            throw new RuntimeException("No ad accounts found for this Meta user. " +
                                       "Make sure you have a Meta Business Suite ad account.");
        }

        JsonNode firstAccount = adAccounts.get(0);
        String metaAdAccountId = firstAccount.path("id").asText();
        String adAccountName   = firstAccount.path("name").asText();
        String currency        = firstAccount.path("currency").asText("INR");
        String timezone        = firstAccount.path("timezone_name").asText("Asia/Kolkata");

        // Step 5: Fetch connected Facebook Pages (required for ad creative launches)
        String pagesJson = graphClient.get()
                .uri(u -> u.path("/" + apiVersion + "/me/accounts")
                        .queryParam("fields", "id,name,access_token")
                        .queryParam("access_token", longToken)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String metaPageId   = null;
        String metaPageName = null;
        try {
            JsonNode pages = parseJson(pagesJson).path("data");
            if (!pages.isEmpty()) {
                metaPageId   = pages.get(0).path("id").asText();
                metaPageName = pages.get(0).path("name").asText();
                log.info("📄 Facebook Page captured: {} ({})", metaPageName, metaPageId);
            } else {
                log.warn("⚠️ No Facebook Pages found for this user. launchAd() will require manual page_id.");
            }
        } catch (Exception e) {
            log.warn("Could not fetch Facebook Pages: {}", e.getMessage());
        }

        // Step 6: Save connection
        MetaConnection conn = metaConnRepo
                .findByAccountIdAndMetaAdAccountId(accountId, metaAdAccountId)
                .orElse(new MetaConnection());

        conn.setAccountId(accountId);
        conn.setMetaUserId(metaUserId);
        conn.setMetaAdAccountId(metaAdAccountId);
        conn.setAccessToken(longToken);
        conn.setTokenStatus("VALID");
        conn.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        conn.setAdAccountName(adAccountName);
        conn.setCurrency(currency);
        conn.setTimezone(timezone);
        conn.setUpdatedAt(LocalDateTime.now());
        if (metaPageId != null)   conn.setMetaPageId(metaPageId);
        if (metaPageName != null) conn.setMetaPageName(metaPageName);

        MetaConnection saved = metaConnRepo.save(conn);
        log.info("✅ Meta connected: {} ({}) | Page: {}", adAccountName, metaAdAccountId,
                 metaPageName != null ? metaPageName : "none");
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Campaign Sync (Meta → Local DB)
    // ══════════════════════════════════════════════════════════════════

    @CircuitBreaker(name = "metaGraphApi")
    @Retry(name = "metaGraphApi")
    public List<Campaign> syncCampaigns(MetaConnection conn) {
        log.info("🔄 Syncing campaigns from Meta: {}", conn.getAdAccountName());

        String json = graphClient.get()
                .uri(u -> u.path("/" + apiVersion + "/" + conn.getMetaAdAccountId() + "/campaigns")
                        .queryParam("fields", "id,name,status,objective,daily_budget,lifetime_budget," +
                                              "budget_remaining,start_time,stop_time")
                        .queryParam("limit", 100)
                        .queryParam("access_token", conn.getAccessToken())
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode data = parseJson(json).path("data");
        List<Campaign> synced = new ArrayList<>();

        for (JsonNode node : data) {
            String metaCampaignId = node.path("id").asText();
            Campaign campaign = campaignRepo.findAll().stream()
                    .filter(c -> metaCampaignId.equals(c.getMetaCampaignId()))
                    .findFirst()
                    .orElse(new Campaign());

            campaign.setAccountId(conn.getAccountId());
            campaign.setMetaCampaignId(metaCampaignId);
            campaign.setName(node.path("name").asText());
            campaign.setObjective(node.path("objective").asText());

            // Map Meta status to our status
            String metaStatus = node.path("status").asText();
            campaign.setStatus(mapMetaStatus(metaStatus));

            // Budget: Meta uses cents/paisa, convert to rupees
            if (node.has("daily_budget")) {
                campaign.setBudget(node.path("daily_budget").asDouble() / 100.0);
            } else if (node.has("lifetime_budget")) {
                campaign.setBudget(node.path("lifetime_budget").asDouble() / 100.0);
            }

            campaign.setUpdatedAt(LocalDateTime.now());
            synced.add(campaignRepo.save(campaign));
        }

        // Sync insights for each campaign
        for (Campaign c : synced) {
            if (c.getMetaCampaignId() != null) {
                syncCampaignInsights(conn, c);
            }
        }

        conn.setLastSyncAt(LocalDateTime.now());
        metaConnRepo.save(conn);

        log.info("✅ Synced {} campaigns from Meta", synced.size());
        return synced;
    }

    /**
     * Pull insights (metrics) for a specific campaign and update both
     * the Campaign entity and create a MetricSnapshot.
     */
    public void syncCampaignInsights(MetaConnection conn, Campaign campaign) {
        try {
            String json = graphClient.get()
                    .uri(u -> u.path("/" + apiVersion + "/" + campaign.getMetaCampaignId() + "/insights")
                            .queryParam("fields", "impressions,reach,clicks,ctr,cpc,spend," +
                                                  "actions,cost_per_action_type,frequency")
                            .queryParam("date_preset", "last_7d")
                            .queryParam("access_token", conn.getAccessToken())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode data = parseJson(json).path("data");
            if (data.isEmpty()) return;

            JsonNode insights = data.get(0);

            // Update campaign with latest metrics
            double spend = insights.path("spend").asDouble(0);
            double ctr   = insights.path("ctr").asDouble(0);
            double cpc   = insights.path("cpc").asDouble(0);
            long clicks  = insights.path("clicks").asLong(0);

            campaign.setSpent(spend);
            campaign.setCtr(ctr);
            campaign.setCpl(clicks > 0 ? spend / clicks : 0);

            // Calculate ROAS from actions (if purchase/lead data available)
            double revenue = extractActionValue(insights, "purchase");
            campaign.setRoas(spend > 0 ? revenue / spend : 0);
            campaign.setUpdatedAt(LocalDateTime.now());
            campaignRepo.save(campaign);

            // Save daily metric snapshot
            LocalDate today = LocalDate.now();
            MetricSnapshot snapshot = metricRepo
                    .findByCampaignIdAndSnapshotDate(campaign.getId(), today)
                    .orElse(new MetricSnapshot());

            snapshot.setAccountId(campaign.getAccountId());
            snapshot.setCampaignId(campaign.getId());
            snapshot.setCampaignName(campaign.getName());
            snapshot.setSnapshotDate(today);
            snapshot.setImpressions(insights.path("impressions").asLong(0));
            snapshot.setReach(insights.path("reach").asLong(0));
            snapshot.setClicks(clicks);
            snapshot.setSpend(spend);
            snapshot.setCtr(ctr);
            snapshot.setCpc(cpc);
            snapshot.setFrequency(insights.path("frequency").asDouble(0));
            snapshot.setRevenue(revenue);
            snapshot.setRoas(spend > 0 ? revenue / spend : 0);
            metricRepo.save(snapshot);

        } catch (WebClientResponseException e) {
            log.warn("Failed to sync insights for campaign {}: {} {}",
                     campaign.getName(), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Failed to sync insights for campaign {}: {}",
                     campaign.getName(), e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Execute Actions on Meta (THIS IS WHAT REPLACES THE HUMAN)
    // ══════════════════════════════════════════════════════════════════

    /** Pause a campaign on Meta */
    public boolean pauseCampaign(MetaConnection conn, String metaCampaignId) {
        return updateCampaignStatus(conn, metaCampaignId, "PAUSED");
    }

    /** Resume a campaign on Meta */
    public boolean resumeCampaign(MetaConnection conn, String metaCampaignId) {
        return updateCampaignStatus(conn, metaCampaignId, "ACTIVE");
    }

    /** Update campaign budget on Meta (amount in rupees, converted to paisa) */
    public boolean updateBudget(MetaConnection conn, String metaCampaignId, double newBudgetRupees) {
        try {
            long budgetPaisa = (long) (newBudgetRupees * 100);
            graphClient.post()
                    .uri("/" + apiVersion + "/" + metaCampaignId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("daily_budget=" + budgetPaisa +
                               "&access_token=" + conn.getAccessToken())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("💰 Budget updated on Meta: {} → ₹{}", metaCampaignId, newBudgetRupees);
            return true;
        } catch (Exception e) {
            log.error("Failed to update budget on Meta: {}", e.getMessage());
            return false;
        }
    }

    private boolean updateCampaignStatus(MetaConnection conn, String metaCampaignId, String status) {
        try {
            graphClient.post()
                    .uri("/" + apiVersion + "/" + metaCampaignId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("status=" + status +
                               "&access_token=" + conn.getAccessToken())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("✅ Campaign {} → {} on Meta", metaCampaignId, status);
            return true;
        } catch (Exception e) {
            log.error("Failed to {} campaign on Meta: {}", status, e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Token Health Monitoring
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check and refresh tokens that are about to expire.
     * Runs every 6 hours. Long-lived tokens last ~60 days.
     */
    @Scheduled(fixedDelay = 21600000) // 6 hours
    public void monitorTokenHealth() {
        List<MetaConnection> connections = metaConnRepo.findByTokenStatus("VALID");
        for (MetaConnection conn : connections) {
            if (conn.getTokenExpiresAt() != null &&
                conn.getTokenExpiresAt().isBefore(LocalDateTime.now().plusDays(7))) {
                log.warn("⚠️ Meta token expiring soon for account: {}", conn.getAdAccountName());
                conn.setTokenStatus("EXPIRING_SOON");
                metaConnRepo.save(conn);
            }

            // Verify token is still valid
            try {
                graphClient.get()
                        .uri(u -> u.path("/" + apiVersion + "/me")
                                .queryParam("access_token", conn.getAccessToken())
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (WebClientResponseException.Unauthorized e) {
                log.error("❌ Meta token expired for: {}", conn.getAdAccountName());
                conn.setTokenStatus("EXPIRED");
                metaConnRepo.save(conn);
            } catch (Exception e) {
                log.warn("Meta token check failed for {}: {}", conn.getAdAccountName(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Auto-Sync (Cron)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Auto-sync all valid Meta connections every 15 minutes.
     * This is the heartbeat — always pulling fresh data from Meta.
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void autoSyncAllConnections() {
        List<MetaConnection> valid = metaConnRepo.findByTokenStatus("VALID");
        for (MetaConnection conn : valid) {
            try {
                syncCampaigns(conn);
                syncAdCreativeMetrics(conn);  // Also pull actualCtr into creatives
            } catch (Exception e) {
                log.error("Auto-sync failed for {}: {}", conn.getAdAccountName(), e.getMessage());
            }
        }
    }

    /**
     * Sync actual performance metrics (CTR, impressions, clicks) from Meta
     * into AdCreative entities that have been published (have a metaAdId).
     * This populates actualCtr so A/B test winner logic has real data.
     */
    public void syncAdCreativeMetrics(MetaConnection conn) {
        List<AdCreative> publishedCreatives = adCreativeRepo
                .findByAccountIdAndStatusIn(conn.getAccountId(), List.of("ACTIVE", "PAUSED"));

        for (AdCreative creative : publishedCreatives) {
            if (creative.getMetaAdId() == null || creative.getMetaAdId().isBlank()) continue;
            try {
                String json = graphClient.get()
                        .uri(u -> u.path("/" + apiVersion + "/" + creative.getMetaAdId() + "/insights")
                                .queryParam("fields", "impressions,clicks,ctr,cpc,spend")
                                .queryParam("date_preset", "last_7d")
                                .queryParam("access_token", conn.getAccessToken())
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode data = parseJson(json).path("data");
                if (data.isEmpty()) continue;

                JsonNode ins = data.get(0);
                creative.setActualCtr(ins.path("ctr").asDouble(0));
                creative.setActualCpc(ins.path("cpc").asDouble(0));
                creative.setImpressions(ins.path("impressions").asLong(0));
                creative.setClicks(ins.path("clicks").asLong(0));
                creative.setSpend(ins.path("spend").asDouble(0));
                creative.setUpdatedAt(java.time.LocalDateTime.now());
                adCreativeRepo.save(creative);
                log.debug("🎨 Creative {} metrics synced: CTR={}", creative.getId(), creative.getActualCtr());

            } catch (Exception e) {
                log.debug("Could not sync creative metrics for {}: {}", creative.getMetaAdId(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════

    public List<MetaConnection> getConnectionsForAccount(String accountId) {
        return metaConnRepo.findByAccountId(accountId);
    }

    public Optional<MetaConnection> getActiveConnection(String accountId) {
        return metaConnRepo.findFirstByAccountIdAndTokenStatus(accountId, "VALID");
    }

    public MetaConnection updateConnectionSettings(String accountId, String id, Map<String, Object> settings) {
        MetaConnection conn = metaConnRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));
        if (!conn.getAccountId().equals(accountId)) {
            throw new SecurityException("Access denied for connection: " + id);
        }
        if (settings.containsKey("auto_manage_enabled")) {
            conn.setAutoManageEnabled((Boolean) settings.get("auto_manage_enabled"));
        }
        if (settings.containsKey("max_daily_spend")) {
            conn.setMaxDailySpend(Double.parseDouble(settings.get("max_daily_spend").toString()));
        }
        if (settings.containsKey("pause_roas_threshold")) {
            conn.setPauseRoasThreshold(Double.parseDouble(settings.get("pause_roas_threshold").toString()));
        }
        if (settings.containsKey("scale_up_roas_threshold")) {
            conn.setScaleUpRoasThreshold(Double.parseDouble(settings.get("scale_up_roas_threshold").toString()));
        }
        if (settings.containsKey("max_budget_change_percent")) {
            conn.setMaxBudgetChangePercent(Double.parseDouble(settings.get("max_budget_change_percent").toString()));
        }
        conn.setUpdatedAt(LocalDateTime.now());
        return metaConnRepo.save(conn);
    }

    public MetaConnection disconnectConnection(String accountId, String id) {
        MetaConnection conn = metaConnRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + id));
        if (!conn.getAccountId().equals(accountId)) {
            throw new SecurityException("Access denied for connection: " + id);
        }
        conn.setTokenStatus("REVOKED");
        conn.setUpdatedAt(LocalDateTime.now());
        return metaConnRepo.save(conn);
    }

    @CircuitBreaker(name = "metaGraphApi")
    @Retry(name = "metaGraphApi")
    public Map<String, String> fetchLeadDetails(MetaConnection conn, String leadgenId) {
        log.info("📥 Fetching details for leadgen_id: {}", leadgenId);
        try {
            String json = graphClient.get()
                    .uri(u -> u.path("/" + apiVersion + "/" + leadgenId)
                            .queryParam("access_token", conn.getAccessToken())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = parseJson(json);
            Map<String, String> details = new HashMap<>();
            details.put("id", leadgenId);
            
            JsonNode fieldData = root.path("field_data");
            if (fieldData.isArray()) {
                for (JsonNode field : fieldData) {
                    String name = field.path("name").asText();
                    JsonNode values = field.path("values");
                    if (values.isArray() && !values.isEmpty()) {
                        String value = values.get(0).asText();
                        details.put(name, value);
                    }
                }
            }
            return details;
        } catch (Exception e) {
            log.error("Failed to fetch lead details from Meta: {}", e.getMessage());
            throw new RuntimeException("Meta Leadgen fetch failed: " + e.getMessage(), e);
        }
    }

    private String mapMetaStatus(String metaStatus) {
        return switch (metaStatus) {
            case "ACTIVE" -> "ACTIVE";
            case "PAUSED" -> "PAUSED";
            case "DELETED", "ARCHIVED" -> "COMPLETED";
            default -> "PAUSED";
        };
    }

    private double extractActionValue(JsonNode insights, String actionType) {
        JsonNode actions = insights.path("actions");
        if (actions.isArray()) {
            for (JsonNode action : actions) {
                if (actionType.equals(action.path("action_type").asText())) {
                    return action.path("value").asDouble(0);
                }
            }
        }
        return 0;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Full-Loop Ad launching & Creative Uploading (Phase C)
    // ══════════════════════════════════════════════════════════════════

    @CircuitBreaker(name = "metaGraphApi")
    @Retry(name = "metaGraphApi")
    public String uploadAdImage(MetaConnection conn, byte[] imageBytes, String filename) {
        try {
            String adAccountId = conn.getMetaAdAccountId(); // act_XXXXX
            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();

            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "ad_image.jpg";
                }
            };

            body.add("filename", resource);
            body.add("access_token", conn.getAccessToken());

            log.info("🖼️ Uploading image bytes to Meta: act={} | filename={}", adAccountId, resource.getFilename());

            String response = graphClient.post()
                    .uri("/" + apiVersion + "/" + adAccountId + "/adimages")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = mapper.readTree(response);
            String hash = root.path("images").path(resource.getFilename()).path("hash").asText();

            if (hash == null || hash.isBlank()) {
                throw new RuntimeException("Meta did not return an image hash in response: " + response);
            }

            log.info("✅ Image uploaded to Meta Library! Hash: {}", hash);
            return hash;
        } catch (Exception e) {
            log.error("❌ Meta Image Upload failed: {}", e.getMessage());
            throw new RuntimeException("Failed to upload image to Meta: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = "metaGraphApi")
    @Retry(name = "metaGraphApi")
    public String launchAd(MetaConnection conn, String campaignName, String adSetName, String adName,
                           String headline, String bodyText, String imageHash, String targetLink,
                           String pageId, double dailyBudgetRupees) {
        String adAccountId = conn.getMetaAdAccountId();
        String accessToken = conn.getAccessToken();
        long budgetPaisa = (long) (dailyBudgetRupees * 100);

        try {
            log.info("🚀 Initiating full-loop AI ad deployment on Meta: Account={}", adAccountId);

            // 1. Create Campaign
            String campaignBody = String.format(
                    "name=%s&objective=OUTCOME_TRAFFIC&status=PAUSED&special_ad_categories=[\"NONE\"]&access_token=%s",
                    campaignName, accessToken);

            String campaignResp = graphClient.post()
                    .uri("/" + apiVersion + "/" + adAccountId + "/campaigns")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(campaignBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String campaignId = extractField(campaignResp, "id");
            log.info("   ↳ Campaign Created: ID={}", campaignId);

            // 2. Create Ad Set
            String targetingJson = "{\"geo_locations\":{\"countries\":[\"IN\"]}}";
            String adsetBody = String.format(
                    "name=%s&campaign_id=%s&daily_budget=%d&billing_event=IMPRESSIONS&optimization_goal=LINK_CLICKS" +
                    "&targeting=%s&status=PAUSED&access_token=%s",
                    adSetName, campaignId, budgetPaisa, targetingJson, accessToken);

            String adsetResp = graphClient.post()
                    .uri("/" + apiVersion + "/" + adAccountId + "/adsets")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(adsetBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String adsetId = extractField(adsetResp, "id");
            log.info("   ↳ Ad Set Created: ID={}", adsetId);

            // 3. Create Ad Creative
            String storySpec = String.format(
                    "{\"page_id\":\"%s\",\"link_data\":{\"image_hash\":\"%s\",\"message\":\"%s\",\"link\":\"%s\",\"name\":\"%s\"}}",
                    pageId, imageHash, bodyText, targetLink, headline);

            String creativeBody = String.format(
                    "name=%s&object_story_spec=%s&access_token=%s",
                    adName + "_creative", storySpec, accessToken);

            String creativeResp = graphClient.post()
                    .uri("/" + apiVersion + "/" + adAccountId + "/adcreatives")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(creativeBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String creativeId = extractField(creativeResp, "id");
            log.info("   ↳ Ad Creative Registered: ID={}", creativeId);

            // 4. Create Ad
            String adBody = String.format(
                    "name=%s&adset_id=%s&creative={\"creative_id\":\"%s\"}&status=PAUSED&access_token=%s",
                    adName, adsetId, creativeId, accessToken);

            String adResp = graphClient.post()
                    .uri("/" + apiVersion + "/" + adAccountId + "/ads")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(adBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String adId = extractField(adResp, "id");
            log.info("✅ Live Meta Ad deployed successfully! Ad ID: {}", adId);
            return adId;

        } catch (Exception e) {
            log.error("❌ Failed to launch ad on Meta: {}", e.getMessage());
            throw new RuntimeException("Meta ad launch failed: " + e.getMessage(), e);
        }
    }

    private String extractField(String json, String field) {
        try {
            return mapper.readTree(json).path(field).asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Meta API response: " + e.getMessage());
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage());
        }
    }
}
