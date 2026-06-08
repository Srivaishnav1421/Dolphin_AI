import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { WebsocketService } from '../../core/services/websocket.service';
import { Subscription } from 'rxjs';
import type { TraceGroupItem } from './trace-explorer';

@Component({
  selector: 'app-workflows-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    DecimalPipe,
    FormsModule,
  ],
  templateUrl: './workflows-dashboard.html',
  styleUrl: './workflows-dashboard.scss',
})
export class WorkflowsDashboard implements OnInit, OnDestroy {
  activeTab = signal<'executions' | 'templates' | 'approvals' | 'traces'>('executions');
  loading = signal(true);
  
  executions = signal<any[]>([]);
  templates = signal<any[]>([]);
  approvals = signal<any[]>([]);
  stats = signal<any>({
    activeCount: 0,
    completedCount: 0,
    failedCount: 0,
    averageDurationMs: 0,
    agentUsage: []
  });

  // Selected details
  selectedTraceId = signal<string | null>(null);
  selectedTraceExecutions = signal<any[]>([]);
  tracesList = signal<TraceGroupItem[]>([]);
  selectedStepIndex = signal<number>(0);
  
  // Custom execution trigger
  customMessage = signal('');
  submittingWorkflow = signal(false);

  // Approval response
  approvalReason = '';

  private sub: Subscription | null = null;

  constructor(private api: ApiService, private ws: WebsocketService) {}

  ngOnInit() {
    this.loadAll();
    this.ws.connected$.subscribe(connected => {
      if (connected) {
        this.sub = this.ws.workflows$.subscribe(event => {
          if (event && event.eventType) {
            this.handleRealtimeEvent(event);
          }
        });
      }
    });
  }

  ngOnDestroy() {
    if (this.sub) {
      this.sub.unsubscribe();
    }
  }

  loadAll() {
    this.loading.set(true);
    this.api.getWorkflowExecutions().subscribe({
      next: (data) => {
        this.executions.set(data.sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()));
        
        // Group by traceId to construct tracesList
        const traceMap = new Map<string, TraceGroupItem>();
        data.forEach(exec => {
          if (!traceMap.has(exec.traceId)) {
            traceMap.set(exec.traceId, {
              traceId: exec.traceId,
              name: exec.workflowName || 'Workflow Step',
              stepsCount: 0,
              status: 'COMPLETED',
              timestamp: exec.startTime
            });
          }
          const item = traceMap.get(exec.traceId)!;
          item.stepsCount++;
          if (exec.status === 'FAILED') item.status = 'FAILED';
          if (exec.status === 'WAITING_FOR_APPROVAL') item.status = 'WAITING_FOR_APPROVAL';
        });
        this.tracesList.set(Array.from(traceMap.values()));
        
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.api.getWorkflowStats().subscribe(data => {
      if (data) this.stats.set(data);
    });

    this.api.getWorkflowTemplates().subscribe(data => {
      if (data) this.templates.set(data);
    });

    this.api.getWorkflowApprovals().subscribe(data => {
      if (data) this.approvals.set(data);
    });
  }

  handleRealtimeEvent(evt: any) {
    // Add real-time event updates to UI
    this.executions.update(list => {
      const idx = list.findIndex(e => e.executionId === evt.executionId);
      if (idx !== -1) {
        const updated = { ...list[idx] };
        updated.status = this.getEventStatus(evt.eventType);
        if (evt.eventType === 'AGENT_SELECTED' && evt.details) {
          updated.agentUsed = evt.details.agent;
        }
        if (evt.eventType === 'WORKFLOW_COMPLETED' && evt.details) {
          updated.finalResponse = evt.details.response;
          updated.endTime = evt.timestamp;
        }
        if (evt.eventType === 'WORKFLOW_FAILED' && evt.details) {
          updated.errorLogs = evt.details.error;
          updated.endTime = evt.timestamp;
        }
        const newList = [...list];
        newList[idx] = updated;
        return newList;
      } else {
        // If it's a new workflow execution triggered, reload list
        this.loadAll();
        return list;
      }
    });

    // Refresh stats
    this.api.getWorkflowStats().subscribe(data => {
      if (data) this.stats.set(data);
    });

    // Refresh approvals list if any approvals are created/updated
    if (evt.eventType === 'WAITING_FOR_APPROVAL' || evt.eventType.startsWith('APPROVAL_')) {
      this.api.getWorkflowApprovals().subscribe(data => {
        if (data) this.approvals.set(data);
      });
    }
  }

  getEventStatus(type: string): string {
    switch (type) {
      case 'WORKFLOW_STARTED': return 'RUNNING';
      case 'WAITING_FOR_APPROVAL': return 'WAITING_FOR_APPROVAL';
      case 'WORKFLOW_COMPLETED': return 'COMPLETED';
      case 'WORKFLOW_FAILED': return 'FAILED';
      default: return 'RUNNING';
    }
  }

  triggerCustomWorkflow() {
    if (!this.customMessage().trim()) return;
    this.submittingWorkflow.set(true);
    this.api.executeWorkflow(this.customMessage()).subscribe({
      next: (exec) => {
        this.customMessage.set('');
        this.submittingWorkflow.set(false);
        this.loadAll();
      },
      error: () => this.submittingWorkflow.set(false)
    });
  }

  viewTrace(traceId: string) {
    this.selectedTraceId.set(traceId);
    this.selectedStepIndex.set(0);
    this.activeTab.set('traces');
    this.api.getTrace(traceId).subscribe(data => {
      this.selectedTraceExecutions.set(data.sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()));
    });
  }

  onPlaybackIndexChange(index: number) {
    this.selectedStepIndex.set(index);
  }

  onNodeSelected(nodeId: string) {
    const idx = this.selectedTraceExecutions().findIndex(step => step.id === nodeId);
    if (idx !== -1) {
      this.selectedStepIndex.set(idx);
    }
  }

  respondToApproval(id: string, decision: 'APPROVED' | 'REJECTED') {
    this.api.respondToApproval(id, decision, this.approvalReason || 'Decision by user').subscribe(() => {
      this.approvalReason = '';
      this.loadAll();
    });
  }

  getStatusBadgeClass(status: string) {
    if (!status) return 'badge-info';
    switch (status.toUpperCase()) {
      case 'COMPLETED': return 'badge-success';
      case 'FAILED': return 'badge-danger';
      case 'WAITING_FOR_APPROVAL': return 'badge-warning';
      case 'RUNNING': return 'badge-primary';
      default: return 'badge-info';
    }
  }
}
