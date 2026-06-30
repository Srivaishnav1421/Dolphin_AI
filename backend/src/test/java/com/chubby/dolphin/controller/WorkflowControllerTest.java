package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.WorkflowApproval;
import com.chubby.dolphin.entity.WorkflowExecution;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.repository.WorkflowApprovalRepository;
import com.chubby.dolphin.repository.WorkflowExecutionRepository;
import com.chubby.dolphin.repository.WorkflowTemplateRepository;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WorkflowControllerTest {

    @Mock private WorkflowService workflowService;
    @Mock private WorkflowExecutionRepository executionRepo;
    @Mock private WorkflowApprovalRepository approvalRepo;
    @Mock private WorkflowTemplateRepository templateRepo;
    @Mock private SecurityUtils sec;
    @Mock private AccessControlService access;
    @Mock private AuditLogService auditLogService;
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private WorkflowController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new WorkflowController(workflowService, executionRepo, approvalRepo, templateRepo, sec, access, auditLogService, localApprovalSafetyService);

        User mockUser = new User();
        mockUser.setId("usr-123");
        mockUser.setName("Srivan");
        mockUser.setAccountId("tenant-abc");
        mockUser.setWorkspaceId("tenant-abc");
        when(sec.currentUser()).thenReturn(mockUser);
        when(sec.currentAccountId()).thenReturn("tenant-abc");
        when(sec.currentWorkspaceId()).thenReturn("tenant-abc");
        when(access.currentUser()).thenReturn(mockUser);
        when(auditLogService.redact(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(localApprovalSafetyService.shouldRequireApprovalOnly(anyString())).thenReturn(false);
        when(localApprovalSafetyService.blockedMessage(anyString())).thenAnswer(inv -> inv.getArgument(0) + " is disabled in local approval-first mode. No external execution was performed.");
    }

    @Test
    public void testExecuteWorkflow() {
        Map<String, String> payload = new HashMap<>();
        payload.put("message", "Start research agent");

        WorkflowExecution mockExec = new WorkflowExecution();
        mockExec.setId("exec-001");
        mockExec.setStatus("RUNNING");

        when(workflowService.triggerWorkflow(eq("usr-123"), anyString(), eq("Start research agent"), eq("tenant-abc")))
                .thenReturn(mockExec);

        ResponseEntity<?> response = controller.executeWorkflow(payload);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        WorkflowExecution body = (WorkflowExecution) response.getBody();
        assertNotNull(body);
        assertEquals("exec-001", body.getId());
        assertEquals("RUNNING", body.getStatus());
    }

    @Test
    public void testExecuteWorkflowBlockedInLocalModeBeforeDispatch() {
        when(localApprovalSafetyService.shouldRequireApprovalOnly("WORKFLOW_MANUAL_EXECUTION")).thenReturn(true);

        ResponseEntity<?> response = controller.executeWorkflow(Map.of("message", "Send WhatsApp follow-up"));

        assertEquals(403, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("blocked", body.get("status"));
        assertEquals(true, body.get("approval_required"));
        assertEquals(false, body.get("external_execution_allowed"));
        verify(workflowService, never()).triggerWorkflow(anyString(), anyString(), anyString(), anyString());
        verify(localApprovalSafetyService).auditBlockedExecution(
                eq("tenant-abc"),
                eq("WORKFLOW_MANUAL_EXECUTION"),
                eq("WorkflowExecution"),
                isNull(),
                contains("before workflow row creation")
        );
    }

    @Test
    public void testGetExecutions() {
        WorkflowExecution exec1 = new WorkflowExecution();
        exec1.setId("1");
        exec1.setWorkspaceId("tenant-abc");

        when(executionRepo.findByWorkspaceId("tenant-abc")).thenReturn(List.of(exec1));

        ResponseEntity<List<WorkflowExecution>> response = controller.getExecutions();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("1", response.getBody().get(0).getId());
    }

    @Test
    public void testGetStats() {
        when(executionRepo.countByWorkspaceIdAndStatus("tenant-abc", "RUNNING")).thenReturn(2L);
        when(executionRepo.countByWorkspaceIdAndStatus("tenant-abc", "WAITING_FOR_APPROVAL")).thenReturn(1L);
        when(executionRepo.countByWorkspaceIdAndStatus("tenant-abc", "COMPLETED")).thenReturn(5L);
        when(executionRepo.countByWorkspaceIdAndStatus("tenant-abc", "FAILED")).thenReturn(1L);
        when(executionRepo.getAverageDurationByWorkspaceId("tenant-abc")).thenReturn(12500.0);

        List<Object[]> mockUsage = List.of(
                new Object[]{"ResearchAgent", 4L},
                new Object[]{"ChatAgent", 3L}
        );
        when(executionRepo.getAgentUsageStatsByWorkspaceId("tenant-abc")).thenReturn(mockUsage);

        ResponseEntity<?> response = controller.getStats();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> stats = (Map<String, Object>) response.getBody();
        assertNotNull(stats);
        assertEquals(3L, stats.get("activeCount")); // 2 RUNNING + 1 WAITING
        assertEquals(5L, stats.get("completedCount"));
        assertEquals(1L, stats.get("failedCount"));
        assertEquals(12500L, stats.get("averageDurationMs"));

        List<Map<String, Object>> agentUsage = (List<Map<String, Object>>) stats.get("agentUsage");
        assertEquals(2, agentUsage.size());
    }

    @Test
    public void testRespondToApproval() {
        Map<String, String> payload = new HashMap<>();
        payload.put("decision", "APPROVED");
        payload.put("reason", "Checks passed");

        WorkflowApproval approval = new WorkflowApproval();
        approval.setId("appr-999");
        approval.setWorkspaceId("tenant-abc");
        approval.setStatus("PENDING");

        when(approvalRepo.findByIdAndWorkspaceId("appr-999", "tenant-abc")).thenReturn(Optional.of(approval));

        ResponseEntity<?> response = controller.respondToApproval("appr-999", payload);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(workflowService, times(1)).respondToApproval("appr-999", "tenant-abc", "APPROVED", "Checks passed");
    }

    @Test
    public void testReceiveEventRejectsCrossWorkspaceExecutionId() {
        WorkflowExecution foreign = new WorkflowExecution();
        foreign.setExecutionId("exec-foreign");
        foreign.setWorkspaceId("other-ws");
        when(executionRepo.findByExecutionId("exec-foreign")).thenReturn(Optional.of(foreign));

        ResponseEntity<?> response = controller.receiveEvent(Map.of(
                "executionId", "exec-foreign",
                "eventType", "WORKFLOW_COMPLETED",
                "message", "done"
        ));

        assertEquals(404, response.getStatusCode().value());
        verify(workflowService, never()).processEvent(any());
    }

    @Test
    public void testReceiveEventBlockedInLocalModeBeforeMutation() {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setExecutionId("exec-1");
        execution.setWorkspaceId("tenant-abc");
        when(executionRepo.findByExecutionId("exec-1")).thenReturn(Optional.of(execution));
        when(localApprovalSafetyService.shouldRequireApprovalOnly("WORKFLOW_EVENT_CALLBACK")).thenReturn(true);

        ResponseEntity<?> response = controller.receiveEvent(Map.of(
                "executionId", "exec-1",
                "eventType", "WORKFLOW_COMPLETED",
                "message", "done"
        ));

        assertEquals(403, response.getStatusCode().value());
        verify(workflowService, never()).processEvent(any());
        verify(localApprovalSafetyService).auditBlockedExecution(
                eq("tenant-abc"),
                eq("WORKFLOW_EVENT_CALLBACK"),
                eq("WorkflowExecution"),
                eq("exec-1"),
                contains("before external workflow callback mutation")
        );
    }
}
