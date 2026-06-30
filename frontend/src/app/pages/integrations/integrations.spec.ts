import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';
import { Integrations } from './integrations';

describe('Integrations local-mode status', () => {
  let fixture: ComponentFixture<Integrations>;
  let component: Integrations;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Integrations],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RuntimeConfigService, useValue: { apiBase: 'http://api.test' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Integrations);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('shows real runtime and AI status with local-mode warning', () => {
    flushInitialStatus();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Local approval-first mode is enabled');
    expect(text).toContain('dolphindb');
    expect(text).toContain('MOCK');
    expect(text).toContain('Missing credentials are shown as Needs setup');
  });

  it('blocks live AI provider validation in local mode without calling the backend test endpoint', () => {
    flushInitialStatus({
      integrations: {
        openai: {
          configured: true,
          connected: false,
          validationStatus: 'PENDING_VALIDATION',
          validationMessage: 'Credentials stored. Run Test connection to verify live access.',
          maskedKeys: { api_key: '••••••••1234' },
        },
      },
    });
    fixture.detectChanges();

    const openAi = component.providers().find(provider => provider.id === 'openai');
    expect(openAi).toBeTruthy();
    expect(component.liveValidationBlocked(openAi!)).toBe(true);

    component.testConnection(openAi!);
    http.expectNone('http://api.test/api/integrations/openai/test');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Live validation is disabled in local approval-first mode.');
  });

  function flushInitialStatus(options?: { integrations?: Record<string, any> }) {
    http.expectOne('http://api.test/api/system/runtime').flush({
      profile: 'dev',
      localModeEnabled: true,
      mockAiEnabled: true,
      database: { connected: true, name: 'dolphindb', flyway: 'OK(42)' },
    });
    http.expectOne('http://api.test/api/admin/ai-infrastructure/providers').flush({
      activeProvider: 'MOCK',
      providers: [{ provider: 'MOCK', usable: true }],
    });
    http.expectOne('http://api.test/api/integrations/status').flush(options?.integrations || {});
  }
});
