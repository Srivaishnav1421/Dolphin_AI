import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from './runtime-config.service';
import { AuthService } from './auth.service';
import {
  Campaign, Lead, WalletStatus, EmasMetrics,
  BrainEvent, DashboardSummary, ArbitrageResult,
  MetaConnection, BrainDecision, AdCreative, LlmProviderStatus,
  MarketingForm, LandingPage, FormSubmission, ApprovalItem,
  AdBrainRunResult, AdBrainSignal, CampaignMathEvaluation,
  ContentFactoryItem, AnalyticsSummary,
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base: string;

  constructor(private http: HttpClient, config: RuntimeConfigService, private auth: AuthService) {
    this.base = config.apiBase;
  }

  // ══════════════════════════════════════════════════════════════════
  //  Dashboard
  // ══════════════════════════════════════════════════════════════════
  getDashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/api/dashboard/summary`);
  }

  getRuntimeIdentity(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/system/runtime`);
  }

  getAnalyticsSummary(): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>(`${this.base}/api/analytics/summary`);
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
    return this.http.post(`${this.base}/api/math-engine/campaigns/${id}/evaluate`, {});
  }
  getCampaignMathScore(id: string): Observable<CampaignMathEvaluation> {
    return this.http.get<CampaignMathEvaluation>(`${this.base}/api/campaigns/${id}/math-score`);
  }
  pauseCampaign(id: string): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/api/campaigns/${id}/pause`, {});
  }
  resumeCampaign(id: string): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/api/campaigns/${id}/resume`, {});
  }

  // ══════════════════════════════════════════════════════════════════
  //  Marketing Engine: landing pages, forms, submissions, analytics
  // ══════════════════════════════════════════════════════════════════
  getMarketingTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/marketing/templates`);
  }
  getMarketingForms(): Observable<MarketingForm[]> {
    return this.http.get<MarketingForm[]>(`${this.base}/api/marketing/forms`);
  }
  createMarketingForm(body: Partial<MarketingForm>): Observable<MarketingForm> {
    return this.http.post<MarketingForm>(`${this.base}/api/marketing/forms`, body);
  }
  updateMarketingForm(id: string, body: Partial<MarketingForm>): Observable<MarketingForm> {
    return this.http.put<MarketingForm>(`${this.base}/api/marketing/forms/${id}`, body);
  }
  getLandingPages(): Observable<LandingPage[]> {
    return this.http.get<LandingPage[]>(`${this.base}/api/marketing/landing-pages`);
  }
  createLandingPage(body: Partial<LandingPage>): Observable<LandingPage> {
    return this.http.post<LandingPage>(`${this.base}/api/marketing/landing-pages`, body);
  }
  updateLandingPage(id: string, body: Partial<LandingPage>): Observable<LandingPage> {
    return this.http.put<LandingPage>(`${this.base}/api/marketing/landing-pages/${id}`, body);
  }
  getFormSubmissions(): Observable<FormSubmission[]> {
    return this.http.get<FormSubmission[]>(`${this.base}/api/marketing/submissions`);
  }
  getMarketingAnalytics(params?: { campaignId?: string; landingPageId?: string; formId?: string }): Observable<any> {
    let httpParams = new HttpParams();
    if (params?.campaignId) httpParams = httpParams.set('campaignId', params.campaignId);
    if (params?.landingPageId) httpParams = httpParams.set('landingPageId', params.landingPageId);
    if (params?.formId) httpParams = httpParams.set('formId', params.formId);
    return this.http.get<any>(`${this.base}/api/marketing/analytics`, { params: httpParams });
  }
  submitPublicForm(workspaceId: string, formId: string, body: any, landingPageId?: string): Observable<any> {
    let params = new HttpParams();
    if (landingPageId) params = params.set('landingPageId', landingPageId);
    return this.http.post<any>(`${this.base}/api/public/forms/${workspaceId}/${formId}/submit`, body, { params });
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
  createPaymentOrder(amount: number): Observable<any> {
    return this.http.post<any>(`${this.base}/api/payment/order`, { amount });
  }
  getPaymentConfig(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/payment/config`);
  }
  verifyPayment(body: {
    razorpay_payment_id: string;
    razorpay_order_id: string;
    razorpay_signature: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.base}/api/payment/verify`, body);
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
  createLead(body: Partial<Lead> & Record<string, unknown>): Observable<Lead> {
    return this.http.post<Lead>(`${this.base}/api/leads`, body);
  }
  getLead(id: string): Observable<Lead> {
    return this.http.get<Lead>(`${this.base}/api/leads/${id}`);
  }
  scoreExistingLead(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/${id}/score`, {});
  }
  recommendLeadNextAction(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/${id}/recommend-next-action`, {});
  }
  submitLeadFollowupApproval(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/${id}/submit-followup-approval`, {});
  }
  updateLeadStatus(id: string, body: { status?: string; pipeline_stage?: string }): Observable<Lead> {
    return this.http.patch<Lead>(`${this.base}/api/leads/${id}/status`, body);
  }
  deleteLead(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/leads/${id}`);
  }
  getLeadChatHistory(leadId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/leads/${leadId}/chat`);
  }
  sendLeadChatMessage(leadId: string, message: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/${leadId}/chat`, { message });
  }
  getLeadInteractions(leadId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/leads/${leadId}/interactions`);
  }
  addLeadInteraction(leadId: string, body: { type: string; channel: string; notes: string }): Observable<any> {
    return this.http.post<any>(`${this.base}/api/leads/${leadId}/interactions`, body);
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

  // ══════════════════════════════════════════════════════════════════
  //  Global Approval Queue
  // ══════════════════════════════════════════════════════════════════
  getApprovals(): Observable<ApprovalItem[]> {
    return this.http.get<ApprovalItem[]>(`${this.base}/api/approvals`);
  }
  getPendingApprovals(): Observable<ApprovalItem[]> {
    return this.http.get<ApprovalItem[]>(`${this.base}/api/approvals/pending`);
  }
  createApprovalItem(body: Partial<ApprovalItem> & Record<string, unknown>): Observable<ApprovalItem> {
    return this.http.post<ApprovalItem>(`${this.base}/api/approvals`, body);
  }
  approveApprovalItem(id: string): Observable<ApprovalItem> {
    return this.http.post<ApprovalItem>(`${this.base}/api/approvals/${id}/approve`, {});
  }
  rejectApprovalItem(id: string, reason = 'Rejected from Growth Home'): Observable<ApprovalItem> {
    return this.http.post<ApprovalItem>(`${this.base}/api/approvals/${id}/reject`, { reason });
  }
  executeApprovalItem(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/approvals/${id}/execute`, {});
  }

  // ══════════════════════════════════════════════════════════════════
  //  Ad Brain Manual Run
  // ══════════════════════════════════════════════════════════════════
  runAdBrain(): Observable<AdBrainRunResult> {
    return this.http.post<AdBrainRunResult>(`${this.base}/api/ad-brain/run`, {});
  }
  getAdBrainStatus(): Observable<AdBrainRunResult | null> {
    return this.http.get<AdBrainRunResult | null>(`${this.base}/api/ad-brain/status`);
  }
  getAdBrainRuns(): Observable<AdBrainRunResult[]> {
    return this.http.get<AdBrainRunResult[]>(`${this.base}/api/ad-brain/runs`);
  }
  getAdBrainRun(id: string): Observable<AdBrainRunResult> {
    return this.http.get<AdBrainRunResult>(`${this.base}/api/ad-brain/runs/${id}`);
  }
  getLatestAdBrainSignals(): Observable<AdBrainSignal[]> {
    return this.http.get<AdBrainSignal[]>(`${this.base}/api/ad-brain/signals/latest`);
  }
  getAdBrainEvaluations(runId?: string): Observable<CampaignMathEvaluation[]> {
    const params = runId ? new HttpParams().set('runId', runId) : undefined;
    return this.http.get<CampaignMathEvaluation[]>(`${this.base}/api/ad-brain/evaluations`, { params });
  }
  getAdBrainEvaluation(id: string): Observable<CampaignMathEvaluation> {
    return this.http.get<CampaignMathEvaluation>(`${this.base}/api/ad-brain/evaluations/${id}`);
  }

  getBrainAnalytics(workspaceId?: string): Observable<any> {
    const ws = this.workspaceId(workspaceId);
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
    const ws = this.workspaceId(workspaceId);
    return this.http.get<any>(`${this.base}/api/cmo/dashboard?workspaceId=${ws}`);
  }
  getCmoForecast(workspaceId?: string): Observable<any> {
    const ws = this.workspaceId(workspaceId);
    return this.http.get<any>(`${this.base}/api/cmo/forecast?workspaceId=${ws}`);
  }
  getCmoStrategy(workspaceId?: string): Observable<any> {
    const ws = this.workspaceId(workspaceId);
    return this.http.get<any>(`${this.base}/api/cmo/strategy?workspaceId=${ws}`);
  }
  getCmoCompetitors(workspaceId?: string): Observable<any> {
    const ws = this.workspaceId(workspaceId);
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
  getIntegrationStatus(): Observable<Record<string, any>> {
    return this.http.get<Record<string, any>>(`${this.base}/api/integrations/status`);
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
  generateCreative(body: { product: string; audience: string; tone: string; platform: string; campaign_id?: string; quality_tier?: string; language_code?: string }): Observable<any> {
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
  //  Content Factory
  // ══════════════════════════════════════════════════════════════════
  generateContentFactory(body: {
    business_name: string;
    product_service: string;
    target_audience: string;
    location?: string;
    offer?: string;
    tone: string;
    language: string;
    channel: string;
    goal: string;
    cta_style: string;
    content_type: string;
  }): Observable<ContentFactoryItem> {
    return this.http.post<ContentFactoryItem>(`${this.base}/api/content-factory/generate`, body);
  }
  getContentFactoryItems(): Observable<ContentFactoryItem[]> {
    return this.http.get<ContentFactoryItem[]>(`${this.base}/api/content-factory/items`);
  }
  getContentFactoryItem(id: string): Observable<ContentFactoryItem> {
    return this.http.get<ContentFactoryItem>(`${this.base}/api/content-factory/items/${id}`);
  }
  submitContentFactoryVariantApproval(id: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/content-factory/variants/${id}/submit-approval`, {});
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
  getWorkspaceTeam(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/workspace/team`);
  }
  updateWorkspaceTeamRole(userId: string, role: string): Observable<any> {
    return this.http.put<any>(`${this.base}/api/workspace/team/${userId}/role`, { role });
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
    return this.http.get<any>(`${this.base}/api/admin/ai-infrastructure/providers`);
  }
  switchAiProvider(provider: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/admin/ai-infrastructure/switch`, { provider });
  }
  getAiUsageStats(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/admin/ai-infrastructure/usage-stats`);
  }
  getAiBudget(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/admin/ai-infrastructure/budgets`);
  }
  updateAiBudget(body: any): Observable<any> {
    return this.http.post<any>(`${this.base}/api/admin/ai-infrastructure/budgets`, body);
  }
  getAiRouting(): Observable<any> {
    return this.http.get<any>(`${this.base}/api/admin/ai-infrastructure/routing`);
  }
  updateDefaultAiProvider(provider: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/admin/ai-infrastructure/routing/default`, { provider });
  }
  updateAiTaskRoute(taskKey: string, provider: string): Observable<any> {
    return this.http.post<any>(`${this.base}/api/admin/ai-infrastructure/routing/tasks/${taskKey}`, { provider });
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

  private workspaceId(workspaceId?: string): string {
    const resolved = workspaceId || this.auth.currentUser()?.account_id;
    if (!resolved) {
      throw new Error('Workspace context is missing. Please sign in again.');
    }
    return resolved;
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
