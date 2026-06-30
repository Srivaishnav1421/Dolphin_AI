import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Lead } from '../../shared/models';
import { AppIcon } from '../../shared/ui';

type Filter = 'ALL' | 'HOT' | 'WARM' | 'COLD' | 'UNKNOWN';
type ViewMode = 'LIST' | 'KANBAN';
type PipelineStage = 'NEW_LEAD' | 'CONTACTED' | 'QUALIFIED' | 'INTERESTED' | 'PROPOSAL_SENT' | 'FOLLOW_UP' | 'NEGOTIATION' | 'CONVERTED' | 'LOST' | 'DORMANT' | 'RECYCLED';
type MessageTone = 'success' | 'warning' | 'error';

interface InteractionLog {
  id: string;
  type: 'EMAIL' | 'CALL' | 'MEETING' | 'SYSTEM';
  notes: string;
  createdAt: Date;
}

@Component({
  selector: 'app-leads',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, FormsModule, AppIcon],
  templateUrl: './leads.html',
  styleUrl: './leads.scss',
})
export class Leads implements OnInit {
  leads          = signal<Lead[]>([]);
  loading        = signal(true);
  scoring        = signal(false);
  filter         = signal<Filter>('ALL');
  selected       = signal<Lead | null>(null);
  showForm       = signal(false);
  message        = signal('');
  messageTone    = signal<MessageTone>('warning');
  viewMode       = signal<ViewMode>('KANBAN'); // Default to beautiful Kanban board
  whatsAppConnected = signal(false);

  // Interaction logs (emails, calls, meetings)
  interactions   = signal<InteractionLog[]>([]);
  newNoteText    = signal('');
  newNoteType    = signal<'EMAIL' | 'CALL' | 'MEETING'>('CALL');

  // Conversation note signals
  chatMessages   = signal<any[]>([]);
  newMessageText = signal('');
  sendingMessage = signal(false);
  submittingApproval = signal(false);
  selectedScoreBreakdown = signal<Record<string, any> | null>(null);

  filters: Filter[] = ['ALL', 'HOT', 'WARM', 'COLD', 'UNKNOWN'];
  kanbanStages: PipelineStage[] = ['NEW_LEAD', 'CONTACTED', 'QUALIFIED', 'INTERESTED', 'PROPOSAL_SENT', 'FOLLOW_UP', 'NEGOTIATION', 'CONVERTED', 'LOST'];
  stageOptions = this.kanbanStages.map(stage => ({ value: stage, label: this.stageLabel(stage) }));

  form = { name: '', phone: '', email: '', message: '', source: 'INSTAGRAM' };
  sources = ['INSTAGRAM', 'FACEBOOK', 'WEBSITE', 'WHATSAPP', 'FORM', 'REFERRAL'];

