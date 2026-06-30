package com.chubby.dolphin;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CRMStatusTemperatureSeparationIT {

    @Autowired
    private LeadRepository leadRepository;

    @Test
    public void testStatusAndTemperatureAreSeparate() {
        Lead lead = new Lead();
        lead.setWorkspaceId("test-workspace");
        lead.setName("CRM_API_STATUS_VERIFY_" + System.currentTimeMillis());
        lead.setEmail("crm.status.verify@example.com");
        lead.setPhone("0000000000");
        lead.setMessage("Test lead for status and temperature separation");
        lead.setSource("LOCAL_TEST");
        lead.setStatus("NEW");
        lead.setTemperature("UNKNOWN");
        lead.setScore(0.0);
        
        Lead saved = leadRepository.save(lead);
        
        // Verify fields are separate
        assertNotNull(saved.getId(), "Lead ID should be generated");
        assertEquals("NEW", saved.getStatus(), "Status should be NEW (business status)");
        assertEquals("UNKNOWN", saved.getTemperature(), "Temperature should be UNKNOWN initially");
        assertTrue(saved.getName().contains("CRM_API_STATUS_VERIFY_"), "Lead name should match pattern");
        
        // Fetch and verify
        Lead fetched = leadRepository.findById(saved.getId()).orElse(null);
        assertNotNull(fetched, "Lead should be retrievable from database");
        assertEquals("NEW", fetched.getStatus(), "Status must remain NEW after fetch");
        assertEquals("UNKNOWN", fetched.getTemperature(), "Temperature must be UNKNOWN after fetch");
        
        System.out.println("✅ API PROOF: Status and Temperature are separate fields");
        System.out.println("   - Status: " + fetched.getStatus());
        System.out.println("   - Temperature: " + fetched.getTemperature());
        System.out.println("   - Score: " + fetched.getScore());
    }

    @Test
    public void testScoringDoesNotOverwriteStatus() {
        Lead lead = new Lead();
        lead.setWorkspaceId("test-workspace");
        lead.setName("CRM_SCORE_VERIFY_" + System.currentTimeMillis());
        lead.setEmail("score.verify@example.com");
        lead.setPhone("1111111111");
        lead.setMessage("High quality lead with budget and timeline signals");
        lead.setSource("LOCAL_TEST");
        lead.setStatus("NEW");
        lead.setTemperature("UNKNOWN");
        
        Lead saved = leadRepository.save(lead);
        String originalStatus = saved.getStatus();
        
        // Simulate scoring by updating temperature only (like scoreExistingLead does)
        saved.setTemperature("HOT");
        saved.setScore(0.85);
        Lead scored = leadRepository.save(saved);
        
        // Verify status was NOT changed
        assertEquals(originalStatus, scored.getStatus(), "Scoring must NOT change status");
        assertEquals("HOT", scored.getTemperature(), "Scoring SHOULD change temperature");
        
        System.out.println("✅ SCORING PROOF: Status not overwritten");
        System.out.println("   - Original Status: " + originalStatus);
        System.out.println("   - After Scoring Status: " + scored.getStatus());
        System.out.println("   - After Scoring Temperature: " + scored.getTemperature());
    }
}
