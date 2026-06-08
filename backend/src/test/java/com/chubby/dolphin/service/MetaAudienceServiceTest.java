package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MetaAudienceServiceTest {

    @Mock private MetaAudienceRepository audienceRepo;
    @Mock private MetaConnectionRepository metaConnRepo;
    @Mock private LeadRepository leadRepo;

    private MetaAudienceService service;
    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        service = new MetaAudienceService(audienceRepo, metaConnRepo, leadRepo, mapper);
    }

    @Test
    public void testCreateCustomAudienceFallback_CreatesLocalRecord() {
        String workspaceId = "ws-123";
        String name = "Test Custom Audience";

        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID"))
                .thenReturn(Optional.empty()); // No active meta connections

        when(audienceRepo.save(any(MetaAudience.class))).thenAnswer(i -> i.getArgument(0));

        MetaAudience result = service.createCustomAudience(workspaceId, name, "Fallback test");

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals("CUSTOM", result.getAudienceType());
        assertTrue(result.getMetaAudienceId().startsWith("mock-"));
        verify(audienceRepo, times(1)).save(any(MetaAudience.class));
    }

    @Test
    public void testCreateSuperLookalike_GeneratesThreeTiers() {
        String workspaceId = "ws-123";
        String name = "VIP Lookalike";
        String sourceId = "src-456";

        MetaAudience source = new MetaAudience();
        source.setId(sourceId);
        source.setMetaAudienceId("meta-src-456");
        when(audienceRepo.findById(sourceId)).thenReturn(Optional.of(source));

        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID"))
                .thenReturn(Optional.empty()); // fallback to local mock

        when(audienceRepo.save(any(MetaAudience.class))).thenAnswer(i -> i.getArgument(0));

        List<MetaAudience> result = service.createSuperLookalike(workspaceId, name, sourceId, "IN");

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(0.01, result.get(0).getLookalikeRatio());
        assertEquals(0.02, result.get(1).getLookalikeRatio());
        assertEquals(0.05, result.get(2).getLookalikeRatio());
        verify(audienceRepo, times(3)).save(any(MetaAudience.class));
    }

    @Test
    public void testSyncHotLeadsToAudience_UploadsIntentLeads() {
        String workspaceId = "ws-123";
        String audienceId = "aud-789";

        MetaAudience audience = new MetaAudience();
        audience.setId(audienceId);
        audience.setWorkspaceId(workspaceId);
        audience.setMetaAudienceId("mock-aud");
        audience.setSizeEstimate(100L);
        when(audienceRepo.findById(audienceId)).thenReturn(Optional.of(audience));

        Lead lead1 = new Lead();
        lead1.setEmail("hot-lead@example.com");
        lead1.setPhone("919999999999");
        lead1.setStatus("HOT");
        lead1.setAccountId(workspaceId);

        when(leadRepo.findByAccountIdAndStatus(workspaceId, "HOT")).thenReturn(List.of(lead1));
        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID")).thenReturn(Optional.empty());

        int count = service.syncHotLeadsToAudience(workspaceId, audienceId);

        assertEquals(1, count);
        assertEquals(101L, audience.getSizeEstimate());
        verify(audienceRepo, times(1)).save(audience);
    }
}
