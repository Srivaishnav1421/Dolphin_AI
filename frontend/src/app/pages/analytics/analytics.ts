import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { EmasMetrics } from '../../shared/models';
import { UiPanel } from '../../shared/ui';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, DecimalPipe, UiPanel],
  templateUrl: './analytics.html',
  styleUrl: './analytics.scss',
})
export class Analytics implements OnInit {
  metrics = signal<EmasMetrics | null>(null);
  loading = signal(true);
  running = signal(false);
  message = signal('');

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() { this.load(); }

  load() {
    const accountId = this.auth.currentUser()?.account_id;
    if (!accountId) { this.loading.set(false); return; }
    this.loading.set(true);
    this.api.getEmasMetrics(accountId).subscribe({
      next: m => { this.metrics.set(m); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  runArbitrage() {
    const accountId = this.auth.currentUser()?.account_id;
    if (!accountId) return;
    this.running.set(true);
    this.api.runArbitrage(accountId).subscribe({
      next: () => { this.message.set('✅ Arbitrage executed!'); this.running.set(false); this.load(); setTimeout(() => this.message.set(''), 3000); },
      error: () => { this.message.set('❌ Failed.'); this.running.set(false); },
    });
  }

  roasHealth(roas: number) {
    if (roas >= 3) return 'var(--success)';
    if (roas >= 1.5) return 'var(--warning)';
    return 'var(--danger)';
  }
  merHealth(mer: number) {
    if (mer >= 4) return 'var(--success)';
    if (mer >= 2) return 'var(--warning)';
    return 'var(--danger)';
  }
}
