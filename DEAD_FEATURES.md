# DolphinAI — Dead & Mocked Feature Inventory

This document identifies orphaned code segments, mock templates, placeholder services, and duplicated logic patterns in the DolphinAI repository.

---

## 1. Code-Only Features (No UI Bindings)

* **Wallet Module (`/wallet` Route & Components)**: The credit recharge checkouts and transaction ledger components exist in the frontend filesystem but are not bound to any link in the sidebar menu.
* **Advantage+ Split Grids (`AdvantageExperimentController.java` & `AdvantageIntelligenceController.java`)**: The backend possesses database tables and REST controllers to fetch Advantage+ ads experiments, but the Campaigns page has no charts or views to display them.
* **Admin Logs / Diagnostics (`AdminController.java`)**: Implements endpoints to download container logs and trigger artificial test leads, but no administration panel exists in the UI.

---

## 2. UI-Only Features (No Backend / Database Support)

* **Analytics Dashboards (`analytics.html`, `analytics.ts`)**: The charts and performance grids exist in the UI but bind directly to static mockup metrics. The backend (`BrainAnalyticsController.java`) contains no analytics aggregation jobs, retention metrics tables, or calculation pipelines.

---

## 3. Heuristic Mock & Simulated AI Systems

* **AI CEO Strategy (`AiceoService.java`)**: Recommended actions and growth guides are hardcoded template strings loaded via `if-else` loops on health levels.
* **Strategic Long-Term Memory (`CmoMemoryService.java`)**: Returns hardcoded JSON lists of winning audiences (e.g., "Lookalike VIP [IN, 1%]") and creatives when the relational table has no records.
* **CLV Predictions (`ClvForecastEngine.java`)**: Evaluates predicted CLV using a static hardcoded multiplier `predictedClv = currentClv * 1.18`.
* **Churn Risks (`ChurnPredictionEngine.java`)**: Calculates churn weights arithmetically by adding fixed values (e.g., `+35` if zero active campaigns).
* **Creative Banners Fallback (`CreativeController.java`)**: Returns static Unsplash URLs if OpenAI credentials are not provided.

---

## 4. Placeholder Code Blocks

* **Diagnostics Controller (`DiagnosticsController.java`)**: Employs mock methods to trigger mock system memory alerts and mock workflow updates.
* **Ad-Archive Search (`CompetitorSpyService.java`)**: If Meta API tokens are missing, it falls back to generating mock competitor ad variations via local LLM instead of making a live Ads Archive API request.

---

## 5. Duplicate & Orphaned Logic

* **Ollama Client Implementations**: Separate, duplicate HTTP clients and configurations inside `AgentRuntimeService` and `LlmRouterService` execute daemon prompts, creating configuration overhead.
* **Redundant Frontend Components**: The folders `/pipeline-health`, `/cmo-dashboard`, `/executive-center`, and `/knowledge-hub` are legacy views. Their routes redirect to `/dashboard` or `/ad-brain` in `app.routes.ts`, meaning these directories contain duplicate/dead templates and code.
