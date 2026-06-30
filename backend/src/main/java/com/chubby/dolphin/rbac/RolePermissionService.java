package com.chubby.dolphin.rbac;

import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

@Service
public class RolePermissionService {

    private static final EnumMap<Role, Set<Permission>> PERMISSIONS = new EnumMap<>(Role.class);

    static {
        Set<Permission> all = EnumSet.allOf(Permission.class);
        PERMISSIONS.put(Role.SYSTEM_ADMIN, all);
        PERMISSIONS.put(Role.PLATFORM_ADMIN, all);

        EnumSet<Permission> orgOwner = EnumSet.of(
                Permission.VIEW_ORGANIZATION, Permission.VIEW_WORKSPACE, Permission.CREATE_WORKSPACE,
                Permission.MANAGE_WORKSPACE, Permission.VIEW_MEMBERS, Permission.MANAGE_MEMBERS,
                Permission.SWITCH_WORKSPACE, Permission.VIEW_AUDIT_LOGS,
                Permission.WORKSPACE_READ, Permission.WORKSPACE_MANAGE, Permission.MEMBER_READ,
                Permission.MEMBER_MANAGE, Permission.AUDIT_READ,
                Permission.LEAD_READ, Permission.LEAD_CREATE, Permission.LEAD_UPDATE, Permission.LEAD_DELETE,
                Permission.LEAD_ASSIGN, Permission.LEAD_SCORE, Permission.LEAD_ACTIVITY_ADD,
                Permission.CAMPAIGN_READ, Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_UPDATE,
                Permission.CAMPAIGN_DELETE, Permission.CAMPAIGN_PAUSE, Permission.CAMPAIGN_RESUME,
                Permission.CAMPAIGN_APPROVE_AI_ACTION, Permission.CAMPAIGN_METRICS_READ,
                Permission.MATH_ENGINE_RUN,
                Permission.CREATIVE_READ, Permission.CREATIVE_GENERATE, Permission.CREATIVE_UPDATE,
                Permission.CREATIVE_DELETE, Permission.AUTOMATION_READ, Permission.AUTOMATION_MANAGE,
                Permission.AD_BRAIN_READ, Permission.AD_BRAIN_RUN,
                Permission.APPROVAL_READ, Permission.APPROVAL_MANAGE, Permission.APPROVAL_EXECUTE,
                Permission.ANALYTICS_READ, Permission.REPORT_READ, Permission.REPORT_EXPORT,
                Permission.FILE_READ, Permission.FILE_MANAGE, Permission.NOTIFICATION_READ,
                Permission.TASK_READ, Permission.TASK_MANAGE,
                Permission.INTEGRATION_READ, Permission.INTEGRATION_MANAGE,
                Permission.AI_PROVIDER_READ, Permission.AI_PROVIDER_MANAGE,
                Permission.AI_ROUTE_READ, Permission.AI_ROUTE_MANAGE,
                Permission.BILLING_READ, Permission.BILLING_MANAGE, Permission.INVOICE_READ,
                Permission.WALLET_READ, Permission.WALLET_MANAGE, Permission.SUBSCRIPTION_MANAGE
        );
        PERMISSIONS.put(Role.ORG_OWNER, orgOwner);
        PERMISSIONS.put(Role.OWNER, orgOwner);

        PERMISSIONS.put(Role.ORG_ADMIN, EnumSet.of(
                Permission.VIEW_ORGANIZATION,
                Permission.VIEW_WORKSPACE,
                Permission.CREATE_WORKSPACE,
                Permission.MANAGE_WORKSPACE,
                Permission.VIEW_MEMBERS,
                Permission.MANAGE_MEMBERS,
                Permission.SWITCH_WORKSPACE,
                Permission.VIEW_AUDIT_LOGS,
                Permission.WORKSPACE_READ, Permission.WORKSPACE_MANAGE, Permission.MEMBER_READ,
                Permission.MEMBER_MANAGE, Permission.AUDIT_READ,
                Permission.LEAD_READ, Permission.LEAD_CREATE, Permission.LEAD_UPDATE, Permission.LEAD_DELETE,
                Permission.LEAD_ASSIGN, Permission.LEAD_SCORE, Permission.LEAD_ACTIVITY_ADD,
                Permission.CAMPAIGN_READ, Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_UPDATE,
                Permission.CAMPAIGN_DELETE, Permission.CAMPAIGN_PAUSE, Permission.CAMPAIGN_RESUME,
                Permission.CAMPAIGN_APPROVE_AI_ACTION, Permission.CAMPAIGN_METRICS_READ,
                Permission.MATH_ENGINE_RUN,
                Permission.CREATIVE_READ, Permission.CREATIVE_GENERATE, Permission.CREATIVE_UPDATE,
                Permission.CREATIVE_DELETE, Permission.AUTOMATION_READ, Permission.AUTOMATION_MANAGE,
                Permission.AD_BRAIN_READ, Permission.AD_BRAIN_RUN,
                Permission.APPROVAL_READ, Permission.APPROVAL_MANAGE, Permission.APPROVAL_EXECUTE,
                Permission.ANALYTICS_READ, Permission.REPORT_READ, Permission.REPORT_EXPORT,
                Permission.FILE_READ, Permission.FILE_MANAGE, Permission.NOTIFICATION_READ,
                Permission.TASK_READ, Permission.TASK_MANAGE,
                Permission.INTEGRATION_READ, Permission.INTEGRATION_MANAGE,
                Permission.AI_PROVIDER_READ, Permission.AI_ROUTE_READ,
                Permission.BILLING_READ, Permission.BILLING_MANAGE, Permission.INVOICE_READ,
                Permission.WALLET_READ, Permission.WALLET_MANAGE, Permission.SUBSCRIPTION_MANAGE
        ));
        PERMISSIONS.put(Role.ADMIN, PERMISSIONS.get(Role.ORG_ADMIN));

        EnumSet<Permission> manager = EnumSet.of(
                Permission.VIEW_ORGANIZATION,
                Permission.VIEW_WORKSPACE,
                Permission.VIEW_MEMBERS,
                Permission.SWITCH_WORKSPACE,
                Permission.WORKSPACE_READ, Permission.MEMBER_READ,
                Permission.LEAD_READ, Permission.LEAD_CREATE, Permission.LEAD_UPDATE,
                Permission.LEAD_ASSIGN, Permission.LEAD_SCORE, Permission.LEAD_ACTIVITY_ADD,
                Permission.CAMPAIGN_READ, Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_UPDATE,
                Permission.CAMPAIGN_PAUSE, Permission.CAMPAIGN_RESUME, Permission.CAMPAIGN_METRICS_READ,
                Permission.MATH_ENGINE_RUN,
                Permission.CREATIVE_READ, Permission.CREATIVE_GENERATE, Permission.CREATIVE_UPDATE,
                Permission.AUTOMATION_READ, Permission.AUTOMATION_MANAGE,
                Permission.AD_BRAIN_READ, Permission.AD_BRAIN_RUN,
                Permission.APPROVAL_READ, Permission.APPROVAL_MANAGE,
                Permission.ANALYTICS_READ, Permission.REPORT_READ, Permission.REPORT_EXPORT,
                Permission.FILE_READ, Permission.FILE_MANAGE, Permission.NOTIFICATION_READ,
                Permission.TASK_READ, Permission.TASK_MANAGE, Permission.INTEGRATION_READ,
                Permission.AI_PROVIDER_READ, Permission.AI_ROUTE_READ,
                Permission.INVOICE_READ, Permission.WALLET_READ
        );
        PERMISSIONS.put(Role.WORKSPACE_MANAGER, manager);
        PERMISSIONS.put(Role.MANAGER, manager);

        PERMISSIONS.put(Role.MARKETER, EnumSet.of(
                Permission.VIEW_ORGANIZATION, Permission.VIEW_WORKSPACE, Permission.SWITCH_WORKSPACE,
                Permission.WORKSPACE_READ, Permission.LEAD_READ, Permission.LEAD_CREATE,
                Permission.LEAD_UPDATE, Permission.LEAD_SCORE, Permission.LEAD_ACTIVITY_ADD,
                Permission.CAMPAIGN_READ, Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_UPDATE,
                Permission.CAMPAIGN_PAUSE, Permission.CAMPAIGN_RESUME, Permission.CAMPAIGN_METRICS_READ,
                Permission.MATH_ENGINE_RUN,
                Permission.CREATIVE_READ, Permission.CREATIVE_GENERATE, Permission.CREATIVE_UPDATE,
                Permission.AD_BRAIN_READ, Permission.AD_BRAIN_RUN,
                Permission.APPROVAL_READ, Permission.APPROVAL_MANAGE,
                Permission.ANALYTICS_READ, Permission.REPORT_READ, Permission.REPORT_EXPORT,
                Permission.FILE_READ, Permission.AUTOMATION_READ, Permission.INTEGRATION_READ, Permission.INVOICE_READ,
                Permission.WALLET_READ
        ));

        PERMISSIONS.put(Role.SALES_AGENT, EnumSet.of(
                Permission.VIEW_ORGANIZATION, Permission.VIEW_WORKSPACE, Permission.SWITCH_WORKSPACE,
                Permission.WORKSPACE_READ, Permission.LEAD_READ, Permission.LEAD_CREATE,
                Permission.LEAD_UPDATE, Permission.LEAD_ACTIVITY_ADD, Permission.CAMPAIGN_READ,
                Permission.APPROVAL_READ, Permission.ANALYTICS_READ, Permission.TASK_READ,
                Permission.TASK_MANAGE, Permission.NOTIFICATION_READ
        ));

        PERMISSIONS.put(Role.EMPLOYEE, EnumSet.of(
                Permission.VIEW_ORGANIZATION,
                Permission.VIEW_WORKSPACE,
                Permission.SWITCH_WORKSPACE,
                Permission.WORKSPACE_READ,
                Permission.LEAD_READ,
                Permission.LEAD_UPDATE,
                Permission.LEAD_ACTIVITY_ADD,
                Permission.CAMPAIGN_READ,
                Permission.CREATIVE_READ,
                Permission.AUTOMATION_READ,
                Permission.AD_BRAIN_READ,
                Permission.APPROVAL_READ,
                Permission.ANALYTICS_READ,
                Permission.REPORT_READ,
                Permission.FILE_READ,
                Permission.NOTIFICATION_READ,
                Permission.TASK_READ
        ));
        PERMISSIONS.put(Role.AGENT, PERMISSIONS.get(Role.SALES_AGENT));
        PERMISSIONS.put(Role.CLIENT, EnumSet.of(
                Permission.VIEW_ORGANIZATION, Permission.VIEW_WORKSPACE, Permission.SWITCH_WORKSPACE,
                Permission.WORKSPACE_READ, Permission.LEAD_READ, Permission.CAMPAIGN_READ,
                Permission.CAMPAIGN_METRICS_READ, Permission.CREATIVE_READ, Permission.AUTOMATION_READ,
                Permission.AD_BRAIN_READ,
                Permission.APPROVAL_READ, Permission.ANALYTICS_READ, Permission.REPORT_READ, Permission.FILE_READ,
                Permission.NOTIFICATION_READ, Permission.INVOICE_READ, Permission.WALLET_READ
        ));
        EnumSet<Permission> viewer = EnumSet.of(
                Permission.VIEW_ORGANIZATION,
                Permission.VIEW_WORKSPACE,
                Permission.SWITCH_WORKSPACE,
                Permission.WORKSPACE_READ,
                Permission.LEAD_READ,
                Permission.CAMPAIGN_READ,
                Permission.CAMPAIGN_METRICS_READ,
                Permission.CREATIVE_READ,
                Permission.AUTOMATION_READ,
                Permission.AD_BRAIN_READ,
                Permission.APPROVAL_READ,
                Permission.ANALYTICS_READ,
                Permission.REPORT_READ,
                Permission.FILE_READ,
                Permission.NOTIFICATION_READ,
                Permission.INVOICE_READ,
                Permission.WALLET_READ
        );
        PERMISSIONS.put(Role.VIEWER, viewer);
        PERMISSIONS.put(Role.CLIENT_VIEWER, viewer);
    }

    public boolean hasPermission(String role, Permission permission) {
        return PERMISSIONS.getOrDefault(Role.from(role), PERMISSIONS.get(Role.VIEWER)).contains(permission);
    }

    public boolean hasPermission(String role, String permission) {
        return hasPermission(role, Permission.fromCode(permission));
    }
}
