package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.entity.AdvantageExperiment;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.chubby.dolphin.repository.AdvantageExperimentRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdvantageExperimentServiceTest {

    @Mock private AdvantageExperimentRepository experimentRepo;
    @Mock private CampaignRepository campaignRepo;
    @Mock private MetaConnectionRepository metaConnRepo;
    @Mock private AdCreativeRepository creativeRepo;
    @Mock private BrainDecisionService brainDecisionService;

    private AdvantageExperimentService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AdvantageExperimentService(experimentRepo, campaignRepo, metaConnRepo, creativeRepo, brainDecisionService);
    }

    @Test
    public void testProposeExperiment_EligibilitySavesSuggested() {
        String workspaceId = "ws-123";
        String campaignId = "camp-abc";

        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setMetaCampaignId("meta-123");
        campaign.setRoas(1.5);

        when(campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(experimentRepo.findByCampaignIdAndStatus(campaignId, "ACTIVE")).thenReturn(Optional.empty());
        when(experimentRepo.save(any(AdvantageExperiment.class))).thenAnswer(i -> i.getArgument(0));

        AdvantageExperiment result = service.proposeAdvantagePlusExperiment(workspaceId, campaignId);

        assertNotNull(result);
        assertEquals("SUGGESTED", result.getStatus());
        assertEquals(1.5, result.getRoasBefore());
        verify(experimentRepo, times(1)).save(any(AdvantageExperiment.class));
    }

    @Test
    public void testEvaluateActiveExperiments_TriggersSafetyRollbackWhenRoasPlummets() {
        String campaignId = "camp-abc";
        AdvantageExperiment exp = new AdvantageExperiment();
        exp.setId("exp-999");
        exp.setCampaignId(campaignId);
        exp.setMetaCampaignId("meta-123");
        exp.setRoasBefore(2.2);
        exp.setStatus("ACTIVE");
        exp.setSwitchedAt(LocalDateTime.now().minusDays(5));

        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Brand Awareness");
        campaign.setRoas(0.8); // Plummeted from 2.2 to 0.8!

        when(experimentRepo.findByStatus("ACTIVE")).thenReturn(List.of(exp));
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(any(), any())).thenReturn(Optional.empty());

        service.evaluateActiveExperiments();

        assertEquals("REVERTED", exp.getStatus());
        assertNotNull(exp.getRevertedAt());
        assertTrue(exp.getRevertReason().contains("Safety net triggered"));
        verify(experimentRepo, times(1)).save(exp);
    }

    @Test
    public void testActivateExperiment_BlocksWithoutMetaConnection() {
        AdvantageExperiment exp = new AdvantageExperiment();
        exp.setId("exp-123");
        exp.setWorkspaceId("ws-123");
        exp.setCampaignId("camp-abc");
        exp.setMetaCampaignId("meta-123");
        exp.setStatus("SUGGESTED");

        when(experimentRepo.findById("exp-123")).thenReturn(Optional.of(exp));
        when(metaConnRepo.findFirstByAccountIdAndTokenStatus("ws-123", "VALID")).thenReturn(Optional.empty());
        when(experimentRepo.save(any(AdvantageExperiment.class))).thenAnswer(i -> i.getArgument(0));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.activateAdvantagePlus("exp-123"));

        assertTrue(thrown.getMessage().contains("requires a valid Meta connection"));
        assertEquals("ACTIVATION_BLOCKED", exp.getStatus());
        verify(experimentRepo).save(exp);
    }

    @Test
    public void testCreateAbExperiment_Generates27Permutations() {
        String workspaceId = "ws-123";
        String campaignId = "camp-abc";

        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setMetaCampaignId("meta-123");
        campaign.setRoas(2.0);

        when(campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(experimentRepo.save(any(AdvantageExperiment.class))).thenAnswer(i -> i.getArgument(0));

        List<String> headlines = Arrays.asList("H1", "H2", "H3");
        List<String> bodies = Arrays.asList("B1", "B2", "B3");
        List<String> ctas = Arrays.asList("C1", "C2", "C3");

        AdvantageExperiment result = service.createAbExperiment(workspaceId, campaignId, headlines, bodies, ctas);

        assertNotNull(result);
        assertEquals("SUGGESTED", result.getStatus());
        assertTrue(result.getRevertReason().startsWith("A/B Test ID:"));
        
        // Assert that 27 creative permutation records were saved
        verify(creativeRepo, times(27)).save(any(AdCreative.class));
        verify(brainDecisionService, times(1)).incrementExperimentCount();
    }
}
