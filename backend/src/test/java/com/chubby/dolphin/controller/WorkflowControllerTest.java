package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.WorkflowApproval;
import com.chubby.dolphin.entity.WorkflowExecution;
import com.chubby.dolphin.repository.WorkflowApprovalRepository;
import com.chubby.dolphin.repository.WorkflowExecutionRepository;
import com.chubby.dolphin.repository.WorkflowTemplateRepository;
import com.chubby.dolphin.security.SecurityUtils;
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

    private WorkflowController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new WorkflowController(workflowService, executionRepo, approvalRepo, templateRepo, sec);

        User mockUser = new User();
        mockUser.setId("usr-123");
        mockUser.setName("Srivan");
        mockUser.setAccountId("tenant-abc");
        mockUser.setWorkspaceId("tenant-abc");
        when(sec.currentUser()).thenReturn(mockUser);
        when(sec.currentAccountId()).thenReturn("tenant-abc");
        when(sec.currentWorkspaceId()).thenReturn("tenant-abc");
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

        when(approvalRepo.findById("appr-999")).thenReturn(Optional.of(approval));

        ResponseEntity<?> response = controller.respondToApproval("appr-999", payload);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(workflowService, times(1)).respondToApproval("appr-999", "APPROVED", "Checks passed");
    }
}
