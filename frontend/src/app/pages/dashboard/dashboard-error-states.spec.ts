import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { HttpClient } from '@angular/common/http';

import { Dashboard } from './dashboard';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';
import { DashboardSummary } from '../../shared/models';

/**
 * Growth Home Error State Tests — Issue 1 & Issue 2 verification
 *
 * Required by sprint:
 * 1. Dashboard summary API failure shows error banner.
 * 2. Dashboard summary API failure does not render fake metrics.
 * 3. Retry button (loadData) clears error and re-fetches.
 * 4. Integration status API failure defaults cards to "Needs setup."
 * 5. Integration status API failure does not show "Connected."
 * 6. Integration status API failure shows non-blocking warning.
 * 7. Empty successful dashboard response shows honest zero/empty state.
 */
describe('Dashboard — Error States & Resilience', () => {
  let fixture: ComponentFixture<Dashboard>;
  let component: Dashboard;
  let apiService: any;
  let authService: any;
  let wsService: any;
  let httpClientSpy: any;

  const realEmptySummary: DashboardSummary = {
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
      active_provider: 'ollama',
    },
    recent_events: [],
  };

  const setupTestBed = async (httpGetImpl?: (url: string) => any) => {
    apiService = {
      getDashboard: vi.fn().mockReturnValue(of(realEmptySummary)),
      getRecentEvents: vi.fn().mockReturnValue(of([])),
      getRecommendations: vi.fn().mockReturnValue(of([])),
      getPendingApprovals: vi.fn().mockReturnValue(of([])),
      getAdBrainStatus: vi.fn().mockReturnValue(of(null)),
      runAdBrain: vi.fn().mockReturnValue(of({
        run_id: 'run-1',
        status: 'COMPLETED',
        campaigns_evaluated: 1,
        evaluations_created: 3,
        approval_items_created: 1,
        duplicate_approvals_skipped: 0,
        risks_created: 1,
        opportunities_created: 0,
        started_at: '2026-06-18T10:00:00',
        completed_at: '2026-06-18T10:00:01',
        message: 'Ad Brain completed. 1 actions require approval.',
      })),
      approveRecommendation: vi.fn(),
      rejectRecommendation: vi.fn(),
      approveApprovalItem: vi.fn().mockReturnValue(of({})),
      rejectApprovalItem: vi.fn().mockReturnValue(of({})),
    };
    authService = {
      getToken: vi.fn().mockReturnValue(null),
      currentUser: vi.fn().mockReturnValue({ role: 'OWNER', account_id: 'ws-test-id' }),
    };
    wsService = {
      connected$: of(false),
      events$: new Subject(),
      connect: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ApiService, useValue: apiService },
        { provide: AuthService, useValue: authService },
        { provide: WebsocketService, useValue: wsService },
        { provide: RuntimeConfigService, useValue: { apiBase: 'http://localhost:8000' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;

    // Override HttpClient.get for integration status endpoint
    if (httpGetImpl) {
      const http = TestBed.inject(HttpClient);
      vi.spyOn(http, 'get').mockImplementation((url: string) => httpGetImpl(url));
    }
  };

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 1 — Dashboard summary API failure shows error banner (loadError set)
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 1: loadError is set when getDashboard() fails', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : throwError(() => new Error('net err'))
    );
    apiService.getDashboard.mockReturnValue(throwError(() => new Error('500 Server Error')));

    fixture.detectChanges();

    expect(component.loadError()).toBeTruthy();
    expect(component.loadError()).toContain('Could not load dashboard data');
    expect(component.loading()).toBe(false);
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 2 — Dashboard summary API failure does NOT render fake metrics
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 2: summary() stays null when getDashboard() fails — no fake metrics', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : throwError(() => new Error(''))
    );
    apiService.getDashboard.mockReturnValue(throwError(() => new Error('Backend down')));

    fixture.detectChanges();

    // summary must be null — the component must not fall back to invented data
    expect(component.summary()).toBeNull();

    // Computed helpers must return zero/empty when summary is null, not fake values
    expect(component.totalLeads()).toBe(0);
    expect(component.spendPct).toBe(0);
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 3 — Retry button: loadData() clears error and re-fetches
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 3: calling loadData() clears loadError and triggers new API request', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : throwError(() => new Error(''))
    );
    apiService.getDashboard
      .mockReturnValueOnce(throwError(() => new Error('First fail')))
      .mockReturnValueOnce(of(realEmptySummary));

    fixture.detectChanges(); // First call — fails
    expect(component.loadError()).toBeTruthy();

    component.loadData(); // Retry — succeeds
    fixture.detectChanges();

    expect(component.loadError()).toBeNull();
    expect(component.summary()).not.toBeNull();
    expect(apiService.getDashboard).toHaveBeenCalledTimes(2);
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 4 — Integration status API failure defaults all cards to "needs-setup"
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 4: integration status API failure sets all providers to needs-setup', async () => {
    await setupTestBed((url) => {
      if (url.includes('/api/integrations/status')) {
        return throwError(() => new Error('503 Integration API down'));
      }
      return of([]);
    });

    fixture.detectChanges();

    // Every card shown on Growth Home must be needs-setup — never connected or error
    expect(component.integrationStatus('meta')).toBe('needs-setup');
    expect(component.integrationStatus('whatsapp')).toBe('needs-setup');
    expect(component.integrationStatus('google')).toBe('needs-setup');
    expect(component.integrationStatus('ai')).toBe('needs-setup');
    expect(component.integrationStatus('n8n')).toBe('needs-setup');
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 5 — Integration status API failure does NOT show "Connected" for any card
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 5: integration status API failure never shows connected for any provider', async () => {
    await setupTestBed((url) => {
      if (url.includes('/api/integrations/status')) {
        return throwError(() => new Error('API unavailable'));
      }
      return of([]);
    });

    fixture.detectChanges();

    const allProviders = ['meta', 'whatsapp', 'google', 'ai', 'n8n'];
    allProviders.forEach((key) => {
      expect(component.integrationStatus(key)).not.toBe('connected');
    });
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 6 — Integration status API failure shows non-blocking warning message
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 6: integrationStatusError is set when integration API fails', async () => {
    await setupTestBed((url) => {
      if (url.includes('/api/integrations/status')) {
        return throwError(() => new Error('Timeout'));
      }
      return of([]);
    });

    fixture.detectChanges();

    expect(component.integrationStatusError()).toBeTruthy();
    expect(component.integrationStatusError()).toContain(
      'Integration status could not be refreshed'
    );
  });

  it('TEST 6b: integrationStatusError is cleared when integration API succeeds', async () => {
    let callCount = 0;
    await setupTestBed((url) => {
      if (url.includes('/api/integrations/status')) {
        callCount++;
        if (callCount === 1) return throwError(() => new Error('First failure'));
        return of({ meta: { connected: true, validationStatus: 'VALIDATED', configured: true } });
      }
      return of([]);
    });

    fixture.detectChanges(); // First call fails — error set
    expect(component.integrationStatusError()).toBeTruthy();

    component.loadData(); // Second call succeeds — error cleared
    fixture.detectChanges();

    expect(component.integrationStatusError()).toBeNull();
  });

  it('loads pending approvals from the global approval queue endpoint', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    apiService.getPendingApprovals.mockReturnValue(of([
      {
        id: 'approval-1',
        source_module: 'CAMPAIGN',
        action_type: 'PAUSE_CAMPAIGN',
        title: 'Pause campaign',
        description: 'Review campaign spend',
        severity: 'HIGH',
        status: 'PENDING',
        requires_execution: true,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
        execution_available: false,
      }
    ]));

    fixture.detectChanges();

    expect(apiService.getPendingApprovals).toHaveBeenCalled();
    expect(component.pendingApprovals().length).toBe(1);
    expect(component.pendingApprovals()[0].title).toBe('Pause campaign');
  });

  it('shows approval API failure state and renders no fake approvals', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    apiService.getPendingApprovals.mockReturnValue(throwError(() => new Error('approval api down')));

    fixture.detectChanges();

    expect(component.pendingApprovals().length).toBe(0);
    expect(component.approvalsError()).toContain('Could not load pending approvals');
  });

  it('approveApproval calls the global approval endpoint', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    fixture.detectChanges();

    component.approveApproval('approval-1');

    expect(apiService.approveApprovalItem).toHaveBeenCalledWith('approval-1');
  });

  it('rejectApproval calls the global approval endpoint', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    fixture.detectChanges();

    component.rejectApproval('approval-1');

    expect(apiService.rejectApprovalItem).toHaveBeenCalledWith('approval-1');
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // TEST 7 — Empty successful API response shows honest zero/empty state
  // ─────────────────────────────────────────────────────────────────────────────
  it('TEST 7: empty DB response shows zero metrics — no fake data injected', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    apiService.getDashboard.mockReturnValue(of(realEmptySummary));

    fixture.detectChanges();

    // Summary is populated with real DB zeros — not null, not fake
    const s = component.summary()!;
    expect(s).not.toBeNull();
    expect(s.total_revenue).toBe(0);
    expect(s.total_spend).toBe(0);
    expect(s.active_campaigns).toBe(0);
    expect(s.hot_leads).toBe(0);
    expect(s.blended_roas).toBe(0);

    // Computed helpers correctly return zero
    expect(component.totalLeads()).toBe(0);
    expect(component.spendPct).toBe(0);
    expect(component.revenueHealthLabel(s)).toBe('No spend yet');

    // No error state shown
    expect(component.loadError()).toBeNull();

    // No fake opportunities or risks
    expect(component.opportunities().length).toBe(0);
    expect(component.risks().length).toBe(0);
    expect(component.recommendations().length).toBe(0);
    expect(component.events().length).toBe(0);
  });

  it('TEST 7b: integrationStatus() returns needs-setup by default for unknown keys', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );

    fixture.detectChanges();

    // Unknown key must default to needs-setup, not error or connected
    expect(component.integrationStatus('unknown-provider')).toBe('needs-setup');
  });

  it('shows the Run Ad Brain button on Growth Home', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Run Ad Brain');
  });

  it('clicking Run Ad Brain calls POST /api/ad-brain/run through ApiService', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    fixture.detectChanges();

    component.runAdBrain();

    expect(apiService.runAdBrain).toHaveBeenCalled();
  });

  it('shows loading state while Ad Brain is running', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    const runSubject = new Subject<any>();
    apiService.runAdBrain.mockReturnValue(runSubject.asObservable());
    fixture.detectChanges();

    component.runAdBrain();
    fixture.detectChanges();

    expect(component.adBrainRunning()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Running Ad Brain');
  });

  it('shows success summary and reloads pending approvals after Ad Brain completes', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    fixture.detectChanges();
    const beforeCalls = apiService.getPendingApprovals.mock.calls.length;

    component.runAdBrain();
    fixture.detectChanges();

    expect(component.adBrainMessage()).toContain('Ad Brain completed');
    expect(apiService.getPendingApprovals.mock.calls.length).toBeGreaterThan(beforeCalls);
  });

  it('shows error state and does not invent approvals when Ad Brain run fails', async () => {
    await setupTestBed((url) =>
      url.includes('/api/integrations/status') ? of({}) : of([])
    );
    apiService.runAdBrain.mockReturnValue(throwError(() => new Error('run failed')));
    fixture.detectChanges();

    component.runAdBrain();
    fixture.detectChanges();

    expect(component.adBrainError()).toContain('No campaign actions were executed');
    expect(component.pendingApprovals().length).toBe(0);
  });
});
