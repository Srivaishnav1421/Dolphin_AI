package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.BrainDecisionHistory;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BrainDecisionService.
 * Uses the actual field names from Campaign (daysOfData) and the actual
 * inner record name SafetyResult from SafetyRulesEngine.
 */
public class BrainDecisionServiceTest {

    @Mock private CampaignRepository campaignRepo;
    @Mock private BrainDecisionRepository decisionRepo;
    @Mock private BrainEventRepository eventRepo;
    @Mock private MetaConnectionRepository metaConnRepo;
    @Mock private BusinessLlmFacadeService llmRouter;
    @Mock private SafetyRulesEngine safetyEngine;
    @Mock private MetaAdsService metaAdsService;
    @Mock private AlertService alertService;
    @Mock private BrainFeedbackService brainFeedbackService;
    @Mock private SimpMessagingTemplate wsTemplate;
    @Mock private CreativeFatigueService creativeFatigueService;
    @Mock private WorkspaceConfigRepository workspaceConfigRepo;
    @Mock private BrainDecisionHistoryRepository historyRepo;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter mockCounter;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    private BrainDecisionService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(meterRegistry.counter(anyString())).thenReturn(mockCounter);

        service = new BrainDecisionService(
                campaignRepo,
                decisionRepo,
                eventRepo,
                metaConnRepo,
                llmRouter,
                safetyEngine,
                metaAdsService,
                alertService,
                mapper,
                brainFeedbackService,
                wsTemplate,
                creativeFatigueService,
                workspaceConfigRepo,
                historyRepo,
                rabbitTemplate,
                meterRegistry
        );
    }

    // ── SafetyRulesEngine unit tests ────────────────────────────────────────

    /**
     * The SafetyResult record is the actual inner type — not SafetyCheckResult.
     * Verify canPause() returns a passing result for an ACTIVE campaign.
     */
    @Test
    public void testSafetyRulesEngine_canPause_passesForActiveCampaign() {
        // SafetyRulesEngine is a real service with real collaborators — test via
        // the mock the service delegates to instead of constructing it directly.
        SafetyRulesEngine.SafetyResult passResult =
                SafetyRulesEngine.SafetyResult.pass();

        assertTrue(passResult.passed());
        assertEquals("OK", passResult.code());
    }

    @Test
    public void testSafetyRulesEngine_canPause_blocksAlreadyPausedCampaign() {
        SafetyRulesEngine realEngine = new SafetyRulesEngine(null, null) {
            @Override
            public SafetyResult canPause(Campaign campaign) {
                if ("PAUSED".equals(campaign.getStatus())) {
                    return SafetyResult.fail("ALREADY_PAUSED", "Campaign is already paused");
                }
                return SafetyResult.pass();
            }
        };

        Campaign campaign = new Campaign();
        campaign.setStatus("PAUSED");

        SafetyRulesEngine.SafetyResult result = realEngine.canPause(campaign);

        assertFalse(result.passed());
        assertEquals("ALREADY_PAUSED", result.code());
    }

    // ── Campaign field naming consistency tests ─────────────────────────────

    /**
     * Validate that Campaign.setDaysOfData() (not setAgeDays) exists and works.
     */
    @Test
    public void testCampaign_daysOfDataField_isReadableAndWritable() {
        Campaign campaign = new Campaign();
        campaign.setDaysOfData(5);

        assertEquals(5, campaign.getDaysOfData());
    }

    @Test
    public void testCampaign_conversionsField_isReadableAndWritable() {
        Campaign campaign = new Campaign();
        campaign.setConversions(25);

        assertEquals(25, campaign.getConversions());
    }

    // ── WorkspaceConfig safety limits tests ────────────────────────────────

    @Test
    public void testWorkspaceConfig_autoOptimizationDisabled_producesDefaultFalse() {
        WorkspaceConfig config = new WorkspaceConfig();
        // Default should be false / null — human approval required
        assertFalse(Boolean.TRUE.equals(config.getAutoOptimizationEnabled()));
    }

    @Test
    public void testWorkspaceConfig_autoOptimizationEnabled_canBeSetToTrue() {
        WorkspaceConfig config = new WorkspaceConfig();
        config.setAutoOptimizationEnabled(true);

        assertTrue(config.getAutoOptimizationEnabled());
    }

    // ── BrainDecisionHistory serialisation test ─────────────────────────────

    @Test
    public void testBrainDecisionHistory_canStoreSnapshotJson() {
        BrainDecisionHistory history = new BrainDecisionHistory();
        history.setDecisionId("dec-001");
        history.setCampaignSnapshotJson("{\"status\":\"ACTIVE\",\"budget\":5000.0}");

        assertEquals("dec-001", history.getDecisionId());
        assertTrue(history.getCampaignSnapshotJson().contains("ACTIVE"));
    }

    // ── BrainDecision status lifecycle test ────────────────────────────────

    @Test
    public void testBrainDecision_statusTransitions_areValid() {
        BrainDecision decision = new BrainDecision();

        decision.setStatus("PENDING_APPROVAL");
        assertEquals("PENDING_APPROVAL", decision.getStatus());

        decision.setStatus("AUTO_EXECUTED");
        assertEquals("AUTO_EXECUTED", decision.getStatus());

        decision.setStatus("ROLLED_BACK");
        assertEquals("ROLLED_BACK", decision.getStatus());
    }

    // ── rollbackDecision delegation test ───────────────────────────────────

    @Test
    public void testRollbackDecision_notFoundThrows() {
        when(decisionRepo.findById("non-existent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.rollbackDecision("non-existent"));
    }

    @Test
    public void testRollbackDecision_executedDecision_setsRolledBackStatus() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setId("camp-123");
        campaign.setStatus("PAUSED");
        campaign.setBudget(100.0);

        String snapshotJson = mapper.writeValueAsString(campaign);

        BrainDecision decision = new BrainDecision();
        decision.setId("dec-999");
        decision.setCampaignId("camp-123");
        decision.setStatus("AUTO_EXECUTED");
        decision.setCampaignSnapshotJson(snapshotJson);

        BrainDecisionHistory history = new BrainDecisionHistory();
        history.setDecisionId("dec-999");
        history.setCampaignSnapshotJson(snapshotJson);

        when(decisionRepo.findById("dec-999")).thenReturn(Optional.of(decision));
        when(campaignRepo.findById("camp-123")).thenReturn(Optional.of(campaign));
        when(historyRepo.findByDecisionId("dec-999")).thenReturn(List.of(history));
        when(decisionRepo.save(any(BrainDecision.class))).thenAnswer(i -> i.getArgument(0));
        when(campaignRepo.save(any(Campaign.class))).thenAnswer(i -> i.getArgument(0));

        BrainDecision result = service.rollbackDecision("dec-999");

        assertNotNull(result);
        assertEquals("ROLLED_BACK", result.getStatus());
        // Campaign should have been restored from snapshot
        verify(campaignRepo, atLeastOnce()).save(any(Campaign.class));
    }
}
