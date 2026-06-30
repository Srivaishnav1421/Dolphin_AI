import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { RuntimeConfigService } from './runtime-config.service';
import { AuthService } from './auth.service';

describe('ApiService database truth behavior', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RuntimeConfigService, useValue: { apiBase: 'http://api.test' } },
        { provide: AuthService, useValue: { currentUser: () => ({ account_id: 'ws-1' }) } },
      ],
    });

    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('propagates dashboard API failures instead of returning fake metrics', () => {
    expectFailure(() => api.getDashboard(), 'http://api.test/api/dashboard/summary');
  });

  it('propagates CRM lead API failures instead of returning fake leads', () => {
    expectFailure(() => api.getLeads(), 'http://api.test/api/leads');
  });

  it('propagates campaign API failures instead of returning fake campaigns', () => {
    expectFailure(() => api.getCampaigns(), 'http://api.test/api/campaigns');
  });

  it('propagates analytics API failures instead of returning fake charts', () => {
    expectFailure(() => api.getEmasMetrics('ws-1'), 'http://api.test/api/emas/ws-1');
  });

  function expectFailure(call: () => any, url: string) {
    let nextCalled = false;
    let errorStatus = 0;

    call().subscribe({
      next: () => {
        nextCalled = true;
      },
      error: (err: any) => {
        errorStatus = err.status;
      },
    });

    http.expectOne(url)
      .flush({ message: 'Database unavailable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(nextCalled).toBe(false);
    expect(errorStatus).toBe(503);
  }
});
