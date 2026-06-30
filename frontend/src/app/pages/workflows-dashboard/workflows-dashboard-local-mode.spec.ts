import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../core/services/api.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { WorkflowsDashboard } from './workflows-dashboard';

describe('WorkflowsDashboard local mode', () => {
  let fixture: ComponentFixture<WorkflowsDashboard>;
  let component: WorkflowsDashboard;
  let apiService: any;

  beforeEach(async () => {
    apiService = {
      getRuntimeIdentity: vi.fn().mockReturnValue(of({
        profile: 'dev',
        localModeEnabled: true,
      })),
      getWorkflowExecutions: vi.fn().mockReturnValue(of([])),
      getWorkflowStats: vi.fn().mockReturnValue(of({
        activeCount: 0,
        completedCount: 0,
        failedCount: 0,
        averageDurationMs: 0,
        agentUsage: [],
      })),
      getWorkflowTemplates: vi.fn().mockReturnValue(of([])),
      getWorkflowApprovals: vi.fn().mockReturnValue(of([])),
      executeWorkflow: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [WorkflowsDashboard],
      providers: [
        { provide: ApiService, useValue: apiService },
        { provide: WebsocketService, useValue: { connected$: of(false), workflows$: of(null) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowsDashboard);
    component = fixture.componentInstance;
  });

  it('shows local-mode warning and prevents manual execution', () => {
    fixture.detectChanges();
    component.customMessage.set('Send WhatsApp follow-up');
    component.triggerCustomWorkflow();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Local approval-first mode is enabled.');
    expect(text).toContain('Manual automation execution is disabled');
    expect(text).toContain('No automation activity yet.');
    expect(apiService.executeWorkflow).not.toHaveBeenCalled();
  });
});
