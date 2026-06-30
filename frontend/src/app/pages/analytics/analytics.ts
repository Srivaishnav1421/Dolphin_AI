import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AnalyticsSummary } from '../../shared/models';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, DecimalPipe],
  templateUrl: './analytics.html',
  styleUrl: './analytics.scss',
})
export class Analytics implements OnInit {
  summary = signal<AnalyticsSummary | null>(null);
  loading = signal(true);
  message = signal('');

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.message.set('');
    this.api.getAnalyticsSummary().subscribe({
      next: summary => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => {
        this.summary.set(null);
        this.message.set('Could not load analytics. Check backend and database connection.');
        this.loading.set(false);
      },
    });
  }

  roasHealth(roas: number) {
    if (roas >= 3) return 'var(--success)';
    if (roas >= 1.5) return 'var(--warning)';
    return 'var(--danger)';
  }

  approvalHealth(pending: number) {
    if (pending === 0) return 'var(--success)';
    if (pending <= 5) return 'var(--warning)';
    return 'var(--danger)';
  }

  sectionState(empty: boolean) {
    return empty ? 'No records yet' : 'DB-backed';
  }
}
