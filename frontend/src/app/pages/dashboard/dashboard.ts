import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { ApprovalItem, DashboardSummary, BrainEvent, BrainDecision, AdBrainRunResult } from '../../shared/models';
import { AppIcon } from '../../shared/ui';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, DecimalPipe, RouterLink, AppIcon],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  summary = signal<DashboardSummary | null>(null);
  events  = signal<BrainEvent[]>([]);
  recommendations = signal<BrainDecision[]>([]);
  pendingApprovals = signal<ApprovalItem[]>([]);
  opportunities = signal<BrainDecision[]>([]);
  risks = signal<BrainDecision[]>([]);
  loading = signal(true);
  loadError = signal<string | null>(null);
  approvalsError = signal<string | null>(null);
  wsAlive = signal(false);
  adBrainStatus = signal<AdBrainRunResult | null>(null);
  adBrainRunning = signal(false);
  adBrainMessage = signal<string | null>(null);
  adBrainError = signal<string | null>(null);

  /** All integration providers shown on Growth Home */
  private readonly INTEGRATION_KEYS = ['meta', 'whatsapp', 'google', 'n8n', 'openai', 'gemini', 'anthropic', 'huggingface', 'ollama'];

  integrationStatuses = signal<Record<string, 'connected' | 'needs-setup' | 'needs-validation' | 'error'>>({});
  /** Non-blocking warning shown when integration status API is unreachable */
  integrationStatusError = signal<string | null>(null);

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private ws: WebsocketService,
    private router: Router,
    private http: HttpClient,
    private config: RuntimeConfigService
  ) {}

  ngOnInit() {
    this.loadData();
    const token = this.auth.getToken();
    if (token) {
      this.ws.connect(token);
      this.ws.connected$.subscribe(v => this.wsAlive.set(v));
      this.ws.events$.subscribe(evt => {
        const ev: BrainEvent = {
          id:         crypto.randomUUID(),
          event_type: evt.event_type,
          message:    evt.message,
          severity:   evt.severity,
          account_id: evt.account_id ?? '',
          created_at: evt.created_at ?? new Date().toISOString(),
        };
        this.events.update(list => [ev, ...list].slice(0, 20));
      });
    }
  }

  loadData() {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.getDashboard().subscribe({
      next: s => { this.summary.set(s); this.loading.set(false); },
      error: () => {
        this.loading.set(false);
        this.loadError.set('Could not load dashboard data. Check that the backend is running and the database is connected.');
      },
    });
    this.api.getRecentEvents().subscribe({ next: e => this.events.set(e), error: () => {} });

    // Build a default map so no card ever shows "error" when the API is unreachable.
    const defaultStatuses = () => {
      const m: Record<string, 'connected' | 'needs-setup' | 'needs-validation' | 'error'> = {};
      ['meta', 'whatsapp', 'google', 'ai', 'n8n'].forEach(k => (m[k] = 'needs-setup'));
      return m;
    };

    this.http.get<any>(`${this.config.apiBase}/api/integrations/status`).subscribe({
      next: (res) => {
        this.integrationStatusError.set(null); // Clear any previous warning on success
        const statusMap: Record<string, 'connected' | 'needs-setup' | 'needs-validation' | 'error'> = {};
        ['meta', 'whatsapp', 'google', 'n8n'].forEach(key => {
          statusMap[key] = this.integrationStatusFromInfo(res?.[key]);
        });
        const aiStatuses = ['openai', 'gemini', 'anthropic', 'huggingface', 'ollama']
          .map(key => this.integrationStatusFromInfo(res?.[key]));
        statusMap['ai'] = aiStatuses.includes('connected') ? 'connected'
          : aiStatuses.includes('error') ? 'error'
          : aiStatuses.includes('needs-validation') ? 'needs-validation'
          : 'needs-setup';
        this.integrationStatuses.set(statusMap);
      },
      error: () => {
        // Non-blocking: show warning, default all to needs-setup, do NOT break dashboard
        this.integrationStatusError.set(
          'Integration status could not be refreshed. Showing setup-required state.'
        );
        this.integrationStatuses.set(defaultStatuses());
      }
    });

    this.api.getRecommendations().subscribe({
      next: recs => {
        this.recommendations.set(recs.filter(r => r.status === 'PENDING_APPROVAL'));
        this.opportunities.set(recs.filter(r =>
          r.decision_type === 'SCALE_UP' ||
          r.decision_type === 'RESUME' ||
          r.decision_type === 'BUDGET_REALLOCATE' ||
          r.decision_type === 'CREATE_CAMPAIGN' ||
          (r.riskScore || 0) <= 0.4
        ).slice(0, 3));
        this.risks.set(recs.filter(r =>
          r.decision_type === 'PAUSE' ||
          r.decision_type === 'SCALE_DOWN' ||
          (r.riskScore || 0) > 0.4
        ).slice(0, 3));
      },
      error: () => {}
    });

    this.api.getPendingApprovals().subscribe({
      next: approvals => {
        this.approvalsError.set(null);
        this.pendingApprovals.set(approvals ?? []);
      },
      error: () => {
        this.pendingApprovals.set([]);
        this.approvalsError.set('Could not load pending approvals from the database.');
      }
    });

    this.api.getAdBrainStatus().subscribe({
      next: status => {
        this.adBrainStatus.set(status);
        if (status?.status === 'COMPLETED' && !this.adBrainMessage()) {
          this.adBrainMessage.set(status.message);
        }
      },
      error: () => {}
    });
  }

  runAdBrain() {
    this.adBrainRunning.set(true);
    this.adBrainError.set(null);
    this.adBrainMessage.set(null);
    this.api.runAdBrain().subscribe({
      next: result => {
        this.adBrainRunning.set(false);
        this.adBrainStatus.set(result);
        this.adBrainMessage.set(result.message);
        this.loadData();
      },
      error: () => {
        this.adBrainRunning.set(false);
        this.adBrainError.set('Ad Brain could not run. No campaign actions were executed.');
      }
    });
  }

  approveDecision(id: string) {
    this.api.approveRecommendation(id).subscribe(() => this.loadData());
  }

  rejectDecision(id: string) {
    this.api.rejectRecommendation(id).subscribe(() => this.loadData());
  }

  approveApproval(id: string) {
    this.api.approveApprovalItem(id).subscribe({
      next: () => this.loadData(),
      error: () => this.approvalsError.set('Could not approve this item. Refresh and try again.')
    });
  }

  rejectApproval(id: string) {
    this.api.rejectApprovalItem(id).subscribe({
      next: () => this.loadData(),
      error: () => this.approvalsError.set('Could not reject this item. Refresh and try again.')
    });
  }

  confidenceColor(c: number): string {
    if (c >= 0.85) return 'var(--success)';
    if (c >= 0.50) return 'var(--warning)';
    return 'var(--danger)';
  }

  get spendPct(): number {
    const s = this.summary();
    if (!s || !s.total_campaign_budget) return 0;
    return Math.min((s.total_spend / s.total_campaign_budget) * 100, 100);
  }

  totalLeads(): number {
    const s = this.summary();
    return (s?.hot_leads ?? 0) + (s?.warm_leads ?? 0) + (s?.cold_leads ?? 0);
  }

  severityClass(s: string) {
    return { INFO: 'info', WARNING: 'warm', CRITICAL: 'hot', SUCCESS: 'active' }[s] ?? 'info';
  }

  get llmProviderName(): string {
    const s = this.summary();
    if (!s?.llm_status) return '—';
    return s.llm_status.active_provider ?? '—';
  }

  get llmOllamaAvailable(): boolean {
    return this.summary()?.llm_status?.ollama?.available ?? false;
  }

  get lastAdBrainRunLabel(): string {
    const status = this.adBrainStatus();
    if (!status?.started_at) return 'Never run';
    return new Date(status.started_at).toLocaleString();
  }

  goToIntegrations() {
    this.router.navigate(['/integrations']);
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      'connected':   'Connected',
      'needs-setup': 'Needs setup',
      'needs-validation': 'Needs validation',
      'error':       'Needs attention'
    };
    return map[status] || status;
  }

  integrationStatus(key: string): 'connected' | 'needs-setup' | 'needs-validation' | 'error' {
    return this.integrationStatuses()[key] ?? 'needs-setup';
  }

  integrationStatusFromInfo(info: any): 'connected' | 'needs-setup' | 'needs-validation' | 'error' {
    if (!info) return 'needs-setup';
    if (info.validationStatus === 'FAILED' || info.error) return 'error';
    if (info.connected || info.validationStatus === 'VALIDATED') return 'connected';
    if (info.configured || info.validationStatus === 'PENDING_VALIDATION') return 'needs-validation';
    return 'needs-setup';
  }

  revenueHealthLabel(s: DashboardSummary): string {
    if ((s.total_spend ?? 0) <= 0) return 'No spend yet';
    if ((s.blended_roas ?? 0) >= 2.5) return 'Healthy';
    if ((s.blended_roas ?? 0) >= 1) return 'Watch';
    return 'At risk';
  }

  revenueHealthClass(s: DashboardSummary): string {
    if ((s.total_spend ?? 0) <= 0) return 'text-muted';
    if ((s.blended_roas ?? 0) >= 2.5) return 'text-success';
    if ((s.blended_roas ?? 0) >= 1) return 'text-warn';
    return 'text-danger';
  }

  get roleProfile() {
    const role = this.auth.currentUser()?.role ?? 'VIEWER';
    const profiles: Record<string, { title: string; scope: string; focus: string; access: string }> = {
      OWNER: {
        title: 'Executive Growth Command',
        scope: 'Full business control',
        focus: 'Revenue, ROAS, team access, billing, integrations, and approvals',
        access: 'Read/write/admin'
      },
      ADMIN: {
        title: 'Operations Control Dashboard',
        scope: 'Workspace administration',
        focus: 'Settings, integrations, team access, campaigns, and daily operations',
        access: 'Read/write/admin'
      },
      MANAGER: {
        title: 'Manager Performance Dashboard',
        scope: 'Growth execution',
        focus: 'Campaign movement, lead quality, approvals, automation outcomes',
        access: 'Read/write, limited admin'
      },
      EMPLOYEE: {
        title: 'Work Queue Dashboard',
        scope: 'Assigned business work',
        focus: 'Leads, follow-ups, notes, and assigned customer actions',
        access: 'Limited write'
      },
      VIEWER: {
        title: 'Reporting Dashboard',
        scope: 'Read-only visibility',
        focus: 'Performance summary, reports, and workspace status',
        access: 'Read-only'
      }
    };
    return profiles[role] ?? profiles['VIEWER'];
  }
}
