package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.AgentRuntimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@Slf4j
public class AgentRuntimeController {

    private final AgentRuntimeService agentRuntimeService;

    public AgentRuntimeController(AgentRuntimeService agentRuntimeService) {
        this.agentRuntimeService = agentRuntimeService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runAgent(@RequestBody Map<String, Object> payload) {
        String agentType = (String) payload.getOrDefault("agentType", "CHAT_AGENT");
        String message = (String) payload.getOrDefault("message", "");
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        String traceId = (String) payload.getOrDefault("traceId", "tr-" + System.currentTimeMillis());

        Map<String, Object> result = agentRuntimeService.executeAgent(agentType, message, metadata, traceId);
        return ResponseEntity.ok(result);
    }
}
