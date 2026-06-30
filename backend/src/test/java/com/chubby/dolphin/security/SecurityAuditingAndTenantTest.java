package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hibernate.Session;
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
    private Session session;

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

        when(entityManager.unwrap(Session.class)).thenReturn(session);

        // Act
        tenantAspect.setTenantSessionVariable();

        // Assert
        verify(entityManager, times(1)).unwrap(Session.class);
        verify(session, times(1)).doWork(any());
        verify(entityManager, never()).createNativeQuery(anyString());

        TenantContext.clear();
    }

    @Test
    public void testTenantConnectionAspectNoTenantFlow() {
        TenantContext.clear();

        // Act
        tenantAspect.setTenantSessionVariable();

        // Assert
        verify(entityManager, never()).createNativeQuery(anyString());
        verify(entityManager, never()).unwrap(Session.class);
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

    @Test
    public void testJpaAuditAspectRedactsEntityValues() {
        Campaign campaign = new Campaign();
        campaign.setId("camp-redacted");
        campaign.setName("password=secret token=abc123 apiKey=key123");
        campaign.setAccountId("ws-1");

        when(auditLogRepo.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jpaAuditAspect.auditSave(campaign);

        verify(auditLogRepo).save(argThat(audit ->
                audit.getNewValue() != null &&
                !audit.getNewValue().contains("secret") &&
                !audit.getNewValue().contains("abc123") &&
                !audit.getNewValue().contains("key123") &&
                audit.getNewValue().contains("entityType=Campaign")
        ));
    }
}
