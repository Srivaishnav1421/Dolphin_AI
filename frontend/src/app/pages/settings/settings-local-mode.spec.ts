import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';
import { Settings } from './settings';

describe('Settings local mode', () => {
  let fixture: ComponentFixture<Settings>;
  let component: Settings;
  let apiService: any;

  beforeEach(async () => {
    apiService = {
      getWorkspaceConfig: vi.fn().mockReturnValue(of({})),
      getWorkspaceTeam: vi.fn().mockReturnValue(of([])),
      getPipelineHealth: vi.fn().mockReturnValue(of({})),
      getWorkflowStats: vi.fn().mockReturnValue(of({})),
      getAiUsageStats: vi.fn().mockReturnValue(of({})),
      getRecentEvents: vi.fn().mockReturnValue(of([])),
      getRuntimeIdentity: vi.fn().mockReturnValue(of({
        profile: 'dev',
        localModeEnabled: true,
        fakeDataEnabled: false,
        mockAiEnabled: true,
        environment: 'local-dev',
        database: {
          connected: true,
          host: 'localhost',
          port: 5432,
          name: 'dolphindb',
          product: 'PostgreSQL',
          flyway: 'OK(35)'
        }
      })),
      updateWorkspaceConfig: vi.fn().mockReturnValue(of({})),
      getPaymentConfig: vi.fn(),
      getWallet: vi.fn(),
      getInvoices: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiService, useValue: apiService },
        { provide: AuthService, useValue: { currentUser: vi.fn().mockReturnValue({ role: 'OWNER' }) } },
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
        { provide: RuntimeConfigService, useValue: { apiBase: 'http://localhost:8000' } },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
  });

  it('disables active billing workflows in local mode', () => {
    fixture.detectChanges();
    component.setTab('billing');
    fixture.detectChanges();

    expect(component.localModeEnabled()).toBe(true);
    expect(apiService.getPaymentConfig).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Billing is disabled in local mode.');
    expect(fixture.nativeElement.textContent).not.toContain('Pay with UPI / Razorpay');
  });

  it('shows honest system runtime values from the backend', () => {
    fixture.detectChanges();
    component.setTab('system');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('dev');
    expect(text).toContain('dolphindb');
    expect(text).toContain('localhost:5432');
    expect(text).toContain('PostgreSQL');
    expect(text).toContain('OK(35)');
    expect(text).toContain('Local mode');
    expect(text).toContain('Mock AI');
    expect(text).toContain('local-dev');
  });
});
