# DolphinAI — n8n Workflow Integration Audit Report

This document audits the n8n orchestration nodes, workflow configurations, and backend event channels in DolphinAI.

---

## 1. Architectural Role: Why n8n Exists

n8n serves as the distributed task coordinator and multi-node execution layer for DolphinAI.
* **Orchestration Flow**: The Spring Boot backend initiates executions by sending payloads to n8n webhooks. n8n processes conditional nodes, calls LLM agents, triggers WhatsApp templates, and updates database records by hitting backend REST endpoints.
* **Invisible execution**: n8n acts strictly under the hood. End users interact only with the premium modular frontend dashboards. The n8n canvas and config panels are completely hidden from the user experience.

---

## 2. Active Workflow Analysis

Based on `/home/srivan/Desktop/Chubby_Dolphin_AI/artifacts/n8n/chubby_dolphin_enterprise_workflows.json`, there is currently a single active workflow:

### Workflow: `DolphinAI Main Workflow`
* **Node Inventory**:
  1. `Webhook Trigger` (POST `/webhook/execute`): Entry point that receives the user query.
  2. `Notify Workflow Started`: Makes a HTTP call back to the backend `/api/workflow/event` to transition the status of the trace run to `RUNNING`.
  3. `Select Agent` (IF conditional): Checks if the user message contains the keyword `"chat"`.
  4. `Execute Chat Agent` / `Execute Research Agent` (POST `/api/agents/run`): Triggers LLM completions inside the Spring Boot agent router.
  5. `Notify Workflow Complete`: Sends final completion payload back to `/api/workflow/event` to mark execution as `COMPLETED`.
* **Business Purpose**: Basic query routing to local Ollama agent profiles.
* **OS Vision Alignment**: **Low**. The workflow acts as a simple chat dispatcher. It does not perform actual growth automations (e.g., campaign budget scaling, pixel event logging, or CRM email flows).
* **Verdict**: **Rebuild & Expand**.
  * *Reason*: The workflow is a prototype. It must be expanded to include Meta campaign state toggles, WhatsApp follow-ups, and a true pause/resume loop for human-in-the-loop approvals.

---

## 3. Human Approval & Callback Audit

The most critical defect is in the human checkpoint approvals loop:

* **What is Broken**: In `WorkflowService.java` (line 160), `respondToApproval` processes decisions (APPROVE/REJECT) and updates the database row. However, it lacks any REST client calls to notify the n8n runner. The n8n execution node remains paused/waiting indefinitely.
* **Rebuild Action**:
  1. Configure an n8n webhook wait node inside the workflows file to register callback hooks.
  2. Add an outgoing REST client call in `WorkflowService.java` that triggers `POST http://localhost:5678/webhook/resume` with the required token parameters when a user clicks "Approve" in the dashboard.
