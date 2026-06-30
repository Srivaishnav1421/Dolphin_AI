package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.WorkflowApproval;
import com.chubby.dolphin.entity.WorkflowExecution;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.repository.WorkflowApprovalRepository;
import com.chubby.dolphin.repository.WorkflowExecutionRepository;
import com.chubby.dolphin.repository.WorkflowTemplateRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflow")
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionRepository executionRepo;
    private final WorkflowApprovalRepository approvalRepo;
    private final WorkflowTemplateRepository templateRepo;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final AuditLogService auditLogService;
    private final LocalApprovalSafetyService localApprovalSafetyService;

    public WorkflowController(WorkflowService workflowService,
                              WorkflowExecutionRepository executionRepo,
                              WorkflowApprovalRepository approvalRepo,
                              WorkflowTemplateRepository templateRepo,
                              SecurityUtils sec,
                              AccessControlService access,
                              AuditLogService auditLogService,
                              LocalApprovalSafetyService localApprovalSafetyService) {
        this.workflowService = workflowService;
        this.executionRepo = executionRepo;
        this.approvalRepo = approvalRepo;
        this.templateRepo = templateRepo;
        this.sec = sec;
        this.access = access;
        this.auditLogService = auditLogService;
        this.localApprovalSafetyService = localApprovalSafetyService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeWorkflow(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.AUTOMATION_MANAGE);
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "session-" + System.currentTimeMillis());
        String userId = sec.currentUser().getId();
        String workspaceId = sec.currentWorkspaceId();

        if (localApprovalSafetyService.shouldRequireApprovalOnly("WORKFLOW_MANUAL_EXECUTION")) {
            localApprovalSafetyService.auditBlockedExecution(
                    workspaceId,
                    "WORKFLOW_MANUAL_EXECUTION",
                    "WorkflowExecution",
                    null,
                    "Blocked /api/workflow/execute before workflow row creation or n8n webhook call."
            );
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "status", "blocked",
                    "approval_required", true,
                    "external_execution_allowed", false,
                    "message", localApprovalSafetyService.blockedMessage("Workflow manual execution")
            ));
        }

        log.info("🚀 Triggering workflow for user {} in workspace {}", userId, workspaceId);
        WorkflowExecution execution = workflowService.triggerWorkflow(userId, sessionId, message, workspaceId);
        auditWorkflow("WORKFLOW_MANUAL_RUN", "WorkflowExecution", execution.getId(), "traceId=" + execution.getTraceId());
        return ResponseEntity.ok(execution);
    }

    @PostMapping("/event")
    public ResponseEntity<?> receiveEvent(@RequestBody Map<String, Object> event) {
        access.requireWorkspacePermission(Permission.AUTOMATION_MANAGE);
        Object executionId = event.get("executionId");
        if (!(executionId instanceof String id) || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "executionId is required"));
        }
        Optional<WorkflowExecution> execution = executionRepo.findByExecutionId(id);
        if (execution.isEmpty() || !sec.currentWorkspaceId().equals(execution.get().getWorkspaceId())) {
            return ResponseEntity.notFound().build();
        }
        if (localApprovalSafetyService.shouldRequireApprovalOnly("WORKFLOW_EVENT_CALLBACK")) {
            localApprovalSafetyService.auditBlockedExecution(
                    sec.currentWorkspaceId(),
                    "WORKFLOW_EVENT_CALLBACK",
                    "WorkflowExecution",
                    id,
                    "Blocked /api/workflow/event before external workflow callback mutation."
            );
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "status", "blocked",
                    "approval_required", true,
                    "external_execution_allowed", false,
                    "message", localApprovalSafetyService.blockedMessage("Workflow event callback")
            ));
        }
        workflowService.processEvent(event);
        auditWorkflow("WORKFLOW_EVENT_RECEIVED", "WorkflowExecution", id,
                "eventType=" + event.getOrDefault("eventType", ""));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<WorkflowExecution>> getExecutions() {
        access.requireWorkspacePermission(Permission.AUTOMATION_READ);
        String workspaceId = sec.currentWorkspaceId();
        return ResponseEntity.ok(executionRepo.findByWorkspaceId(workspaceId));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        access.requireWorkspacePermission(Permission.AUTOMATION_READ);
        String workspaceId = sec.currentWorkspaceId();

        long active = executionRepo.countByWorkspaceIdAndStatus(workspaceId, "RUNNING");
        long waiting = executionRepo.countByWorkspaceIdAndStatus(workspaceId, "WAITING_FOR_APPROVAL");
        long completed = executionRepo.countByWorkspaceIdAndStatus(workspaceId, "COMPLETED");
        long failed = executionRepo.countByWorkspaceIdAndStatus(workspaceId, "FAILED");

        Double avgDuration = executionRepo.getAverageDurationByWorkspaceId(workspaceId);
        List<Object[]> usageList = executionRepo.getAgentUsageStatsByWorkspaceId(workspaceId);

        List<Map<String, Object>> agentUsage = usageList.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("agent", row[0]);
            map.put("count", row[1]);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", active + waiting);
        stats.put("completedCount", completed);
        stats.put("failedCount", failed);
        stats.put("averageDurationMs", avgDuration != null ? avgDuration.longValue() : 0L);
        stats.put("agentUsage", agentUsage);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<List<WorkflowExecution>> getTrace(@PathVariable String traceId) {
        access.requireWorkspacePermission(Permission.AUTOMATION_READ);
        String workspaceId = sec.currentWorkspaceId();
        List<WorkflowExecution> list = executionRepo.findByTraceId(traceId);
        List<WorkflowExecution> filtered = list.stream()
                .filter(e -> workspaceId.equals(e.getWorkspaceId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/templates")
    public ResponseEntity<?> getTemplates() {
        access.requireWorkspacePermission(Permission.AUTOMATION_READ);
        return ResponseEntity.ok(templateRepo.findAll());
    }

    @GetMapping("/approvals")
    public ResponseEntity<List<WorkflowApproval>> getApprovals() {
        access.requireWorkspacePermission(Permission.AUTOMATION_READ);
        String workspaceId = sec.currentWorkspaceId();
        return ResponseEntity.ok(approvalRepo.findByWorkspaceIdAndStatus(workspaceId, "PENDING"));
    }

    @PostMapping("/approvals/{id}/respond")
    public ResponseEntity<?> respondToApproval(@PathVariable String id, @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.AUTOMATION_MANAGE);
        String workspaceId = sec.currentWorkspaceId();
        Optional<WorkflowApproval> appOpt = approvalRepo.findByIdAndWorkspaceId(id, workspaceId);
        if (appOpt.isEmpty()) return ResponseEntity.notFound().build();

        String decision = body.getOrDefault("decision", "APPROVED"); // APPROVED or REJECTED
        String reason = body.getOrDefault("reason", "Human approved");

        workflowService.respondToApproval(id, workspaceId, decision, reason);
        auditWorkflow("WORKFLOW_APPROVAL_RESPONDED", "WorkflowApproval", id, "decision=" + decision);
        return ResponseEntity.ok(Map.of("success", true, "status", decision));
    }

    private void auditWorkflow(String action, String entityType, String entityId, String details) {
        auditLogService.record(access.currentUser(), access.currentUser().getOrganization(), sec.currentWorkspaceId(),
                action, entityType, entityId, auditLogService.redact(details));
    }
}
