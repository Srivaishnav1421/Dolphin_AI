import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { ApprovalItem, BrainEvent, BrainDecision, ArbitrageResult, LlmProviderStatus } from '../../shared/models';
import { Subscription } from 'rxjs';

import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-ad-brain',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, FormsModule],
  templateUrl: './ad-brain.html',
  styleUrl: './ad-brain.scss',
})
export class AdBrain implements OnInit, OnDestroy {
  events        = signal<BrainEvent[]>([]);
  decisions     = signal<BrainDecision[]>([]);
  pending       = signal<BrainDecision[]>([]);
  arbitrage     = signal<ArbitrageResult | null>(null);
  llmStatus     = signal<LlmProviderStatus | null>(null);
  loading       = signal(true);
  running       = signal(false);
  wsAlive       = signal(false);
  
  // Cleaned 4-tab panel structure
  activeTab     = signal<'OPPORTUNITIES' | 'RISKS' | 'HISTORY' | 'APPROVALS'>('OPPORTUNITIES');
  
  opportunities = signal<BrainDecision[]>([]);
  risks         = signal<BrainDecision[]>([]);
  history       = signal<BrainDecision[]>([]);
  approvals     = signal<ApprovalItem[]>([]);
  approvalsError = signal<string | null>(null);

  experimentsList = signal<any[]>([]);
  recommendations = signal<BrainDecision[]>([]);
  evaluating      = signal(false);
  analytics       = signal<any>(null);

  // Competitor Intelligence Signals
  insights      = signal<any[]>([]);
  competitorUrl = signal('');
  crawling      = signal(false);

