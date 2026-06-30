import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { Analytics } from './analytics';
import { ApiService } from '../../core/services/api.service';
import { AnalyticsSummary } from '../../shared/models';

/**
 * Analytics Empty State Tests — Database Truth Enforcement
 *
 * Proves:
 * 1. Empty analytics response shows "No analytics data yet" empty state — not fake charts
 * 2. API error shows message — not fake chart data as fallback
 * 3. Component never pre-fills metrics with invented/fake data
 */
describe('Analytics — Empty State Protection', () => {
  let fixture: ComponentFixture<Analytics>;
  let component: Analytics;
  let apiService: any;

  const emptySummary: AnalyticsSummary = {
    workspace_id: 'test-workspace-id',
    generated_at: '2026-06-23T00:00:00',
    campaign_summary: {
      total: 0,
      active: 0,
      paused: 0,
      completed: 0,
      total_budget: 0,
      total_spend: 0,
      recorded_attributed_revenue: 0,
      average_roas: 0,
      average_cpl: 0,
      source_table: 'campaigns',
      empty: true
    },
    lead_summary: {
      total: 0,
      new_leads: 0,
      hot: 0,
      warm: 0,
      cold: 0,
      unknown_temperature: 0,
      average_score: 0,
      source_table: 'leads',
      empty: true
    },
    approval_summary: {
      total: 0,
      pending: 0,
      approved: 0,
      rejected: 0,
      requires_execution: 0,
      source_table: 'approval_items',
      empty: true
    },
    content_factory_summary: {
      items: 0,
      variants: 0,
      draft_variants: 0,
      submitted_variants: 0,
      approved_variants: 0,
      average_score: 0,
      source_tables: ['content_factory_items', 'content_factory_variants'],
      empty: true
    },
    ad_brain_summary: {
      runs: 0,
      latest_run_at: null,
      campaigns_evaluated: 0,
      evaluations_created: 0,
      approvals_created: 0,
      duplicate_approvals_skipped: 0,
      risks_created: 0,
      opportunities_created: 0,
      source_table: 'ad_brain_runs',
      empty: true
    },
    risk_opportunity_summary: {
      math_evaluations: 0,
      requires_approval: 0,
      critical: 0,
      high: 0,
      not_enough_data: 0,
      latest_evaluation_at: null,
      source_table: 'campaign_math_evaluations',
      empty: true
    },
    empty_state: {
      is_empty: true,
      message: 'No analytics data exists yet for this workspace.'
    },
    read_only: true
  };

  beforeEach(async () => {
    apiService = {
      getAnalyticsSummary: vi.fn()
    };
    apiService.getAnalyticsSummary.mockReturnValue(of(emptySummary));

    await TestBed.configureTestingModule({
      imports: [Analytics],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiService, useValue: apiService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Analytics);
    component = fixture.componentInstance;
  });

  // ── CASE 4: Empty DB → Empty Analytics State ──────────────────────────────

  it('CASE 4: metrics remain null when API returns error (no fake chart data fallback)', () => {
    apiService.getAnalyticsSummary.mockReturnValue(throwError(() => new Error('No data in empty DB')));
    fixture.detectChanges();

    // Must stay null — NOT replaced with fake/invented metrics
    expect(component.summary()).toBeNull();
    expect(component.loading()).toBeFalsy();
  });

  it('CASE 4: error message shown when analytics API fails (not silent fake data)', () => {
    apiService.getAnalyticsSummary.mockReturnValue(throwError(() => new Error('Backend error')));
    fixture.detectChanges();

    expect(component.message()).toContain('Could not load analytics');
  });

  it('CASE 4: all-zero summary from real empty DB is shown as-is (not inflated)', () => {
    apiService.getAnalyticsSummary.mockReturnValue(of(emptySummary));
    fixture.detectChanges();

    const summary = component.summary();
    expect(summary).toBeTruthy();
    expect(summary!.campaign_summary.total).toBe(0);
    expect(summary!.lead_summary.total).toBe(0);
    expect(summary!.approval_summary.total).toBe(0);
    expect(summary!.content_factory_summary.variants).toBe(0);
    expect(summary!.ad_brain_summary.runs).toBe(0);
    expect(summary!.risk_opportunity_summary.math_evaluations).toBe(0);
    expect(summary!.empty_state.is_empty).toBe(true);
  });

  it('CASE 4: roasHealth() handles zero ROAS correctly (danger color — not fake healthy)', () => {
    fixture.detectChanges();

    const color = component.roasHealth(0);
    // Zero ROAS should show danger color — not green fake healthy state
    expect(color).toBe('var(--danger)');
  });

  it('CASE 4: approvalHealth() handles zero pending approvals correctly', () => {
    fixture.detectChanges();

    const color = component.approvalHealth(0);
    expect(color).toBe('var(--success)');
  });

  it('CASE 4: component does not auto-populate summary before API call returns', () => {
    // Before any API response, metrics should be null (no fake pre-population)
    apiService.getAnalyticsSummary.mockReturnValue(of(emptySummary));
    // Do NOT call fixture.detectChanges() yet — check initial state
    expect(component.summary()).toBeNull();
    expect(component.loading()).toBeTruthy();
  });

  it('CASE 4: analytics baseline uses a read-only GET API only', () => {
    fixture.detectChanges();

    expect(apiService.getAnalyticsSummary).toHaveBeenCalled();
    expect((component as any).runArbitrage).toBeUndefined();
  });
});
