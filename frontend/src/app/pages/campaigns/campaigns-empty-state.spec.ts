import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { Campaigns } from './campaigns';
import { ApiService } from '../../core/services/api.service';

/**
 * Campaigns Empty State Tests — Database Truth Enforcement
 *
 * Proves:
 * 1. Empty campaigns response from backend shows "No campaigns yet" — not fake campaigns
 * 2. API error shows error message — not fake campaign data
 * 3. Component never invents fake campaign records
 */
describe('Campaigns — Empty State Protection', () => {
  let fixture: ComponentFixture<Campaigns>;
  let component: Campaigns;
  let apiService: any;

  beforeEach(async () => {
    apiService = {
      getCampaigns: vi.fn(),
      getCampaignMathScore: vi.fn(),
      getCreatives: vi.fn(),
      getRecommendations: vi.fn(),
      createCampaign: vi.fn(),
      updateCampaign: vi.fn(),
      deleteCampaign: vi.fn(),
      evaluateCampaign: vi.fn(),
      pauseCampaign: vi.fn(),
      resumeCampaign: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [Campaigns],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiService, useValue: apiService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Campaigns);
    component = fixture.componentInstance;
  });

  // ── CASE 4: Empty DB → Empty Campaign List ────────────────────────────────

  it('CASE 4: campaigns signal is empty array when backend returns empty DB result', () => {
    apiService.getCampaigns.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.campaigns().length).toBe(0);
    expect(component.loading()).toBeFalsy();
  });

  it('CASE 4: no campaign is auto-selected when list is empty (no fake pre-selection)', () => {
    apiService.getCampaigns.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.selected()).toBeNull();
  });

  it('CASE 4: message shown on API error — not fake campaign data fallback', () => {
    apiService.getCampaigns.mockReturnValue(throwError(() => new Error('DB empty')));
    fixture.detectChanges();

    expect(component.campaigns().length).toBe(0);
    expect(component.message()).toContain('Could not load campaigns');
    expect(component.loading()).toBeFalsy();
  });

  it('CASE 4: evaluate() shows warning when no active campaigns (empty DB)', () => {
    apiService.getCampaigns.mockReturnValue(of([]));
    fixture.detectChanges();

    component.evaluate();

    expect(component.message()).toContain('No active campaigns to evaluate');
  });

  it('CASE 4: campaigns array never has fake hard-coded records on init', () => {
    apiService.getCampaigns.mockReturnValue(of([]));
    fixture.detectChanges();

    // campaigns() should be exactly [] — not pre-populated with fake data
    const campaigns = component.campaigns();
    expect(campaigns).toEqual([]);
    expect(campaigns.length).toBe(0);
  });

  it('CASE 4: creatives are empty when no campaigns (empty DB)', () => {
    apiService.getCampaigns.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.creatives().length).toBe(0);
  });

  it('CASE 4: recommendations are empty when no campaigns (empty DB)', () => {
    apiService.getCampaigns.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.recommendations().length).toBe(0);
  });
});