  private sub?: Subscription;
  private execSub?: Subscription;
  private accountId = '';

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private ws: WebsocketService,
  ) {}

  ngOnInit() {
    this.accountId = this.auth.currentUser()?.account_id ?? '';
    this.load();

    const token = this.auth.getToken();
    if (token) {
      this.ws.connect(token);
      this.ws.connected$.subscribe(v => this.wsAlive.set(v));
      this.sub = this.ws.events$.subscribe(evt => {
        const ev: BrainEvent = {
          id:         crypto.randomUUID(),
          event_type: evt.event_type,
          message:    evt.message,
          severity:   evt.severity,
          account_id: evt.account_id ?? '',
          created_at: evt.created_at ?? new Date().toISOString(),
        };
        this.events.update(list => [ev, ...list].slice(0, 50));
      });
      this.execSub = this.ws.executions$.subscribe(exec => {
        const ev: BrainEvent = {
          id:         crypto.randomUUID(),
          event_type: exec.eventType || 'EXECUTION_UPDATE',
          message:    exec.message || 'Autonomous action triggered.',
          severity:   exec.eventType?.includes('FAILED') ? 'CRITICAL' : 'SUCCESS',
          account_id: this.accountId,
          created_at: new Date().toISOString(),
        };
        this.events.update(list => [ev, ...list].slice(0, 50));
        this.load();
      });
    }
  }

  setTab(tab: 'OPPORTUNITIES' | 'RISKS' | 'HISTORY' | 'APPROVALS') {
    this.activeTab.set(tab);
  }

  load() {
    this.loading.set(true);
    this.api.getRecentEvents().subscribe({
      next: e => { this.events.set(e); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.api.getRecommendations().subscribe({
      next: r => {
        this.recommendations.set(r);
        this.decisions.set(r);
        this.pending.set(r.filter(d => d.status === 'PENDING_APPROVAL'));

        // Map into specific tabs
        this.opportunities.set(r.filter(d => 
          d.decision_type !== 'PAUSE_UNDERPERFORMING' && 
          d.decision_type !== 'BUDGET_SAFETY_LOCK' && 
          d.status === 'PENDING_APPROVAL'
        ));

        this.risks.set(r.filter(d => 
          (d.decision_type === 'PAUSE_UNDERPERFORMING' || d.decision_type === 'BUDGET_SAFETY_LOCK') && 
          d.status === 'PENDING_APPROVAL'
        ));

        this.history.set(r.filter(d => d.status !== 'PENDING_APPROVAL'));
      },
      error: () => {},
    });
    this.api.getPendingApprovals().subscribe({
      next: approvals => {
        this.approvalsError.set(null);
        this.approvals.set(approvals ?? []);
      },
      error: () => {
        this.approvals.set([]);
        this.approvalsError.set('Could not load the global approval queue.');
      }
    });
    this.api.getLlmStatus().subscribe({
      next: s => this.llmStatus.set(s),
      error: () => {},
    });
    this.api.listExperiments().subscribe({
      next: exp => this.experimentsList.set(exp),
      error: () => {},
    });
    if (this.accountId) {
      this.api.getBrainAnalytics(this.accountId).subscribe({
        next: a => this.analytics.set(a),
        error: () => {},
      });
    } else {
      this.analytics.set(null);
    }
    this.loadInsights();
  }

  evaluate() {
    this.evaluating.set(true);
    this.api.evaluateRecommendations().subscribe({
      next: () => {
        this.evaluating.set(false);
        this.load();
      },
      error: () => this.evaluating.set(false)
    });
  }

  parseActionPlan(snapshotJson: string | undefined): string[] {
    if (!snapshotJson) return [];
    try {
      const plan = JSON.parse(snapshotJson);
      if (Array.isArray(plan)) return plan;
      if (plan && Array.isArray(plan.plan)) return plan.plan;
      return [];
    } catch (e) {
      return [];
    }
  }

  loadInsights() {
    this.api.getCompetitorInsights().subscribe({
      next: ins => this.insights.set(ins),
      error: () => {}
    });
  }

  runCompetitorAnalysis() {
    const url = this.competitorUrl().trim();
    if (!url) return;
    this.crawling.set(true);
    this.api.analyzeCompetitor(url).subscribe({
      next: () => {
        this.crawling.set(false);
        this.competitorUrl.set('');
        this.loadInsights();
      },
      error: () => this.crawling.set(false)
    });
  }

  runArbitrage() {
    if (!this.accountId) {
      alert('Workspace context is missing. Please sign in again before running budget analysis.');
      return;
    }
    this.running.set(true);
    this.api.runArbitrage(this.accountId).subscribe({
      next: res => { this.arbitrage.set(res); this.running.set(false); },
      error: () => this.running.set(false),
    });
  }

  approveDecision(id: string) {
    this.api.approveApprovalItem(id).subscribe(() => this.load());
  }

  rejectDecision(id: string) {
    this.api.rejectApprovalItem(id, 'Rejected from AI Insights').subscribe(() => this.load());
  }


  severityClass(s: string) {
    return { INFO: 'info', WARNING: 'warm', CRITICAL: 'hot', SUCCESS: 'active' }[s] ?? 'info';
  }

  decisionClass(s: string) {
    return {
      PENDING_APPROVAL: 'warm', AUTO_EXECUTED: 'active', APPROVED: 'active',
      REJECTED: 'hot', BLOCKED_BY_SAFETY: 'hot', LOGGED_ONLY: 'info',
    }[s] ?? 'info';
  }

  confidenceColor(c: number): string {
    if (c >= 0.85) return 'var(--success)';
    if (c >= 0.50) return 'var(--warning)';
    return 'var(--danger)';
  }

  rollbackDecision(id: string) {
    this.api.rollbackDecision(id).subscribe({
      next: () => this.load(),
      error: (e) => alert('Rollback failed: ' + (e.error?.error || e.message))
    });
  }

  activateExperiment(id: string) {
    this.api.activateExperiment(id).subscribe({
      next: () => this.load(),
      error: (e) => alert('Experiment activation failed: ' + (e.error?.error || e.message))
    });
  }

  triggerDemoAbExperiment(campaignId: string) {
    const headlines = [
      'Scale your sales with Chubby Dolphin AI',
      'Autonomous Marketing Brain for Indian Brands',
      'Replace your ad agency with 24/7 autonomous AI'
    ];
    const bodies = [
      'Stop wasting money on poor Meta optimization. Get real results.',
      'Indian startup sees 4.2x ROAS lift using autonomous ad pilot.',
      'Integrate WhatsApp automated sequences with your Meta Leads.'
    ];
    const ctas = ['LEARN_MORE', 'SIGN_UP', 'BOOK_NOW'];

    this.api.createAbExperiment(campaignId, headlines, bodies, ctas).subscribe({
      next: () => {
        alert('Advantage+ 27-copy permutation grid created successfully and registered as approval-required A/B experiment!');
        this.load();
      },
      error: (e) => alert('A/B experiment creation failed: ' + (e.error?.error || e.message))
    });
  }

  ngOnDestroy() { 
    this.sub?.unsubscribe(); 
    this.execSub?.unsubscribe();
  }
}
