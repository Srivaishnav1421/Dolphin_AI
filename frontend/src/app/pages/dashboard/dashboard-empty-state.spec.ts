import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { Dashboard } from './dashboard';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';
import { provideRouter } from '@angular/router';
import { DashboardSummary } from '../../shared/models';

/**
 * Dashboard Empty State Tests — Database Truth Enforcement
 *
 * Proves that:
 * 1. Empty backend response (all-zero metrics) shows empty states — not fake revenue/leads
 * 2. API error response shows empty state — not fake fallback data
 * 3. Dashboard does not invent metrics when data is absent
 */
describe('Dashboard — Empty State Protection', () => {
  let fixture: ComponentFixture<Dashboard>;
  let component: Dashboard;
  let apiService: any;
  let authService: any;
  let wsService: any;

  const emptyDashboardResponse: DashboardSummary = {
    total_spend: 0,
    total_campaign_budget: 0,
    total_revenue: 0,
    blended_roas: 0,
    active_campaigns: 0,
    total_campaigns: 0,
    hot_leads: 0,
    warm_leads: 0,
    cold_leads: 0,
    wallet_balance: 0,
    meta_connected: false,
    pending_approvals: 0,
    automation: { active: 0, completed: 0, failed: 0, average_duration_ms: 0 },
    llm_status: {
      ollama: { enabled: true, available: true, model: 'llama3' },
      gemini: { enabled: false, model: '' },
      active_provider: 'ollama'
    },
    recent_events: []
  };

  beforeEach(async () => {
    apiService = {
      getDashboard: vi.fn(),
      getRecentEvents: vi.fn(),
      getRecommendations: vi.fn(),
      getPendingApprovals: vi.fn(),
      getAdBrainStatus: vi.fn(),
      runAdBrain: vi.fn(),
      approveApprovalItem: vi.fn(),
      rejectApprovalItem: vi.fn()
    };
    authService = {
      getToken: vi.fn(),
      currentUser: vi.fn()
    };
    wsService = {
      connected$: of(false),
      events$: of()
    };

    authService.getToken.mockReturnValue(null);
    authService.currentUser.mockReturnValue({ role: 'OWNER', account_id: 'test-ws' } as any);
    apiService.getRecentEvents.mockReturnValue(of([]));
    apiService.getRecommendations.mockReturnValue(of([]));
    apiService.getPendingApprovals.mockReturnValue(of([]));
    apiService.getAdBrainStatus.mockReturnValue(of(null));
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ApiService, useValue: apiService },
        { provide: AuthService, useValue: authService },
        { provide: WebsocketService, useValue: wsService },
        { provide: RuntimeConfigService, useValue: { apiBase: 'http://localhost:8000' } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
  });

  // ── CASE 4: Empty DB Response Shows Empty State ───────────────────────────

  it('CASE 4: shows zero revenue when backend returns empty DB metrics', () => {
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));
    fixture.detectChanges();

    const summary = component.summary();
    expect(summary).toBeTruthy();
    expect(summary!.total_revenue).toBe(0);
    expect(summary!.total_spend).toBe(0);
    expect(summary!.active_campaigns).toBe(0);
    expect(summary!.hot_leads).toBe(0);
    expect(summary!.warm_leads).toBe(0);
    expect(summary!.cold_leads).toBe(0);
    expect(summary!.blended_roas).toBe(0);
  });

  it('CASE 4: totalLeads() returns 0 when all lead counts are 0 from DB', () => {
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));
    fixture.detectChanges();

    expect(component.totalLeads()).toBe(0);
  });

  it('CASE 4: spendPct returns 0 when no budget data from empty DB', () => {
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));
    fixture.detectChanges();

    expect(component.spendPct).toBe(0);
  });

  it('CASE 4: opportunities and risks are empty when DB returns no brain decisions', () => {
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));
    apiService.getRecommendations.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.opportunities().length).toBe(0);
    expect(component.risks().length).toBe(0);
    expect(component.recommendations().length).toBe(0);
  });

  it('CASE 4: pending approvals are empty when approval queue returns no DB rows', () => {
    apiService.getPendingApprovals.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.pendingApprovals().length).toBe(0);
    expect(component.approvalsError()).toBeNull();
  });

  it('CASE 4: revenueHealthLabel shows "No spend yet" for zero-spend empty DB', () => {
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));
    fixture.detectChanges();

    const label = component.revenueHealthLabel(emptyDashboardResponse);
    expect(label).toBe('No spend yet');
  });

  it('CASE 4: summary remains null on API error (no fake fallback metrics)', () => {
    apiService.getDashboard.mockReturnValue(throwError(() => new Error('Backend unavailable')));
    fixture.detectChanges();

    // summary should remain null — not filled with fake data
    expect(component.summary()).toBeNull();
    expect(component.loading()).toBeFalsy();
  });

  it('CASE 4: events list is empty when DB has no brain events', () => {
    apiService.getDashboard.mockReturnValue(of(emptyDashboardResponse));
    apiService.getRecentEvents.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.events().length).toBe(0);
  });
});
