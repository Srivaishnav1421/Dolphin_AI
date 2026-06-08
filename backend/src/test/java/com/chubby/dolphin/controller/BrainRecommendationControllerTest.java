package com.chubby.dolphin.controller;

import com.chubby.dolphin.brain.BrainRecommendationService;
import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainRecommendationControllerTest {

    @Mock private BrainRecommendationService service;
    @Mock private SecurityUtils sec;

    private BrainRecommendationController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new BrainRecommendationController(service, sec);
        when(sec.currentAccountId()).thenReturn("ws-123");
        when(sec.currentEmail()).thenReturn("admin@dolphin.ai");
    }

    @Test
    public void testGetRecommendations() {
        BrainDecision recommendation = new BrainDecision();
        recommendation.setId("dec-1");
        when(service.getRecentRecommendations("ws-123")).thenReturn(Collections.singletonList(recommendation));

        ResponseEntity<List<BrainDecision>> response = controller.getRecommendations();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("dec-1", response.getBody().get(0).getId());
    }

    @Test
    public void testGetRecommendationByIdSuccess() {
        BrainDecision recommendation = new BrainDecision();
        recommendation.setId("dec-1");
        when(service.getRecommendationById("dec-1")).thenReturn(Optional.of(recommendation));

        ResponseEntity<BrainDecision> response = controller.getRecommendation("dec-1");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("dec-1", response.getBody().getId());
    }

    @Test
    public void testGetRecommendationByIdNotFound() {
        when(service.getRecommendationById("dec-1")).thenReturn(Optional.empty());

        ResponseEntity<BrainDecision> response = controller.getRecommendation("dec-1");

        assertNotNull(response);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    public void testEvaluate() {
        BrainDecision recommendation = new BrainDecision();
        when(service.evaluateAndSave("ws-123")).thenReturn(Collections.singletonList(recommendation));

        ResponseEntity<List<BrainDecision>> response = controller.evaluate();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    public void testApproveSuccess() {
        BrainDecision recommendation = new BrainDecision();
        recommendation.setId("dec-1");
        recommendation.setStatus("APPROVED");
        when(service.approve("dec-1", "admin@dolphin.ai")).thenReturn(recommendation);

        ResponseEntity<?> response = controller.approve("dec-1");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("APPROVED", ((BrainDecision) response.getBody()).getStatus());
    }

    @Test
    public void testRejectSuccess() {
        BrainDecision recommendation = new BrainDecision();
        recommendation.setId("dec-1");
        recommendation.setStatus("REJECTED");
        when(service.reject("dec-1", "admin@dolphin.ai")).thenReturn(recommendation);

        ResponseEntity<?> response = controller.reject("dec-1");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("REJECTED", ((BrainDecision) response.getBody()).getStatus());
    }
}
