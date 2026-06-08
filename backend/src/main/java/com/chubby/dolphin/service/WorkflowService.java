package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.WorkflowApproval;
import com.chubby.dolphin.entity.WorkflowExecution;
import com.chubby.dolphin.repository.WorkflowApprovalRepository;
import com.chubby.dolphin.repository.WorkflowExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class WorkflowService {

    private final WorkflowExecutionRepository executionRepo;
    private final WorkflowApprovalRepository approvalRepo;
    private final SimpMessagingTemplate wsTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${n8n.webhook-url:http://localhost:5678/webhook/execute}")
    private String n8nWebhookUrl;

    public WorkflowService(
            WorkflowExecutionRepository executionRepo,
            WorkflowApprovalRepository approvalRepo,
            @Autowired(required = false) SimpMessagingTemplate wsTemplate,
            ObjectMapper objectMapper) {
        this.executionRepo = executionRepo;
        this.approvalRepo = approvalRepo;
        this.wsTemplate = wsTemplate;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public WorkflowExecution triggerWorkflow(String userId, String sessionId, String message, String workspaceId) {
        String executionId = UUID.randomUUID().toString();
        String traceId = "tr-" + UUID.randomUUID().toString();

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(executionId);
        execution.setExecutionId(executionId);
        execution.setTraceId(traceId);
        execution.setParentTraceId(null);
        execution.setUserId(userId);
        execution.setWorkspaceId(workspaceId);
        execution.setTenantId(workspaceId); // Workspace maps to tenant context
        execution.setUserRequest(message);
        execution.setStatus("RUNNING");
        execution.setStartTime(LocalDateTime.now());
        execution.setWorkflowName("DolphinAI Main Workflow");
        execution.setWorkflowId("dolphin-main-workflow");
        execution.setWorkflowVersion("1.0.0");
        execution.setWorkflowSnapshot("{}");

        WorkflowExecution saved = executionRepo.save(execution);

        // Broadcast initial started event
        broadcastEvent(workspaceId, executionId, traceId, "WORKFLOW_STARTED", "Workflow Started", null);

        // Call n8n asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                log.info("📡 Forwarding execution {} to n8n webhook: {}", executionId, n8nWebhookUrl);

                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", userId);
                payload.put("sessionId", sessionId);
                payload.put("message", message);
                payload.put("executionId", executionId);
                payload.put("traceId", traceId);
                payload.put("timestamp", LocalDateTime.now().toString());

                String response = webClient.post()
                        .uri(n8nWebhookUrl)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(60))
                        .onErrorReturn("{\"status\": \"FAILED\", \"error\": \"n8n webhook timeout/connection error\"}")
                        .block();

                log.info("📥 n8n execution response for {}: {}", executionId, response);
                processN8nCompletedResponse(executionId, response);

            } catch (Exception e) {
                log.error("❌ Failed to call n8n webhook for {}: {}", executionId, e.getMessage());
                markExecutionFailed(executionId, "Failed to connect to n8n: " + e.getMessage());
            }
        });

        return saved;
    }

    public void processEvent(Map<String, Object> event) {
        String executionId = (String) event.get("executionId");
        String eventType = (String) event.get("eventType");
        String message = (String) event.get("message");
        Map<String, Object> details = (Map<String, Object>) event.get("details");

        log.info("🔔 Processing workflow event: {} | Type: {} | Message: {}", executionId, eventType, message);

        executionRepo.findByExecutionId(executionId).ifPresent(exec -> {
            String traceId = exec.getTraceId();

            if ("AGENT_SELECTED".equals(eventType) && details != null) {
                exec.setAgentUsed((String) details.get("agent"));
                executionRepo.save(exec);
            }

            if ("WORKFLOW_COMPLETED".equals(eventType)) {
                exec.setStatus("COMPLETED");
                exec.setEndTime(LocalDateTime.now());
                exec.setExecutionDuration(Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis());
                if (details != null && details.containsKey("response")) {
                    exec.setFinalResponse((String) details.get("response"));
                }
                executionRepo.save(exec);
            }

            if ("WORKFLOW_FAILED".equals(eventType)) {
                exec.setStatus("FAILED");
                exec.setEndTime(LocalDateTime.now());
                exec.setExecutionDuration(Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis());
                if (details != null && details.containsKey("error")) {
                    exec.setErrorLogs((String) details.get("error"));
                }
                executionRepo.save(exec);
            }

            if ("WAITING_FOR_APPROVAL".equals(eventType)) {
                exec.setStatus("WAITING_FOR_APPROVAL");
                executionRepo.save(exec);

                // Create a Human Approval Checkpoint
                WorkflowApproval approval = new WorkflowApproval();
                approval.setExecutionId(executionId);
                approval.setTraceId(traceId);
                approval.setWorkspaceId(exec.getWorkspaceId());
                approval.setStatus("PENDING");
                approvalRepo.save(approval);
            }

            broadcastEvent(exec.getWorkspaceId(), executionId, traceId, eventType, message, details);
        });
    }

    public void respondToApproval(String approvalId, String decision, String reason) {
        approvalRepo.findById(approvalId).ifPresent(approval -> {
            approval.setStatus(decision);
            approval.setDecisionReason(reason);
            approval.setUpdatedAt(LocalDateTime.now());
            approvalRepo.save(approval);

            executionRepo.findByExecutionId(approval.getExecutionId()).ifPresent(exec -> {
                exec.setStatus("APPROVED".equals(decision) ? "RUNNING" : "FAILED");
                if ("REJECTED".equals(decision)) {
                    exec.setErrorLogs("Rejected by human: " + reason);
                    exec.setEndTime(LocalDateTime.now());
                    exec.setExecutionDuration(Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis());
                }
                executionRepo.save(exec);

                broadcastEvent(exec.getWorkspaceId(), exec.getExecutionId(), exec.getTraceId(), 
                        "APPROVED".equals(decision) ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                        "Approval checkpoint: " + decision, 
                        Map.of("reason", reason));
            });
        });
    }

    private void processN8nCompletedResponse(String executionId, String response) {
        executionRepo.findByExecutionId(executionId).ifPresent(exec -> {
            try {
                Map<String, Object> map = objectMapper.readValue(response, Map.class);
                String status = (String) map.getOrDefault("status", "COMPLETED");
                exec.setStatus(status);
                exec.setEndTime(LocalDateTime.now());
                exec.setExecutionDuration(Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis());

                if (map.containsKey("response")) {
                    exec.setFinalResponse((String) map.get("response"));
                }
                if (map.containsKey("error")) {
                    exec.setErrorLogs((String) map.get("error"));
                }
                executionRepo.save(exec);

                broadcastEvent(exec.getWorkspaceId(), executionId, exec.getTraceId(), 
                        "COMPLETED".equals(status) ? "WORKFLOW_COMPLETED" : "WORKFLOW_FAILED", 
                        "COMPLETED".equals(status) ? "Workflow Completed" : "Workflow Failed", 
                        map);

            } catch (Exception e) {
                // If it isn't standard JSON, save it as the response content directly
                exec.setStatus("COMPLETED");
                exec.setEndTime(LocalDateTime.now());
                exec.setExecutionDuration(Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis());
                exec.setFinalResponse(response);
                executionRepo.save(exec);

                broadcastEvent(exec.getWorkspaceId(), executionId, exec.getTraceId(), "WORKFLOW_COMPLETED", "Workflow Completed", null);
            }
        });
    }

    private void markExecutionFailed(String executionId, String error) {
        executionRepo.findByExecutionId(executionId).ifPresent(exec -> {
            exec.setStatus("FAILED");
            exec.setEndTime(LocalDateTime.now());
            exec.setErrorLogs(error);
            exec.setExecutionDuration(Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis());
            executionRepo.save(exec);

            broadcastEvent(exec.getWorkspaceId(), executionId, exec.getTraceId(), "WORKFLOW_FAILED", "Workflow Failed: " + error, Map.of("error", error));
        });
    }

    private void broadcastEvent(String workspaceId, String executionId, String traceId, String eventType, String message, Map<String, Object> details) {
        if (wsTemplate != null) {
            Map<String, Object> wsPayload = new HashMap<>();
            wsPayload.put("workspaceId", workspaceId);
            wsPayload.put("executionId", executionId);
            wsPayload.put("traceId", traceId);
            wsPayload.put("eventType", eventType);
            wsPayload.put("message", message);
            wsPayload.put("details", details);
            wsPayload.put("timestamp", LocalDateTime.now().toString());

            wsTemplate.convertAndSend("/topic/workspace/" + workspaceId + "/workflow", wsPayload);
        }
    }
}
