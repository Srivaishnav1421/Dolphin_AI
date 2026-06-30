import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ApprovalItem } from '../../shared/models';
import { Approvals } from './approvals';

describe('Approvals page', () => {
  let fixture: ComponentFixture<Approvals>;
  let component: Approvals;
  let apiService: any;
  let authService: any;

  const approval: ApprovalItem = {
    id: 'approval-1',
    source_module: 'AD_BRAIN',
    action_type: 'PAUSE_CAMPAIGN',
    title: 'Pause campaign',
    description: 'Spend crossed the safety threshold.',
    recommendation_json: '{"reason":"threshold"}',
    math_snapshot_json: '{"spend":900}',
    severity: 'HIGH',
    status: 'PENDING',
    requires_execution: true,
    created_at: '2026-06-19T10:00:00',
    updated_at: '2026-06-19T10:00:00',
    execution_available: false,
  };

  beforeEach(async () => {
    apiService = {
      getApprovals: vi.fn().mockReturnValue(of([approval])),
      approveApprovalItem: vi.fn().mockReturnValue(of({})),
      rejectApprovalItem: vi.fn().mockReturnValue(of({})),
      executeApprovalItem: vi.fn().mockReturnValue(of({ message: 'Approved, execution integration not connected yet.' })),
    };
    authService = { currentUser: vi.fn().mockReturnValue({ role: 'OWNER' }) };

    await TestBed.configureTestingModule({
      imports: [Approvals],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: apiService },
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Approvals);
    component = fixture.componentInstance;
  });

  it('loads real approval API data', () => {
    fixture.detectChanges();

    expect(apiService.getApprovals).toHaveBeenCalled();
    expect(component.approvals()).toEqual([approval]);
    expect(fixture.nativeElement.textContent).toContain('Pause campaign');
  });

  it('shows empty state when approval API returns no rows', () => {
    apiService.getApprovals.mockReturnValue(of([]));
    fixture.detectChanges();

    expect(component.visibleApprovals()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('No pending approvals.');
  });

  it('shows error and retry when approval API fails', () => {
    apiService.getApprovals.mockReturnValue(throwError(() => new Error('down')));
    fixture.detectChanges();

    expect(component.error()).toContain('could not be loaded');
    expect(fixture.nativeElement.textContent).toContain('Retry');
  });

  it('approve and reject call global approval APIs', () => {
    fixture.detectChanges();

    component.approve(approval);
    expect(apiService.approveApprovalItem).toHaveBeenCalledWith('approval-1');

    component.reject(approval);
    expect(apiService.rejectApprovalItem).toHaveBeenCalledWith('approval-1', 'Rejected from Approval Queue');
  });

  it('viewer cannot approve or reject', () => {
    authService.currentUser.mockReturnValue({ role: 'VIEWER' });
    fixture.detectChanges();

    expect(component.canManageApprovals()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('cannot approve or reject');
  });
});
