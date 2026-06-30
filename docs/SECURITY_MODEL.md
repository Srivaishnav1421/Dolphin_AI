# Security Model

Backend security is authoritative. Frontend visibility is only a usability layer.

Core rules:

- JWT carries `organizationId`, `workspaceId`, and `userId`.
- `TenantContext` carries organization, workspace, and user context for request handling.
- `AccessControlService` is the standard controller boundary for RBAC and tenant access.
- Sensitive actions must create audit logs through `AuditLogService`, with redaction for secrets, credentials, tokens, and authorization data.
- Normal users must never receive provider secrets, storage paths, global platform counts, or cross-tenant aggregates.

Permission groups:

- Creative: `CREATIVE_READ`, `CREATIVE_GENERATE`, `CREATIVE_UPDATE`, `CREATIVE_DELETE`.
- Automation: `AUTOMATION_READ`, `AUTOMATION_MANAGE`.
- Analytics and reports: `ANALYTICS_READ`, `CAMPAIGN_METRICS_READ`, `REPORT_READ`, `REPORT_EXPORT`.
- Files: `FILE_READ`, `FILE_MANAGE`.
- Integrations and AI infrastructure: `INTEGRATION_READ`, `INTEGRATION_MANAGE`, `AI_PROVIDER_READ`, `AI_PROVIDER_MANAGE`, `AI_ROUTE_READ`, `AI_ROUTE_MANAGE`.

Service rules:

- Service methods that mutate or reopen tenant-owned records should accept trusted workspace scope or use scoped repository methods.
- Raw ID tampering must return `404` or `403`; it must not reveal whether a resource exists in another workspace.
- Background jobs that intentionally operate platform-wide should be clearly separated from authenticated user endpoints.
