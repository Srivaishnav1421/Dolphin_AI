import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../core/services/api.service';
import { Lead } from '../../shared/models';
import { Leads } from './leads';

describe('CRM Leads page', () => {
  let fixture: ComponentFixture<Leads>;
  let component: Leads;
  let apiService: any;

  const lead: Lead = {
    id: 'lead-1',
    name: 'Ravi Kumar',
    phone: '+919999999999',
    email: 'ravi@example.com',
    message: 'Need AI marketing automation',
    score: 0.65,
    status: 'NEW',
    temperature: 'WARM',
    pipeline_stage: 'QUALIFIED',
    budget_signal: null,
    timeline_signal: null,
    intent_signal: null,
    location_signal: null,
    source: 'WEBSITE',
    next_best_action: 'Send follow-up message',
    gemini_analysis: JSON.stringify({
      formulaVersion: 'lead-score-v1',
      phone: 20,
      email: 20,
      sourceOrCampaign: 15,
      interestedOrQualifiedStatus: 0,
      recentActivityWithin2Days: 0,
      locationRequirementOrNotes: 10,
      score: 65,
      temperature: 'WARM',
    }),
    created_at: '2026-06-19T10:00:00',
  };

  beforeEach(async () => {
    apiService = {
      getLeads: vi.fn().mockReturnValue(of([lead])),
      getIntegrationStatus: vi.fn().mockReturnValue(of({ whatsapp: { connected: false } })),
      getLeadChatHistory: vi.fn().mockReturnValue(of([])),
      getLeadInteractions: vi.fn().mockReturnValue(of([])),
      addLeadInteraction: vi.fn().mockReturnValue(of({ id: 'note-1', type: 'NOTE', details: 'note' })),
      createLead: vi.fn().mockReturnValue(of(lead)),
      scoreExistingLead: vi.fn().mockReturnValue(of({
        lead,
        score: 65,
        temperature: 'WARM',
        score_breakdown: JSON.parse(lead.gemini_analysis ?? '{}'),
        next_action: 'Send follow-up message',
      })),
      updateLeadStatus: vi.fn().mockReturnValue(of(lead)),
      submitLeadFollowupApproval: vi.fn().mockReturnValue(of({ approval: { id: 'approval-1' } })),
    };

    await TestBed.configureTestingModule({
      imports: [Leads],
      providers: [{ provide: ApiService, useValue: apiService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Leads);
    component = fixture.componentInstance;
  });

  it('renders leads from the API', () => {
    fixture.detectChanges();

    expect(apiService.getLeads).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Ravi Kumar');
    expect(fixture.nativeElement.textContent).toContain('65% score');
  });

  it('shows empty state when API returns no leads', () => {
    apiService.getLeads.mockReturnValue(of([]));
    fixture.detectChanges();
    component.setViewMode('LIST');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No leads found matching filters.');
  });

  it('shows error and retry surface on API failure', () => {
    apiService.getLeads.mockReturnValue(throwError(() => new Error('down')));
    fixture.detectChanges();

    expect(component.message()).toContain('Could not load CRM leads');
    expect(component.messageTone()).toBe('error');
  });

  it('renders Lead Score breakdown and next action for selected lead', () => {
    fixture.detectChanges();
    component.select(lead);
    fixture.detectChanges();

    expect(apiService.scoreExistingLead).toHaveBeenCalledWith('lead-1');
    expect(fixture.nativeElement.textContent).toContain('Lead Score');
    expect(fixture.nativeElement.textContent).toContain('Source/campaign');
    expect(fixture.nativeElement.textContent).toContain('Send follow-up message');
  });

  it('submit approval calls real Sales Closer API and does not show fake sent status', () => {
    fixture.detectChanges();
    component.select(lead);

    component.submitFollowupApproval();
    fixture.detectChanges();

    expect(apiService.submitLeadFollowupApproval).toHaveBeenCalledWith('lead-1');
    expect(component.message()).toContain('queued for approval');
    expect(fixture.nativeElement.textContent).not.toContain('WhatsApp sent');
    expect(fixture.nativeElement.textContent).not.toContain('call completed');
  });
});
