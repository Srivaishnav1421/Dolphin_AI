package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.MetaCapiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/capi")
@Slf4j
public class MetaCapiController {

    private final MetaCapiService capiService;

    public MetaCapiController(MetaCapiService capiService) {
        this.capiService = capiService;
    }

    /**
     * Endpoint to manually trigger or test a Meta Conversions API event.
     */
    @PostMapping("/event")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> triggerConversionEvent(@RequestBody Map<String, Object> request) {
        String leadId = (String) request.get("lead_id");
        String eventName = (String) request.get("event_name");
        Double value = request.get("value") != null ? Double.valueOf(request.get("value").toString()) : null;
        String currency = (String) request.get("currency");

        if (leadId == null || eventName == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing lead_id or event_name"));
        }

        boolean success = capiService.sendServerEvent(leadId, eventName, value, currency);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Event successfully dispatched to Meta Pixel CAPI."));
        } else {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Dispatch failed. Check logs/configuration."));
        }
    }
}
