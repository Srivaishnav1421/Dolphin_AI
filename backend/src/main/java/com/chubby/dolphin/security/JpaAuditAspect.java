package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * JPA Audit Aspect — Automated interception of CRUD mutations on auditable entities.
 * Ensures strict compliance with enterprise security audit logging regulations.
 */
@Aspect
@Component
@Slf4j
public class JpaAuditAspect {

    private final AuditLogRepository auditLogRepo;
    private final com.chubby.dolphin.repository.UserRepository userRepo;

    @Autowired
    public JpaAuditAspect(AuditLogRepository auditLogRepo, com.chubby.dolphin.repository.UserRepository userRepo) {
        this.auditLogRepo = auditLogRepo;
        this.userRepo = userRepo;
    }

    public JpaAuditAspect(AuditLogRepository auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
        this.userRepo = null;
    }

    @AfterReturning(pointcut = "execution(* org.springframework.data.repository.CrudRepository+.save(..))", returning = "result")
    public void auditSave(Object result) {
        if (result == null) return;
        
        String resourceType = result.getClass().getSimpleName();
        if (!isAuditable(resourceType)) return;
        
        try {
            String id = getEntityId(result);
            String userEmail = getCurrentUserEmail();
            String workspaceId = getEntityWorkspaceId(result);
            String actorId = getCurrentUserId(userEmail);
            String actorType = "system-daemon".equals(userEmail) ? "SYSTEM" : "USER";
            
            AuditLog audit = new AuditLog();
            audit.setUserEmail(userEmail);
            audit.setAction("SAVE_" + resourceType.toUpperCase());
            audit.setResourceType(resourceType);
            audit.setResourceId(id);
            audit.setDetails("State mutation saved successfully for " + resourceType);
            audit.setTimestamp(LocalDateTime.now());
            audit.setIpAddress("system-local");
            
            // New audit columns
            audit.setEventType(resourceType.toUpperCase() + "_SAVE");
            audit.setWorkspaceId(workspaceId);
            audit.setCorrelationId(java.util.UUID.randomUUID().toString());
            audit.setActorId(actorId);
            audit.setActorType(actorType);
            audit.setEntityType(resourceType);
            audit.setEntityId(id);
            audit.setNewValue(result.toString());
            
            auditLogRepo.save(audit);
            log.info("📝 System Audit: Automatically logged SAVE on {} (id={}, workspaceId={}) by user={}", resourceType, id, workspaceId, userEmail);
        } catch (Exception e) {
            log.warn("Could not log save audit: {}", e.getMessage());
        }
    }

    @Before("execution(* org.springframework.data.repository.CrudRepository+.deleteById(*)) && args(id)")
    public void auditDeleteById(JoinPoint joinPoint, Object id) {
        try {
            String repoName = joinPoint.getTarget().getClass().getSimpleName();
            String resourceType = repoName.replace("Repository", "").replace("Impl", "");
            if (!isAuditable(resourceType)) return;

            String userEmail = getCurrentUserEmail();
            String actorId = getCurrentUserId(userEmail);
            String actorType = "system-daemon".equals(userEmail) ? "SYSTEM" : "USER";

            AuditLog audit = new AuditLog();
            audit.setUserEmail(userEmail);
            audit.setAction("DELETE_" + resourceType.toUpperCase());
            audit.setResourceType(resourceType);
            audit.setResourceId(id != null ? id.toString() : "unknown");
            audit.setDetails("Delete state triggered for " + resourceType);
            audit.setTimestamp(LocalDateTime.now());
            audit.setIpAddress("system-local");

            // New audit columns
            audit.setEventType(resourceType.toUpperCase() + "_DELETE");
            audit.setCorrelationId(java.util.UUID.randomUUID().toString());
            audit.setActorId(actorId);
            audit.setActorType(actorType);
            audit.setEntityType(resourceType);
            audit.setEntityId(id != null ? id.toString() : "unknown");

            auditLogRepo.save(audit);
            log.info("📝 System Audit: Automatically logged DELETE on {} (id={}) by user={}", resourceType, id, userEmail);
        } catch (Exception e) {
            log.warn("Could not log delete audit: {}", e.getMessage());
        }
    }

    private boolean isAuditable(String className) {
        return "Campaign".equalsIgnoreCase(className) ||
               "Lead".equalsIgnoreCase(className) ||
               "MetaConnection".equalsIgnoreCase(className) ||
               "WalletTransaction".equalsIgnoreCase(className) ||
               "Invoice".equalsIgnoreCase(className) ||
               "WorkflowExecution".equalsIgnoreCase(className) ||
               "WorkflowApproval".equalsIgnoreCase(className) ||
               "BrainDecision".equalsIgnoreCase(className) ||
               "BrainDecisionHistory".equalsIgnoreCase(className);
    }

    private String getEntityId(Object entity) {
        try {
            Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            return id != null ? id.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getEntityWorkspaceId(Object entity) {
        try {
            Method getWorkspaceIdMethod = entity.getClass().getMethod("getWorkspaceId");
            Object wsId = getWorkspaceIdMethod.invoke(entity);
            return wsId != null ? wsId.toString() : null;
        } catch (Exception e) {
            try {
                Method getAccountIdMethod = entity.getClass().getMethod("getAccountId");
                Object accId = getAccountIdMethod.invoke(entity);
                return accId != null ? accId.toString() : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String getCurrentUserId(String email) {
        if ("system-daemon".equals(email)) return "system";
        if (userRepo == null) return "unknown";
        return userRepo.findByEmail(email).map(com.chubby.dolphin.entity.User::getId).orElse("unknown");
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            return auth.getName();
        }
        return "system-daemon";
    }
}
