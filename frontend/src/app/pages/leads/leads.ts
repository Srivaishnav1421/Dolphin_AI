import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Lead } from '../../shared/models';

type Filter = 'ALL' | 'HOT' | 'WARM' | 'COLD' | 'UNQUALIFIABLE';
type ViewMode = 'LIST' | 'KANBAN';
type PipelineStage = 'NEW_LEAD' | 'CONTACTED' | 'QUALIFIED' | 'INTERESTED' | 'PROPOSAL_SENT' | 'FOLLOW_UP' | 'NEGOTIATION' | 'CONVERTED' | 'LOST' | 'DORMANT' | 'RECYCLED';

interface InteractionLog {
  id: string;
  type: 'EMAIL' | 'CALL' | 'MEETING' | 'SYSTEM';
  notes: string;
  createdAt: Date;
}

@Component({
  selector: 'app-leads',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, FormsModule],
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
  viewMode       = signal<ViewMode>('KANBAN'); // Default to beautiful Kanban board

  // Interaction logs (emails, calls, meetings)
  interactions   = signal<InteractionLog[]>([]);
  newNoteText    = signal('');
  newNoteType    = signal<'EMAIL' | 'CALL' | 'MEETING'>('CALL');

  // Conversational SDR Chat Signals
  chatMessages   = signal<any[]>([]);
  newMessageText = signal('');
  sendingMessage = signal(false);

  filters: Filter[] = ['ALL', 'HOT', 'WARM', 'COLD', 'UNQUALIFIABLE'];
  kanbanStages: PipelineStage[] = ['NEW_LEAD', 'CONTACTED', 'QUALIFIED', 'INTERESTED', 'PROPOSAL_SENT', 'FOLLOW_UP', 'NEGOTIATION', 'CONVERTED', 'LOST'];

  form = { name: '', message: '', source: 'INSTAGRAM' };
  sources = ['INSTAGRAM', 'FACEBOOK', 'WEBSITE', 'WHATSAPP', 'FORM', 'REFERRAL'];

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    const f = this.filter() === 'ALL' ? undefined : this.filter();
    this.api.getLeads(f).subscribe({
      next: l => { this.leads.set(l); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  setFilter(f: Filter) { this.filter.set(f); this.selected.set(null); this.load(); }
  
  select(l: Lead) {
    const isSelected = this.selected()?.id === l.id;
    this.chatMessages.set([]);
    this.newMessageText.set('');
    this.interactions.set([]);
    this.newNoteText.set('');
    
    if (isSelected) {
      this.selected.set(null);
    } else {
      this.selected.set(l);
      this.loadChatHistory(l.id);
      this.loadInteractions(l);
    }
  }

  loadChatHistory(leadId: string) {
    this.api.getLeadChatHistory(leadId).subscribe({
      next: msgs => this.chatMessages.set(msgs),
      error: () => {}
    });
  }

  loadInteractions(l: Lead) {
    // Standard system-generated timeline entries
    const initialList: InteractionLog[] = [
      {
        id: '1',
        type: 'SYSTEM',
        notes: `Lead created from source: ${l.source || 'WEB'}`,
        createdAt: new Date(new Date(l.created_at).getTime() - 1000 * 60 * 5)
      },
      {
        id: '2',
        type: 'SYSTEM',
        notes: `AI scored lead: ${l.status} with intent tag: ${l.intent_signal || 'Low'}`,
        createdAt: new Date(l.created_at)
      }
    ];
    this.interactions.set(initialList);
  }

  addInteraction() {
    const txt = this.newNoteText().trim();
    if (!txt) return;
    const newLog: InteractionLog = {
      id: crypto.randomUUID(),
      type: this.newNoteType(),
      notes: txt,
      createdAt: new Date()
    };
    this.interactions.update(list => [...list, newLog]);
    this.newNoteText.set('');
  }

  sendChatMessage() {
    const lead = this.selected();
    const txt = this.newMessageText().trim();
    if (!lead || !txt) return;
    this.sendingMessage.set(true);
    this.api.sendLeadChatMessage(lead.id, txt).subscribe({
      next: () => {
        this.sendingMessage.set(false);
        this.newMessageText.set('');
        this.loadChatHistory(lead.id);
        
        // Add note to interaction timeline
        this.interactions.update(list => [...list, {
          id: crypto.randomUUID(),
          type: 'SYSTEM',
          notes: `Sent/replied via WhatsApp SDR Bot: "${txt}"`,
          createdAt: new Date()
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
      error: () => this.sendingMessage.set(false)
    });
  }

  openForm() {
    this.form = { name: '', message: '', source: 'INSTAGRAM' };
    this.message.set('');
    this.showForm.set(true);
  }
  closeForm() { this.showForm.set(false); }

  /** Submit lead → Gemini scores it instantly */
  submitLead() {
    if (!this.form.name || !this.form.message) {
      this.message.set('⚠️ Name and message are required.');
      return;
    }
    this.scoring.set(true);
    this.api.scoreLead(this.form).subscribe({
      next: (scored) => {
        this.message.set(`✅ Lead quality: ${scored.status} (${(scored.score * 100).toFixed(0)}%)`);
        this.scoring.set(false);
        this.showForm.set(false);
        this.filter.set('ALL');
        this.load();
        setTimeout(() => this.message.set(''), 4000);
      },
      error: () => { this.message.set('❌ Lead analysis failed. Please try again.'); this.scoring.set(false); },
    });
  }

  scoreBar(score: number)  { return Math.round(score * 100); }
  scoreColor(score: number) {
    if (score >= 0.7) return 'var(--danger)';
    if (score >= 0.4) return 'var(--warning)';
    return 'var(--info)';
  }

  count(status: Filter) {
    if (status === 'ALL') return this.leads().length;
    return this.leads().filter(l => l.status === status).length;
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
    let targetStatus: Lead['status'] = lead.status;
    if (['NEW_LEAD', 'CONTACTED'].includes(stage)) targetStatus = 'COLD';
    if (['QUALIFIED', 'INTERESTED', 'PROPOSAL_SENT', 'FOLLOW_UP', 'NEGOTIATION'].includes(stage)) targetStatus = 'WARM';
    if (stage === 'CONVERTED') targetStatus = 'HOT';
    if (stage === 'LOST') targetStatus = 'UNQUALIFIABLE';

    this.api.updateLead(lead.id, { status: targetStatus, pipeline_stage: stage }).subscribe({
      next: (updated) => {
        this.load();
        if (this.selected()?.id === lead.id) {
          this.selected.set(updated);
        }
      },
      error: () => {}
    });
  }

  // Next action suggestions
  getRecommendedAction(lead: Lead): string {
    if (lead.status === 'HOT') {
      return 'Schedule a demo call immediately & send commercial proposal.';
    }
    if (lead.status === 'WARM') {
      return 'Send marketing deck via WhatsApp and nurture via AI SDR.';
    }
    if (lead.status === 'COLD') {
      return 'Verify requirements via automated email survey.';
    }
    return 'Nurture with low-priority newsletter campaign.';
  }

  stageFromStatus(lead: Lead): PipelineStage {
    if (lead.status === 'HOT') return 'INTERESTED';
    if (lead.status === 'WARM') return 'QUALIFIED';
    if (lead.status === 'UNQUALIFIABLE') return 'LOST';
    return 'NEW_LEAD';
  }

  stageLabel(stage: PipelineStage): string {
    return stage.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }

  setViewMode(mode: ViewMode) {
    this.viewMode.set(mode);
  }
}
