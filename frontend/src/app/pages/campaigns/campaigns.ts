import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Campaign, AdCreative, BrainDecision, CampaignMathEvaluation } from '../../shared/models';

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
  mathScores        = signal<Record<string, CampaignMathEvaluation>>({});

  // Inspector panel
  selected          = signal<Campaign | null>(null);
  creatives         = signal<AdCreative[]>([]);
  recommendations   = signal<BrainDecision[]>([]);
  editBudgetVal     = signal<number>(0);

  // New campaign form fields
  form = {
    name: '',
    objective: 'LEADS',
    budget: null as number | null,
    target_cpl: null as number | null,
    description: '',
    ctr: null as number | null,
    cpl: null as number | null,
    roas: null as number | null,
    spent: null as number | null,
  };

  objectives = ['LEADS', 'CONVERSIONS', 'AWARENESS', 'TRAFFIC'];

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.api.getCampaigns().subscribe({
      next: c => {
        this.campaigns.set(c);
        this.loading.set(false);
        this.loadMathScores(c);
      },
      error: () => {
        this.message.set('Could not load campaigns. Check backend and database connection.');
        this.loading.set(false);
      },
    });
  }

  loadMathScores(campaigns = this.campaigns()) {
    const scores: Record<string, CampaignMathEvaluation> = {};
    this.mathScores.set(scores);
    campaigns.forEach(campaign => {
      this.api.getCampaignMathScore(campaign.id).subscribe({
        next: score => this.mathScores.update(current => ({ ...current, [campaign.id]: score })),
        error: () => {
          // No score yet is a valid state before the Math Engine has evaluated this campaign.
        }
      });
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
      error: () => {
        this.message.set('Could not load campaign creatives from the database.');
      }
    });

    // Load recommendations
    this.api.getRecommendations().subscribe({
      next: recs => {
        this.recommendations.set(recs.filter(r => r.campaign_id === c.id || r.campaign_name === c.name));
      },
      error: () => {
        this.message.set('Could not load campaign recommendations from the database.');
      }
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
    this.form = {
      name: '',
      objective: 'LEADS',
      budget: null,
      target_cpl: null,
      description: '',
      ctr: null,
      cpl: null,
      roas: null,
      spent: null
    };
    this.showForm.set(true);
  }

  closeForm() { this.showForm.set(false); }

  createCampaign() {
    if (!this.form.name || !this.form.budget || !this.form.target_cpl) {
      this.message.set('⚠️ Campaign name, budget, and target CPL are required.');
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

  latestMathScore(campaign: Campaign): CampaignMathEvaluation | null {
    return this.mathScores()[campaign.id] ?? null;
  }

  scoreValue(campaign: Campaign): number | null {
    const score = this.latestMathScore(campaign)?.score;
    return typeof score === 'number' ? score : null;
  }

  scoreLabel(campaign: Campaign): string {
    const score = this.scoreValue(campaign);
    return score === null ? 'No score yet' : score.toFixed(1);
  }

  scoreColor(score: number | null): string {
    if (score === null) return 'var(--text-muted)';
    if (score >= 70) return 'var(--success)';
    if (score >= 40) return 'var(--warning)';
    return 'var(--danger)';
  }

  getHealth(score: number | null): { label: string, color: string } {
    if (score === null) return { label: 'NO SCORE YET', color: 'var(--text-muted)' };
    if (score >= 75) return { label: 'HEALTHY', color: 'var(--success)' };
    if (score >= 50) return { label: 'WATCHLIST', color: 'var(--warning)' };
    if (score >= 30) return { label: 'RISK', color: 'var(--orange)' };
    return { label: 'CRITICAL', color: 'var(--danger)' };
  }

  scoreSnapshot(campaign: Campaign): any {
    const raw = this.latestMathScore(campaign)?.input_snapshot_json;
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  scoreBreakdown(campaign: Campaign): Record<string, number> | null {
    return this.scoreSnapshot(campaign)?.scoreBreakdown ?? null;
  }

  scoreExplanation(campaign: Campaign): string[] {
    const explanation = this.scoreSnapshot(campaign)?.explanation;
    return Array.isArray(explanation) ? explanation : [];
  }

  moneyValue(value: number | null | undefined): string {
    return typeof value === 'number' ? `₹${Math.round(value).toLocaleString('en-IN')}` : 'No data yet';
  }

  percentValue(value: number | null | undefined): string {
    return typeof value === 'number' ? `${value.toFixed(2)}%` : 'No actual CTR yet';
  }

  multiplierValue(value: number | null | undefined): string {
    return typeof value === 'number' ? `${value.toFixed(2)}x` : 'No ROAS yet';
  }
}
