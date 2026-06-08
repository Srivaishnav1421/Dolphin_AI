import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Campaign, Lead, WalletStatus, EmasMetrics,
  BrainEvent, DashboardSummary, ArbitrageResult,
  MetaConnection, BrainDecision, AdCreative, LlmProviderStatus,
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = 'http://localhost:8000';

  constructor(private http: HttpClient) {}

  // ══════════════════════════════════════════════════════════════════
  //  Dashboard
  // ══════════════════════════════════════════════════════════════════
  getDashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/api/dashboard/summary`);
  }

  // ══════════════════════════════════════════════════════════════════
  //  Campaigns
  // ══════════════════════════════════════════════════════════════════
  getCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(`${this.base}/api/campaigns`);
  }
  createCampaign(body: any): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/api/campaigns`, body);
  }
  updateCampaign(id: string, body: any): Observable<Campaign> {
    return this.http.put<Campaign>(`${this.base}/api/campaigns/${id}`, body);
  }
  deleteCampaign(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/campaigns/${id}`);
  }
  evaluateCampaign(id: string): Observable<any> {
    return this.http.post(`${this.base}/api/campaigns/${id}/evaluate`, {});
  }
  pauseCampaign(id: string): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/api/campaigns/${id}/pause`, {});
  }
  resumeCampaign(id: string): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/api/campaigns/${id}/resume`, {});
  }

  // ══════════════════════════════════════════════════════════════════
  //  Wallet
  // ══════════════════════════════════════════════════════════════════
  getWallet(): Observable<WalletStatus> {
    return this.http.get<WalletStatus>(`${this.base}/api/wallet`);
  }
  fundWallet(amount: number): Observable<any> {
    return this.http.post(`${this.base}/api/wallet/fund`, { amount });
  }
  updateWalletLimit(daily_budget_limit: number): Observable<any> {
    return this.http.put(`${this.base}/api/wallet/limit`, { daily_budget_limit });
  }
  getInvoices(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/invoices`);
  }
  generateInvoice(amount: number, transaction_id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/invoices/generate`, { amount, transaction_id });
  }

  // ══════════════════════════════════════════════════════════════════
  //  Leads
  // ══════════════════════════════════════════════════════════════════
  getLeads(status?: string): Observable<Lead[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<Lead[]>(`${this.base}/api/leads`, { params });
  }
  scoreLead(body: { name: string; message: string; source: string }): Observable<Lead> {
    return this.http.post<Lead>(`${this.base}/api/leads/score`, body);
  }
  getLeadChatHistory(leadId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/leads/${leadId}/chat`);
  }
  sendLeadChatMessage(leadId: string, message: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/${leadId}/chat`, { message });
  }
  updateLead(id: string, body: any): Observable<Lead> {
    return this.http.put<Lead>(`${this.base}/api/leads/${id}`, body);
  }

  // ══════════════════════════════════════════════════════════════════
  //  Brain / EMAS
  // ══════════════════════════════════════════════════════════════════
  getEmasMetrics(accountId: string): Observable<EmasMetrics> {
    return this.http.get<EmasMetrics>(`${this.base}/api/emas/${accountId}`);
  }
  runArbitrage(accountId: string): Observable<ArbitrageResult> {
    return this.http.post<ArbitrageResult>(`${this.base}/api/brain/arbitrage/${accountId}`, {});
  }

  // ══════════════════════════════════════════════════════════════════
  //  Brain Events
  // ══════════════════════════════════════════════════════════════════
  getRecentEvents(): Observable<BrainEvent[]> {
    return this.http.get<BrainEvent[]>(`${this.base}/api/brain/events/recent`);
  }
  getAllEvents(): Observable<BrainEvent[]> {
    return this.http.get<BrainEvent[]>(`${this.base}/api/brain/events`);
  }

  // ══════════════════════════════════════════════════════════════════
  // ══════════════════════════════════════════════════════════════════
  //  Brain Decisions (Enterprise — Approval Workflow)
  // ══════════════════════════════════════════════════════════════════
  getDecisions(): Observable<BrainDecision[]> {
    return this.http.get<BrainDecision[]>(`${this.base}/api/brain/decisions`);
  }
  getPendingDecisions(): Observable<{ pending: BrainDecision[]; count: number }> {
    return this.http.get<{ pending: BrainDecision[]; count: number }>(`${this.base}/api/brain/decisions/pending`);
  }
  approveDecision(id: string): Observable<BrainDecision> {
    return this.http.post<BrainDecision>(`${this.base}/api/brain/decisions/${id}/approve`, {});
  }
  rejectDecision(id: string): Observable<BrainDecision> {
    return this.http.post<BrainDecision>(`${this.base}/api/brain/decisions/${id}/reject`, {});
  }
  rollbackDecision(id: string): Observable<BrainDecision> {
    return this.http.post<BrainDecision>(`${this.base}/api/brain/rollback/${id}`, {});
  }
  createAbExperiment(campaignId: string, headlines: string[], bodies: string[], ctas: string[]): Observable<any> {
    return this.http.post<any>(`${this.base}/api/brain/experiments`, { campaignId, headlines, bodies, ctas });
  }

  // ══════════════════════════════════════════════════════════════════
  //  Autonomous Brain Decision Layer (Recommendations)
  // ══════════════════════════════════════════════════════════════════
  getRecommendations(): Observable<BrainDecision[]> {
    return this.http.get<BrainDecision[]>(`${this.base}/api/brain/recommendations`);
  }
  getRecommendation(id: string): Observable<BrainDecision> {
    return this.http.get<BrainDecision>(`${this.base}/api/brain/recommendations/${id}`);
  }
  evaluateRecommendations(): Observable<BrainDecision[]> {
    return this.http.post<BrainDecision[]>(`${this.base}/api/brain/recommendations/evaluate`, {});
  }
  approveRecommendation(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/brain/recommendations/${id}/approve`, {});
  }
  rejectRecommendation(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/brain/recommendations/${id}/reject`, {});
  }

  getBrainAnalytics(workspaceId?: string): Observable<any> {
    const ws = workspaceId || 'default-workspace';
    return this.http.get<any>(`${this.base}/api/brain/analytics?workspaceId=${ws}`);
  }


  // ══════════════════════════════════════════════════════════════════
  //  LLM Status
  // ══════════════════════════════════════════════════════════════════
  getLlmStatus(): Observable<LlmProviderStatus> {
    return this.http.get<LlmProviderStatus>(`${this.base}/api/brain/llm-status`);
  }

  // ══════════════════════════════════════════════════════════════════
  //  Competitor Intelligence
  // ══════════════════════════════════════════════════════════════════
  analyzeCompetitor(competitorUrl: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/brain/competitor/analyze`, { competitor_url: competitorUrl });
  }
  getCompetitorInsights(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/brain/competitor/insights`);
  }
  listExperiments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/advantage-experiments`);
  }

  // ══════════════════════════════════════════════════════════════════
  //  AI Chief Marketing Officer (CMO) Dashboard API
  // ══════════════════════════════════════════════════════════════════
  getCmoDashboard(workspaceId?: string): Observable<any> {
    const ws = workspaceId || 'default-workspace';
    return this.http.get<any>(`${this.base}/api/cmo/dashboard?workspaceId=${ws}`);
  }
  getCmoForecast(workspaceId?: string): Observable<any> {
    const ws = workspaceId || 'default-workspace';
    return this.http.get<any>(`${this.base}/api/cmo/forecast?workspaceId=${ws}`);
  }
  getCmoStrategy(workspaceId?: string): Observable<any> {
    const ws = workspaceId || 'default-workspace';
    return this.http.get<any>(`${this.base}/api/cmo/strategy?workspaceId=${ws}`);
  }
  getCmoCompetitors(workspaceId?: string): Observable<any> {
    const ws = workspaceId || 'default-workspace';
    return this.http.get<any>(`${this.base}/api/cmo/competitors?workspaceId=${ws}`);
  }
  activateExperiment(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/advantage-experiments/${id}/activate`, {});
  }

  // ══════════════════════════════════════════════════════════════════
  //  Meta Ads Integration
  // ══════════════════════════════════════════════════════════════════
  getMetaAuthUrl(): Observable<{ auth_url: string; state: string }> {
    return this.http.get<{ auth_url: string; state: string }>(`${this.base}/api/meta/auth-url`);
  }
  metaCallback(code: string, state: string): Observable<any> {
    return this.http.post(`${this.base}/api/meta/callback`, { code, state });
  }
  getMetaConnections(): Observable<MetaConnection[]> {
    return this.http.get<MetaConnection[]>(`${this.base}/api/meta/connections`);
  }
  getMetaStatus(): Observable<any> {
    return this.http.get(`${this.base}/api/meta/status`);
  }
  syncMeta(): Observable<any> {
    return this.http.post(`${this.base}/api/meta/sync`, {});
  }
  updateMetaSettings(connectionId: string, settings: any): Observable<any> {
    return this.http.put(`${this.base}/api/meta/connections/${connectionId}/settings`, settings);
  }

  // ══════════════════════════════════════════════════════════════════
  //  Creative AI
  // ══════════════════════════════════════════════════════════════════
  getCreatives(status?: string): Observable<AdCreative[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<AdCreative[]>(`${this.base}/api/creatives`, { params });
  }
  generateCreative(body: { product: string; audience: string; tone: string; platform: string; campaign_id?: string }): Observable<any> {
    return this.http.post(`${this.base}/api/creatives/generate`, body);
  }
  suggestABTests(campaignId: string): Observable<any> {
    return this.http.post(`${this.base}/api/creatives/campaign/${campaignId}/ab-suggest`, {});
  }
  rewriteCreative(id: string, platform: string): Observable<AdCreative> {
    return this.http.post<AdCreative>(`${this.base}/api/creatives/${id}/rewrite`, { platform });
  }
  updateCreativeStatus(id: string, status: string): Observable<AdCreative> {
    return this.http.put<AdCreative>(`${this.base}/api/creatives/${id}/status`, { status });
  }
  launchCreativeToPlatform(creativeId: string, platform: string, budget: number): Observable<any> {
    return this.http.post<any>(`${this.base}/api/creatives/${creativeId}/launch`, { platform, budget });
  }

  // ══════════════════════════════════════════════════════════════════
  //  Workspace Config
  // ══════════════════════════════════════════════════════════════════
  getWorkspaceConfig(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/workspace/config`);
  }
  updateWorkspaceConfig(body: any): Observable<any> {
    return this.http.put<any>(`${this.base}/api/workspace/config`, body);
  }

  // ══════════════════════════════════════════════════════════════════
  //  Admin
  // ══════════════════════════════════════════════════════════════════
  getAuditLogs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/admin/audit-logs`);
  }

  // ══════════════════════════════════════════════════════════════════
  //  Lead Pipeline Health
  // ══════════════════════════════════════════════════════════════════
  getPipelineHealth(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/leads/pipeline-health`);
  }
  simulatePipelineRun(body: { injectFailure: boolean; stage: string }): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/simulate-run`, body);
  }

  // ══════════════════════════════════════════════════════════════════
  //  AI Provider Layer
  // ══════════════════════════════════════════════════════════════════
  getAiProviders(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/brain/llm/providers`);
  }
  switchAiProvider(provider: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/brain/llm/switch`, { provider });
  }
  getAiUsageStats(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/brain/llm/usage-stats`);
  }
  getAiBudget(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/brain/llm/budgets`);
  }
  updateAiBudget(body: any): Observable<any> {
    return this.http.post<any>(`${this.base}/api/brain/llm/budgets`, body);
  }

  // ══════════════════════════════════════════════════════════════════
  //  AGOS — Growth Command Center
  // ══════════════════════════════════════════════════════════════════
  getGrowthPortfolio(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/growth/portfolio`);
  }
  getGrowthHealth(workspaceId: string): Observable<any> {
    return this.http.get<any>(`${this.base}/api/growth/health`, { params: { workspaceId } });
  }
  getGrowthChurn(workspaceId: string): Observable<any> {
    return this.http.get<any>(`${this.base}/api/growth/churn`, { params: { workspaceId } });
  }
  getGrowthClv(workspaceId: string): Observable<any> {
    return this.http.get<any>(`${this.base}/api/growth/clv`, { params: { workspaceId } });
  }
  getGrowthExecutiveSummary(workspaceId?: string): Observable<any> {
    let params: any = {};
    if (workspaceId) { params['workspaceId'] = workspaceId; }
    return this.http.get<any>(`${this.base}/api/growth/executive-summary`, { params });
  }

  // ══════════════════════════════════════════════════════════════════
  //  n8n Enterprise Workflow Orchestration
  // ══════════════════════════════════════════════════════════════════
  executeWorkflow(message: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/workflow/execute`, { message });
  }
  getWorkflowExecutions(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/workflow/executions`);
  }
  getWorkflowStats(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/workflow/stats`);
  }
  getTrace(traceId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/workflow/traces/${traceId}`);
  }
  getWorkflowTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/workflow/templates`);
  }
  getWorkflowApprovals(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/workflow/approvals`);
  }
  respondToApproval(id: string, decision: string, reason: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/workflow/approvals/${id}/respond`, { decision, reason });
  }
}
