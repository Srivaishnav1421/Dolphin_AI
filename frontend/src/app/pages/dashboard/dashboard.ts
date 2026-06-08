import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { DashboardSummary, BrainEvent, BrainDecision } from '../../shared/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  summary = signal<DashboardSummary | null>(null);
  events  = signal<BrainEvent[]>([]);
  recommendations = signal<BrainDecision[]>([]);
  opportunities = signal<BrainDecision[]>([]);
  risks = signal<BrainDecision[]>([]);
  loading = signal(true);
  wsAlive = signal(false);

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private ws: WebsocketService,
    private router: Router
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
    this.api.getDashboard().subscribe({
      next: s => { this.summary.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.api.getRecentEvents().subscribe(e => this.events.set(e));
    this.api.getRecommendations().subscribe({
      next: recs => {
        // Pending recommendations needing immediate approval
        const pending = recs.filter(r => r.status === 'PENDING_APPROVAL');
        this.recommendations.set(pending);

        // Filter Top 3 Opportunities
        this.opportunities.set(recs.filter(r =>
          r.decision_type === 'SCALE_UP' ||
          r.decision_type === 'RESUME' ||
          r.decision_type === 'BUDGET_REALLOCATE' ||
          r.decision_type === 'CREATE_CAMPAIGN' ||
          (r.riskScore || 0) <= 0.4
        ).slice(0, 3));

        // Filter Top 3 Risks
        this.risks.set(recs.filter(r =>
          r.decision_type === 'PAUSE' ||
          r.decision_type === 'SCALE_DOWN' ||
          (r.riskScore || 0) > 0.4
        ).slice(0, 3));
      },
      error: () => {}
    });
  }

  approveDecision(id: string) {
    this.api.approveRecommendation(id).subscribe(() => this.loadData());
  }

  rejectDecision(id: string) {
    this.api.rejectRecommendation(id).subscribe(() => this.loadData());
  }

  confidenceColor(c: number): string {
    if (c >= 0.85) return 'var(--success)';
    if (c >= 0.50) return 'var(--warning)';
    return 'var(--danger)';
  }

  get spendPct(): number {
    const s = this.summary();
    if (!s || !s.total_revenue) return 0;
    return Math.min((s.total_spend / s.total_revenue) * 100, 100);
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
}
