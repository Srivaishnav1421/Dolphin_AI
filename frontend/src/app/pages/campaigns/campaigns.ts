import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Campaign, AdCreative, BrainDecision } from '../../shared/models';

@Component({
  selector: 'app-campaigns',
  standalone: true,
  imports: [CommonModule, DecimalPipe, FormsModule],
  templateUrl: './campaigns.html',
  styleUrl: './campaigns.scss',
})
export class Campaigns implements OnInit {
  campaigns         = signal<Campaign[]>([]);
  loading           = signal(true);
  evaluating        = signal(false);
  saving            = signal(false);
  message           = signal('');
  showForm          = signal(false);

  // Inspector panel
  selected          = signal<Campaign | null>(null);
  creatives         = signal<AdCreative[]>([]);
  recommendations   = signal<BrainDecision[]>([]);
  editBudgetVal     = signal<number>(0);

  // New campaign form fields
  form = {
    name: '',
    objective: 'LEADS',
    budget: 0,
    ctr: 0,
    cpl: 0,
    roas: 0,
    spent: 0,
  };

  objectives = ['LEADS', 'CONVERSIONS', 'AWARENESS', 'TRAFFIC'];

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.api.getCampaigns().subscribe({
      next: c => { this.campaigns.set(c); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  select(c: Campaign) {
    const isSelected = this.selected()?.id === c.id;
    if (isSelected) {
      this.selected.set(null);
      this.creatives.set([]);
      this.recommendations.set([]);
    } else {
      this.selected.set(c);
      this.editBudgetVal.set(c.budget);
      this.loadCampaignDetails(c);
    }
  }

  loadCampaignDetails(c: Campaign) {
    // Load associated creatives
    this.api.getCreatives().subscribe({
      next: list => {
        this.creatives.set(list.filter(x => x.campaign_id === c.id));
      },
      error: () => {}
    });

    // Load recommendations
    this.api.getRecommendations().subscribe({
      next: recs => {
        this.recommendations.set(recs.filter(r => r.campaign_id === c.id || r.campaign_name === c.name));
      },
      error: () => {}
    });
  }

  saveBudget() {
    const c = this.selected();
    const b = this.editBudgetVal();
    if (!c || b <= 0) return;
    this.api.updateCampaign(c.id, { budget: b }).subscribe({
      next: (updated) => {
        this.selected.set(updated);
        this.load();
        this.message.set('✅ Budget target updated successfully!');
        setTimeout(() => this.message.set(''), 3000);
      },
      error: () => {
        this.message.set('❌ Budget update failed.');
      }
    });
  }

  openForm() {
    this.form = { name: '', objective: 'LEADS', budget: 0, ctr: 0, cpl: 0, roas: 0, spent: 0 };
    this.showForm.set(true);
  }

  closeForm() { this.showForm.set(false); }

  createCampaign() {
    if (!this.form.name || !this.form.budget) {
      this.message.set('⚠️ Campaign name and budget are required.');
      return;
    }
    this.saving.set(true);
    this.api.createCampaign(this.form).subscribe({
      next: () => {
        this.message.set('✅ Campaign created!');
        this.saving.set(false);
        this.showForm.set(false);
        this.load();
        setTimeout(() => this.message.set(''), 3000);
      },
      error: () => { this.message.set('❌ Failed to create.'); this.saving.set(false); },
    });
  }

  /** Evaluate all active campaigns via Gemini */
  evaluate() {
    const active = this.campaigns().filter(c => c.status === 'ACTIVE');
    if (!active.length) { this.message.set('⚠️ No active campaigns to evaluate.'); return; }
    this.evaluating.set(true);
    this.message.set('');
    let done = 0;
    active.forEach(c => {
      this.api.evaluateCampaign(c.id).subscribe({
        next: () => { if (++done === active.length) { this.message.set(`✅ Evaluated ${done} campaign(s)!`); this.evaluating.set(false); this.load(); } },
        error: () => { if (++done === active.length) { this.message.set('⚠️ Some evaluations failed.'); this.evaluating.set(false); } },
      });
    });
  }

  pause(id: string)  { this.api.pauseCampaign(id).subscribe(() => this.load()); }
  resume(id: string) { this.api.resumeCampaign(id).subscribe(() => this.load()); }

  delete(id: string) {
    if (!confirm('Delete this campaign?')) return;
    this.api.deleteCampaign(id).subscribe(() => this.load());
  }

  scoreColor(score: number | null): string {
    if (!score) return 'var(--text-muted)';
    if (score >= 70) return 'var(--success)';
    if (score >= 40) return 'var(--warning)';
    return 'var(--danger)';
  }

  getHealth(score: number): { label: string, color: string } {
    if (!score) return { label: 'HEALTHY', color: 'var(--success)' };
    if (score >= 75) return { label: 'HEALTHY', color: 'var(--success)' };
    if (score >= 50) return { label: 'WATCHLIST', color: 'var(--warning)' };
    if (score >= 30) return { label: 'RISK', color: 'var(--orange)' };
    return { label: 'CRITICAL', color: 'var(--danger)' };
  }
}
