# Tenant Isolation

Chubby Dolphin AI uses organization and workspace scope for business data.

Rules:

- Every business-owned table must carry workspace scope, and should carry `organization_id` when the organization can be inferred safely.
- Legacy `account_id` columns are treated as workspace identifiers until a later cleanup migration removes them.
- New migrations must use nullable-add, backfill, and index-first changes. Do not drop legacy columns in hardening sprints.
- Controllers must use `AccessControlService` before returning or mutating business data.
- Repositories should prefer scoped methods such as `findByIdAndWorkspaceId`, `findByWorkspaceId`, and org/workspace count methods.
- Raw `findById` is unsafe for tenant-owned records unless ownership has already been verified in the same call path.
- Analytics, reports, and exports must aggregate only the current workspace or current organization.
- Worker and callback paths must restore tenant scope from trusted stored records, not from frontend-supplied workspace IDs.

Current Sprint 3 migration `V35__tenant_scope_business_tables.sql` adds nullable `organization_id` columns and org/workspace indexes to remaining business tables without removing compatibility columns.
