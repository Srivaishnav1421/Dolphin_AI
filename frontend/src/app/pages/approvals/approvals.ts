import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ApprovalItem } from '../../shared/models';
import { AppIcon } from '../../shared/ui';

type ApprovalTab = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL';

@Component({
  selector: 'app-approvals',
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule, AppIcon],
  templateUrl: './approvals.html',
  styleUrl: './approvals.scss',
})
export class Approvals implements OnInit {
  approvals = signal<ApprovalItem[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  message = signal<string | null>(null);
  activeTab = signal<ApprovalTab>('PENDING');
  busyId = signal<string | null>(null);
  expandedId = signal<string | null>(null);
  rejectReasons: Record<string, string> = {};

  visibleApprovals = computed(() => {
    const tab = this.activeTab();
    const list = this.approvals();
    if (tab === 'ALL') return list;
    return list.filter(item => item.status === tab);
  });

  pendingCount = computed(() => this.approvals().filter(item => item.status === 'PENDING').length);
  approvedCount = computed(() => this.approvals().filter(item => item.status === 'APPROVED').length);
  rejectedCount = computed(() => this.approvals().filter(item => item.status === 'REJECTED').length);

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getApprovals().subscribe({
      next: approvals => {
        this.approvals.set(approvals ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.approvals.set([]);
        this.loading.set(false);
        this.error.set('Approval Queue could not be loaded from the backend.');
      }
    });
  }

  setTab(tab: ApprovalTab) {
    this.activeTab.set(tab);
  }

  toggleDetails(id: string) {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  canManageApprovals() {
    const role = this.auth.currentUser()?.role;
    return role === 'OWNER' || role === 'ADMIN' || role === 'MANAGER';
  }

  canExecuteApprovals() {
    const role = this.auth.currentUser()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  }

  approve(item: ApprovalItem) {
    if (!this.canManageApprovals()) return;
    this.busyId.set(item.id);
    this.api.approveApprovalItem(item.id).subscribe({
      next: () => {
        this.message.set('Approval item approved.');
        this.busyId.set(null);
        this.load();
      },
      error: () => {
        this.error.set('Could not approve this item.');
        this.busyId.set(null);
      }
    });
  }

  reject(item: ApprovalItem) {
    if (!this.canManageApprovals()) return;
    this.busyId.set(item.id);
    this.api.rejectApprovalItem(item.id, this.rejectReasons[item.id] || 'Rejected from Approval Queue').subscribe({
      next: () => {
        this.message.set('Approval item rejected.');
        this.busyId.set(null);
        this.load();
      },
      error: () => {
        this.error.set('Could not reject this item.');
        this.busyId.set(null);
      }
    });
  }

  execute(item: ApprovalItem) {
    if (!this.canExecuteApprovals() || item.status !== 'APPROVED') return;
    this.busyId.set(item.id);
    this.api.executeApprovalItem(item.id).subscribe({
      next: response => {
        this.message.set(response?.message || 'Execution request processed.');
        this.busyId.set(null);
        this.load();
      },
      error: () => {
        this.error.set('Could not execute this item.');
        this.busyId.set(null);
      }
    });
  }

  prettyJson(value?: string | null): string {
    if (!value) return 'No data recorded.';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }

  mathSnapshot(item: ApprovalItem): any {
    if (!item.math_snapshot_json) return null;
    try {
      const snapshot = JSON.parse(item.math_snapshot_json);
      if (typeof snapshot.inputSnapshotJson === 'string' && snapshot.inputSnapshotJson.trim()) {
        try {
          snapshot.inputSnapshot = JSON.parse(snapshot.inputSnapshotJson);
        } catch {
          snapshot.inputSnapshot = null;
        }
      }
      return snapshot;
    } catch {
      return null;
    }
  }

  mathReason(item: ApprovalItem): string {
    const snapshot = this.mathSnapshot(item);
    return snapshot?.reason || item.description || 'No math reason recorded.';
  }

  metricProof(item: ApprovalItem): Array<{ label: string; value: string }> {
    const input = this.mathSnapshot(item)?.inputSnapshot;
    if (!input) return [];
    const fields: Array<[string, string]> = [
      ['Spent', 'spent'],
      ['Conversions', 'conversions'],
      ['CTR', 'ctrPercent'],
      ['CPC', 'cpc'],
      ['CPL', 'actualCpl'],
      ['Target CPL', 'targetCpl'],
      ['Age hours', 'campaignAgeHours'],
      ['Threshold', 'minimumSpend'],
      ['Rule hours', 'ruleHours'],
    ];
    return fields
      .map(([label, key]) => ({ label, value: input[key] }))
      .filter(row => row.value !== undefined && row.value !== null && row.value !== '')
      .map(row => ({ label: row.label, value: String(row.value) }));
  }

  severityClass(severity: string) {
    return {
      LOW: 'status-connected',
      MEDIUM: 'status-needs-validation',
      HIGH: 'status-needs-setup',
      CRITICAL: 'status-error',
    }[severity] ?? 'status-needs-validation';
  }

  statusClass(status: string) {
    return {
      PENDING: 'status-needs-validation',
      APPROVED: 'status-connected',
      REJECTED: 'status-error',
      EXPIRED: 'status-needs-setup',
      EXECUTED: 'status-connected',
      FAILED: 'status-error',
    }[status] ?? 'status-needs-validation';
  }
}
