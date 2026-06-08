# DolphinAI — Master Build & Stabilization Roadmap

This document outlines the chronological development steps required to move DolphinAI from a mock prototype to a production-ready AI-Powered Growth Operating System.

---

## Step 1: Core UI Foundation
* **Tasks**:
  1. Delete legacy folders: `/pipeline-health`, `/cmo-dashboard`, `/executive-center`, `/knowledge-hub` to clean up codebase bloat.
  2. Remove deprecated routes (e.g. `/wallet` sidebar mapping in `shell.ts`).
  3. Ensure only the 8 mandated modules exist in sidebar.
* **Exit Criteria**: No console compiler errors, clean folder layouts.

---

## Step 2: Theme System
* **Tasks**:
  1. Verify theme persistence in `localStorage.setItem('dolphin-theme')` with Light, Dark, and System states.
  2. Ensure all UI elements use Geist/Inter typography.
  3. Remove any glowing borders, neon components, or cyber-themed templates.
* **Exit Criteria**: Theme persists on reload; color variables strictly match grayscale and blue primary (`#2563EB`).

---

## Step 3: Dashboard
* **Tasks**:
  1. Refactor dashboard tiles to display: Revenue (Today, Month, Forecast), Lead Metrics (New, Qualified, Conversion %), and Marketing Stats (Ad Spend, ROAS, Active Campaigns).
  2. Integrate workspace health gauges and live status logs.
  3. Bind WebSocket STOMP listener to system connectivity badges.
* **Exit Criteria**: Metrics reflect real database counts; approvals trigger backend updates.

---

## Step 4: CRM
* **Tasks**:
  1. Refactor CRM list layout to show a visual Kanban pipeline (New, Contacted, Qualified, Proposal, Won, Lost).
  2. Map activity logs to store emails, calls, and meeting logs in DB.
  3. Ensure inbound leads populate qualifying scores automatically.
* **Exit Criteria**: Interactive Kanban board tracks lead conversions; SDR qualifications chat updates fields.

---

## Step 5: Campaign Manager
* **Tasks**:
  1. Optimize campaign synchronization task, replacing full database table scans with index-based account queries.
  2. Calculate and display CPC (Cost per Click) and CPL (Cost per Lead) dynamically.
  3. Validate Meta Graph API triggers for budget scales.
* **Exit Criteria**: Budget increments modify meta campaigns; sync tasks execute under 100ms.

---

## Step 6: AI Brain
* **Tasks**:
  1. Replace Simulated Strategic engines with structured logic calculations.
  2. Add expected impact indicators, confidence scoring, and explanation trails to every recommendation.
  3. Build an auditable decision rollback history system.
* **Exit Criteria**: Recommendations detail Why, Evidence, Confidence, and Impact.

---

## Step 7: Automation
* **Tasks**:
  1. Implement n8n workflow execution callback resume hook.
  2. Fix `WorkflowService.respondToApproval` to trigger outbound webhooks to resume n8n execution nodes.
  3. Map status values (RUNNING, COMPLETED, FAILED, WAITING_FOR_APPROVAL).
* **Exit Criteria**: Approving a decision resumes the n8n execution thread automatically.

---

## Step 8: Analytics
* **Tasks**:
  1. Rebuild analytics controllers to aggregate database stats.
  2. Integrate Dynamic charts in the frontend Analytics section.
  3. Compile executive reports periodically.
* **Exit Criteria**: Analytics page pulls real data from aggregation tables.

---

## Step 9: Billing
* **Tasks**:
  1. Prevent Razorpay transaction replay attacks.
  2. Map subscription plan tiers (Starter, Growth, Enterprise).
  3. Move Billing and Wallet modules inside `Settings -> Billing`.
* **Exit Criteria**: Wallet recharge, invoicing, and plan selections are fully operational under Settings.

---

## Step 10: Memory Layer
* **Tasks**:
  1. Create Flyway migrations for pgvector setup.
  2. Connect Spring Boot to pgvector embeddings indices.
  3. Implement RAG retrieval contexts for campaign history and lead records.
* **Exit Criteria**: Vector database queries successfully retrieve relevant context documents.

---

## Step 11: Multi-Agent System
* **Tasks**:
  1. Write separate CEO, CMO, Sales, CRM, Research, and Automation Agent classes.
  2. Ensure each agent operates within specific boundaries with independent context memory.
  3. Record detailed agent execution audit trails.
* **Exit Criteria**: Actions are logged with transparent multi-agent reasoning logs.

---

## Step 12: Production Hardening
* **Tasks**:
  1. Implement Meta webhook signature HMAC validation (`X-Hub-Signature-256`).
  2. Restrict WebSocket STOMP channels by workspace ID.
  3. Encrypt access tokens in database records.
  4. Relocate GST invoices to secure S3 storage.
* **Exit Criteria**: Security audit passes with zero plaintext credentials or spoofable webhooks.
