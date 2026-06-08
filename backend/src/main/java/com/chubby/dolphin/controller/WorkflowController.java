package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.WorkflowApproval;
import com.chubby.dolphin.entity.WorkflowExecution;
import com.chubby.dolphin.repository.WorkflowApprovalRepository;
import com.chubby.dolphin.repository.WorkflowExecutionRepository;
import com.chubby.dolphin.repository.WorkflowTemplateRepository;
import com.chubby.dolphin.security.SecurityUtils;
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

    public WorkflowController(WorkflowService workflowService,
                              WorkflowExecutionRepository executionRepo,
                              WorkflowApprovalRepository approvalRepo,
                              WorkflowTemplateRepository templateRepo,
                              SecurityUtils sec) {
        this.workflowService = workflowService;
        this.executionRepo = executionRepo;
        this.approvalRepo = approvalRepo;
        this.templateRepo = templateRepo;
        this.sec = sec;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeWorkflow(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "session-" + System.currentTimeMillis());
        String userId = sec.currentUser().getId();
        String workspaceId = sec.currentWorkspaceId();

        log.info("🚀 Triggering workflow for user {} in workspace {}", userId, workspaceId);
        WorkflowExecution execution = workflowService.triggerWorkflow(userId, sessionId, message, workspaceId);
        return ResponseEntity.ok(execution);
    }

    @PostMapping("/event")
    public ResponseEntity<?> receiveEvent(@RequestBody Map<String, Object> event) {
        workflowService.processEvent(event);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<WorkflowExecution>> getExecutions() {
        String workspaceId = sec.currentWorkspaceId();
        return ResponseEntity.ok(executionRepo.findByWorkspaceId(workspaceId));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
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
        String workspaceId = sec.currentWorkspaceId();
        List<WorkflowExecution> list = executionRepo.findByTraceId(traceId);
        List<WorkflowExecution> filtered = list.stream()
                .filter(e -> workspaceId.equals(e.getWorkspaceId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/templates")
    public ResponseEntity<?> getTemplates() {
        return ResponseEntity.ok(templateRepo.findAll());
    }

    @GetMapping("/approvals")
    public ResponseEntity<List<WorkflowApproval>> getApprovals() {
        String workspaceId = sec.currentWorkspaceId();
        return ResponseEntity.ok(approvalRepo.findByWorkspaceIdAndStatus(workspaceId, "PENDING"));
    }

    @PostMapping("/approvals/{id}/respond")
    public ResponseEntity<?> respondToApproval(@PathVariable String id, @RequestBody Map<String, String> body) {
        String workspaceId = sec.currentWorkspaceId();
        Optional<WorkflowApproval> appOpt = approvalRepo.findById(id);
        if (appOpt.isEmpty()) return ResponseEntity.notFound().build();
        WorkflowApproval approval = appOpt.get();
        if (approval.getWorkspaceId() == null || !approval.getWorkspaceId().equals(workspaceId)) {
            return ResponseEntity.status(403).build();
        }

        String decision = body.getOrDefault("decision", "APPROVED"); // APPROVED or REJECTED
        String reason = body.getOrDefault("reason", "Human approved");

        workflowService.respondToApproval(id, decision, reason);
        return ResponseEntity.ok(Map.of("success", true, "status", decision));
    }
}
