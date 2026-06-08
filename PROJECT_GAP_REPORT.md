# DolphinAI — Project Gap Report & Lockdown Blueprint

This document groups the entire DolphinAI system by implementation readiness, detailing what is finished, what is broken, what must be built, and what must be removed.

---

## 1. COMPLETED (Production Ready)

* **JWT Authentication and Multi-Tenant Workspace Login**: Secure login/registration flows with refresh token cycles.
* **Basic Workspace Configs**: Form panels for GSTIN, address, and meta configurations.
* **WebSockets telemetry stream**: STOMP client connection loops showing live trace run logs.
* **Outbound WhatsApp Template Dispatch**: Triggers message notifications via Meta's Graph API.
* **Meta Campaigns Synchronization**: Syncs basic spent, conversions, and budgets metrics.

---

## 2. PARTIALLY COMPLETE (Requires Hardening)

* **n8n Automation Tracing**: Trace lists and steps playback render in the UI, but n8n approval resume loops are completely broken.
* **Conversational Lead Qualification**: Qualifies intent from incoming SDR chats using simple keyword matching, but lacks context-aware semantic scoring.
* **AI Studio Creative Copy Builders**: Copy variations render in grids, but fall back to hardcoded Unsplash layouts when keys are absent.
* **Razorpay Wallet Recharges**: Signatures verify correctly, but lack replay prevention checks.
* **GST PDF Invoice Generation**: Tax calculations function correctly, but files are saved on the container's ephemeral local disk.

---

## 3. MISSING (Must Be Built)

* **pgvector Long-Term Memory**: Database schemas, semantic vector embeddings generation, and RAG contexts retrieval filters.
* **Subscription Plan Management**: SaaS tiers structures (Starter, Growth, Enterprise) and automated monthly billing runs.
* **Workspace Role/Permission Tables**: Multi-user permissions lists to replace single-user configs.
* **Plaintext OAuth Token Encryption**: AES-256 columns encryption routines in backend JPA entities.
* **HMAC Webhook Verification Headers**: Integrity checks on incoming WhatsApp and Lead webhooks.

---

## 4. REMOVE (Flagged for Deletion)

* **Legacy Sidebar Route `/wallet`**: Route mapping must be deleted to enforce billing isolation under `Settings -> Billing`.
* **Legacy Component Directories**:
  - `/pipeline-health`
  - `/cmo-dashboard`
  - `/executive-center`
  - `/knowledge-hub`
  *Reason*: Redundant template and code bloat that has been bypassed via routing redirects.

---

## 5. REBUILD (Critical Refactor Targets)

* **Multi-Agent Architectures**: Replace the simulated rule-based if-else stubs inside `AiceoService.java`, `ClvForecastEngine.java`, and `ChurnPredictionEngine.java` with dedicated AI CEO, CMO, Sales, CRM, Research, and Automation Agent classes with explanation layers and real contextual prompts.
* **Analytics Engine**: Replace simulated stubs in `BrainAnalyticsController.java` with database aggregation tables and dynamic Chart.js/ngx-charts integrations.
* **n8n Approval Resume Webhook**: Implement outbound callback triggers in `respondToApproval` to resume paused workflow executions in n8n.
* **WebSocket Channel Isolation**: Segment STOMP brokers to `/topic/workspace/{workspaceId}/` paths to prevent cross-tenant trace data leaks.