  interactionIcon(type: InteractionLog['type']): string {
    return { SYSTEM: 'insights', CALL: 'chat', EMAIL: 'campaign', MEETING: 'group' }[type];
  }

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.load();
    this.loadIntegrationStatus();
  }

  load() {
    this.loading.set(true);
    const f = this.filter() === 'ALL' ? undefined : this.filter();
    this.api.getLeads(f).subscribe({
      next: l => { this.leads.set(l); this.loading.set(false); },
      error: () => {
        this.messageTone.set('error');
        this.message.set('Could not load CRM leads. Check backend and database connection.');
        this.loading.set(false);
      },
    });
  }

  loadIntegrationStatus() {
    this.api.getIntegrationStatus().subscribe({
      next: status => this.whatsAppConnected.set(!!status?.['whatsapp']?.connected),
      error: () => this.whatsAppConnected.set(false),
    });
  }

  setFilter(f: Filter) { this.filter.set(f); this.selected.set(null); this.load(); }
  
  select(l: Lead) {
    const isSelected = this.selected()?.id === l.id;
    this.chatMessages.set([]);
    this.newMessageText.set('');
    this.interactions.set([]);
    this.newNoteText.set('');
    this.selectedScoreBreakdown.set(null);
    
    if (isSelected) {
      this.selected.set(null);
    } else {
      this.selected.set(l);
      this.loadChatHistory(l.id);
      this.loadInteractions(l);
      this.refreshLeadScore(l);
    }
  }

  refreshLeadScore(lead: Lead) {
    this.api.scoreExistingLead(lead.id).subscribe({
      next: response => {
        const updated = response?.lead as Lead | undefined;
        if (updated) {
          this.selected.set(updated);
          this.leads.set(this.leads().map(item => item.id === updated.id ? updated : item));
        }
        this.selectedScoreBreakdown.set(response?.score_breakdown ?? this.parseScoreBreakdown(updated ?? lead));
      },
      error: () => {
        this.selectedScoreBreakdown.set(this.parseScoreBreakdown(lead));
      }
    });
  }

  loadChatHistory(leadId: string) {
    this.api.getLeadChatHistory(leadId).subscribe({
      next: msgs => this.chatMessages.set(msgs),
      error: () => {
        this.messageTone.set('error');
        this.message.set('Could not load conversation history for this lead.');
      }
    });
  }

  loadInteractions(l: Lead) {
    const systemEntries: InteractionLog[] = [
      {
        id: `created-${l.id}`,
        type: 'SYSTEM',
        notes: `Lead created from source: ${l.source || 'WEB'}`,
        createdAt: new Date(new Date(l.created_at).getTime() - 1000 * 60 * 5)
      },
      {
        id: `scored-${l.id}`,
        type: 'SYSTEM',
        notes: `Lead qualification recorded: ${this.statusLabel(l)} with intent: ${this.intentSignal(l)}`,
        createdAt: new Date(l.created_at)
      }
    ];
    this.interactions.set(systemEntries);
    this.api.getLeadInteractions(l.id).subscribe({
      next: rows => {
        const persisted = rows.map(row => ({
          id: row.id,
          type: this.normalizeInteractionType(row.type),
          notes: row.details || row.notes || '',
          createdAt: new Date(row.createdAt || row.created_at)
        }));
        this.interactions.set([...systemEntries, ...persisted]);
      },
      error: () => {
        this.messageTone.set('error');
        this.message.set('Could not load CRM interaction timeline.');
      }
    });
  }

  addInteraction() {
    const lead = this.selected();
    const txt = this.newNoteText().trim();
    if (!lead || !txt) return;
    const type = this.newNoteType();
    this.api.addLeadInteraction(lead.id, { type, channel: type, notes: txt }).subscribe({
      next: row => {
        const newLog: InteractionLog = {
          id: row.id || crypto.randomUUID(),
          type: this.normalizeInteractionType(row.type || type),
          notes: row.details || txt,
          createdAt: new Date(row.createdAt || row.created_at || Date.now())
        };
        this.interactions.update(list => [...list, newLog]);
        this.newNoteText.set('');
      },
      error: () => {
        this.messageTone.set('error');
        this.message.set('Could not save this interaction. Please try again.');
      }
    });
  }

  sendChatMessage() {
    const lead = this.selected();
    const txt = this.newMessageText().trim();
    if (!lead || !txt) return;
    this.sendingMessage.set(true);
    this.api.addLeadInteraction(lead.id, { type: 'NOTE', channel: 'CRM', notes: txt }).subscribe({
      next: row => {
        this.sendingMessage.set(false);
        this.newMessageText.set('');
        this.interactions.update(list => [...list, {
          id: row.id || crypto.randomUUID(),
          type: this.normalizeInteractionType(row.type),
          notes: row.details || txt,
          createdAt: new Date(row.createdAt || row.created_at || Date.now())
        }]);

        // Reload leads list dynamically
        this.api.getLeads().subscribe({
          next: list => {
            this.leads.set(list);
            const updated = list.find(x => x.id === lead.id);
            if (updated) this.selected.set(updated);
          }
        });
      },
      error: () => {
        this.sendingMessage.set(false);
        this.messageTone.set('error');
        this.message.set('Could not save this conversation note.');
      }
    });
  }

  openForm() {
    this.form = { name: '', phone: '', email: '', message: '', source: 'INSTAGRAM' };
    this.message.set('');
    this.messageTone.set('warning');
    this.showForm.set(true);
  }
  closeForm() { this.showForm.set(false); }

  /** Submit lead and calculate deterministic Lead Score. */
  submitLead() {
    if (!this.form.name || !this.form.message) {
      this.messageTone.set('warning');
      this.message.set('Name and message are required.');
      return;
    }
    this.scoring.set(true);
    this.api.createLead(this.form).subscribe({
      next: (scored) => {
        this.messageTone.set('success');
        this.message.set(`Lead Score: ${this.scorePct(scored.score)}% (${this.statusLabel(scored)})`);
        this.scoring.set(false);
        this.showForm.set(false);
        this.filter.set('ALL');
        this.load();
        setTimeout(() => this.message.set(''), 4000);
      },
      error: () => {
        this.messageTone.set('error');
        this.message.set('Lead could not be saved. Please check backend and database connection.');
        this.scoring.set(false);
      },
    });
  }

  safeScore(score: number | null | undefined): number {
    const value = Number(score);
    if (!Number.isFinite(value)) return 0;
    return Math.max(0, Math.min(1, value));
  }

  scorePct(score: number | null | undefined): number {
    return Math.round(this.safeScore(score) * 100);
  }

  scoreBar(score: number | null | undefined)  { return this.scorePct(score); }
  scoreColor(score: number | null | undefined) {
    const value = this.safeScore(score);
    if (value >= 0.7) return 'var(--danger)';
    if (value >= 0.4) return 'var(--warning)';
    return 'var(--info)';
  }

  temperatureLabel(lead: Lead): string {
    return lead.temperature || 'UNKNOWN';
  }

  statusLabel(lead: Lead): string {
    return lead.status || 'NEW';
  }

  statusClass(lead: Lead): string {
    return this.temperatureLabel(lead).toLowerCase();
  }

  intentSignal(lead: Lead): string {
    return lead.intent_signal?.trim() || 'Not detected';
  }

  conversionPct(lead: Lead): number {
    return this.scorePct(lead.conversion_probability ?? lead.score);
  }

  count(filter: Filter) {
    if (filter === 'ALL') return this.leads().length;
    return this.leads().filter(l => l.temperature === filter).length;
  }

  // ── Kanban stage mapping helpers ─────────────────────────────────
  getLeadsByStage(stage: PipelineStage): Lead[] {
    const list = this.leads();
    return list.filter(l => {
      const leadStage = l.pipeline_stage || this.stageFromStatus(l);
      return leadStage === stage;
    });
  }

  moveLead(lead: Lead, stage: PipelineStage) {
    this.api.updateLeadStatus(lead.id, { pipeline_stage: stage }).subscribe({
      next: (updated) => {
        this.load();
        if (this.selected()?.id === lead.id) {
          this.selected.set(updated);
          this.loadInteractions(updated);
        }
      },
      error: () => {
        this.messageTone.set('error');
        this.message.set('Could not move this lead. Please retry.');
      }
    });
  }

  // Next action suggestions
  getRecommendedAction(lead: Lead): string {
    if (lead.next_best_action?.trim()) {
      return lead.next_best_action;
    }
    const temp = lead.temperature || 'UNKNOWN';
    if (temp === 'HOT') {
      return 'Schedule a demo call immediately & send commercial proposal.';
    }
    if (temp === 'WARM') {
      return 'Send follow-up message.';
    }
    if (temp === 'COLD') {
      return 'Ask one qualifying question.';
    }
    return 'Collect contact details before prioritizing.';
  }

  stageFromStatus(lead: Lead): PipelineStage {
    const temp = lead.temperature || 'UNKNOWN';
    if (temp === 'HOT') return 'INTERESTED';
    if (temp === 'WARM') return 'QUALIFIED';
    return lead.pipeline_stage || 'NEW_LEAD';
  }

  stageLabel(stage: PipelineStage): string {
    return stage.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }

  normalizeInteractionType(type: string): InteractionLog['type'] {
    const normalized = (type || '').toUpperCase();
    if (normalized === 'EMAIL' || normalized === 'CALL' || normalized === 'MEETING') return normalized;
    return 'SYSTEM';
  }

  setViewMode(mode: ViewMode) {
    this.viewMode.set(mode);
  }

  submitFollowupApproval() {
    const lead = this.selected();
    if (!lead) return;
    this.submittingApproval.set(true);
    this.api.submitLeadFollowupApproval(lead.id).subscribe({
      next: () => {
        this.submittingApproval.set(false);
        this.messageTone.set('success');
        this.message.set('Follow-up queued for approval. Nothing was sent externally.');
        setTimeout(() => this.message.set(''), 5000);
      },
      error: () => {
        this.submittingApproval.set(false);
        this.messageTone.set('error');
        this.message.set('Could not submit follow-up for approval.');
      }
    });
  }

  scoreBreakdownRows(lead: Lead) {
    const breakdown = this.selectedScoreBreakdown() ?? this.parseScoreBreakdown(lead);
    if (!breakdown) return [];
    return [
      { label: 'Phone', value: breakdown['phone'] ?? 0, max: 20 },
      { label: 'Email', value: breakdown['email'] ?? 0, max: 20 },
      { label: 'Source/campaign', value: breakdown['sourceOrCampaign'] ?? 0, max: 15 },
      { label: 'Intent status', value: breakdown['interestedOrQualifiedStatus'] ?? 0, max: 20 },
      { label: 'Recent activity', value: breakdown['recentActivityWithin2Days'] ?? 0, max: 15 },
      { label: 'Requirement data', value: breakdown['locationRequirementOrNotes'] ?? 0, max: 10 },
    ];
  }

  private parseScoreBreakdown(lead: Lead | null | undefined): Record<string, any> | null {
    const raw = lead?.score_breakdown_json || lead?.gemini_analysis;
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw);
      return parsed?.formulaVersion === 'lead-score-v1' ? parsed : null;
    } catch {
      return null;
    }
  }
}
