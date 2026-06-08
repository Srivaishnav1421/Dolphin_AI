package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SecurityAuditingAndTenantTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private AuditLogRepository auditLogRepo;

    private TenantConnectionAspect tenantAspect;
    private JpaAuditAspect jpaAuditAspect;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tenantAspect = new TenantConnectionAspect();
        ReflectionTestUtils.setField(tenantAspect, "entityManager", entityManager);
        ReflectionTestUtils.setField(tenantAspect, "isPostgres", true);

        jpaAuditAspect = new JpaAuditAspect(auditLogRepo);
    }

    @Test
    public void testTenantConnectionAspectFlow() {
        String mockTenantId = "tenant-workspace-999";
        TenantContext.setCurrentTenant(mockTenantId);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("tenantId"), eq(mockTenantId))).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        // Act
        tenantAspect.setTenantSessionVariable();

        // Assert
        verify(entityManager, times(1)).createNativeQuery("SET LOCAL app.workspace_id = :tenantId");
        verify(query, times(1)).setParameter("tenantId", mockTenantId);
        verify(query, times(1)).executeUpdate();

        TenantContext.clear();
    }

    @Test
    public void testTenantConnectionAspectNoTenantFlow() {
        TenantContext.clear();

        // Act
        tenantAspect.setTenantSessionVariable();

        // Assert
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    public void testJpaAuditAspectSaveFlow() {
        Campaign campaign = new Campaign();
        campaign.setId("camp-123");
        campaign.setName("Autumn Campaign");

        when(auditLogRepo.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        jpaAuditAspect.auditSave(campaign);

        // Assert
        verify(auditLogRepo, times(1)).save(argThat(audit -> 
            "SAVE_CAMPAIGN".equals(audit.getAction()) &&
            "Campaign".equals(audit.getResourceType()) &&
            "camp-123".equals(audit.getResourceId())
        ));
    }

    @Test
    public void testJpaAuditAspectSaveNonAuditableFlow() {
        // Simple String is not auditable
        String nonAuditable = "NotAuditable";

        // Act
        jpaAuditAspect.auditSave(nonAuditable);

        // Assert
        verify(auditLogRepo, never()).save(any(AuditLog.class));
    }
}
