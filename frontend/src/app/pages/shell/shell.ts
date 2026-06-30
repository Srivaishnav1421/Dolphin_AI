import { Component, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { StateService } from '../../core/services/state.service';
import { CommandService } from '../../core/services/command.service';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';
import { ApiService } from '../../core/services/api.service';
import { UiModal, UiCommandPalette, AppIcon } from '../../shared/ui';
import { WorkspaceOption } from '../../shared/models';
import { filter } from 'rxjs';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    CommonModule,
    FormsModule,
    UiModal,
    UiCommandPalette,
    AppIcon
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  workspaces = signal<WorkspaceOption[]>([]);
  workspaceLoading = signal(false);
  workspaceError = signal('');
  aiRouting = signal<any | null>(null);
  aiRoutingError = signal('');
  aiRoutingSaving = signal(false);
  currentTaskKey = signal('GROWTH_HOME');
  currentTaskLabel = signal('Growth Home');

  constructor(
    public auth: AuthService,
    private http: HttpClient,
    public state: StateService,
    private command: CommandService, // Auto-initializes global keyboard listener
    private config: RuntimeConfigService,
    private api: ApiService,
    private router: Router
  ) {
    this.loadWorkspaces();
    this.updateCurrentTask(this.router.url);
    this.loadAiRouting();
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe((event) => {
      this.updateCurrentTask((event as NavigationEnd).urlAfterRedirects);
    });
  }

  navItems = [
    { path: '/dashboard', label: 'Growth Home', icon: 'grid_view' },
    { path: '/leads',     label: 'CRM',         icon: 'group' },
    { path: '/campaigns', label: 'Campaigns',   icon: 'campaign' },
    { path: '/creatives', label: 'Creative Studio', icon: 'palette' },
    { path: '/ad-brain',  label: 'AI Insights', icon: 'psychology' },
    { path: '/automation', label: 'Automation',  icon: 'auto_awesome' },
    { path: '/analytics', label: 'Analytics',   icon: 'analytics' },
    { path: '/integrations', label: 'Integrations', icon: 'key' },
    { path: '/settings',  label: 'Settings',    icon: 'settings' }
  ];

  // ── Password Change ──────────────────────────────────────────────
  showPwdModal = signal(false);
  pwdSaving    = signal(false);
  pwdMsg       = signal('');
  pwd = { old: '', new_pwd: '', confirm: '' };

  openPwdModal()  { this.pwd = { old: '', new_pwd: '', confirm: '' }; this.pwdMsg.set(''); this.showPwdModal.set(true); }
  closePwdModal() { this.showPwdModal.set(false); }

  toggleSidebar() {
    this.state.isSidebarExpanded.update(val => !val);
  }

  loadWorkspaces() {
    this.workspaceLoading.set(true);
    this.workspaceError.set('');
    this.http.get<WorkspaceOption[]>(`${this.config.apiBase}/api/workspaces`).subscribe({
      next: (workspaces) => {
        this.workspaces.set(workspaces);
        this.workspaceLoading.set(false);
      },
      error: () => {
        this.workspaceError.set('Unable to load client workspaces.');
        this.workspaceLoading.set(false);
      }
    });
  }

  activeWorkspaceId() {
    return this.auth.currentUser()?.account_id ?? '';
  }

  switchWorkspace(workspaceId: string) {
    if (!workspaceId || workspaceId === this.activeWorkspaceId()) {
      return;
    }
    this.workspaceLoading.set(true);
    this.auth.switchWorkspace(workspaceId).subscribe({
      next: () => {
        this.workspaceLoading.set(false);
        this.loadWorkspaces();
        this.loadAiRouting();
      },
      error: () => {
        this.workspaceError.set('Access denied for that client workspace.');
        this.workspaceLoading.set(false);
      }
    });
  }

  changePassword() {
    if (!this.pwd.old || !this.pwd.new_pwd) { this.pwdMsg.set('⚠️ Fill all fields.'); return; }
    if (this.pwd.new_pwd !== this.pwd.confirm) { this.pwdMsg.set('⚠️ Passwords do not match.'); return; }
    if (this.pwd.new_pwd.length < 6) { this.pwdMsg.set('⚠️ Min 6 characters.'); return; }

    this.pwdSaving.set(true);
    this.http.post(`${this.config.apiBase}/api/auth/change-password`, {
      old_password: this.pwd.old,
      new_password: this.pwd.new_pwd,
    }).subscribe({
      next: () => {
        this.pwdMsg.set('✅ Password changed! Please login again.');
        this.pwdSaving.set(false);
        setTimeout(() => { this.auth.logout(); }, 2000);
      },
      error: (e) => { this.pwdMsg.set('❌ ' + (e.error?.error ?? 'Failed.')); this.pwdSaving.set(false); },
    });
  }

  logout() { this.auth.logout(); }

  canManageAiRouting() {
    const role = (this.auth.currentUser()?.role || '').toUpperCase();
    return ['OWNER', 'ADMIN', 'SYSTEM_ADMIN'].includes(role);
  }

  usableProviders() {
    return (this.aiRouting()?.providers || []).filter((provider: any) => provider.usable);
  }

  defaultAiProvider() {
    return this.aiRouting()?.defaultProvider || 'DEFAULT';
  }

  selectedAiProvider() {
    const routes = this.aiRouting()?.taskRoutes || {};
    return routes[this.currentTaskKey()] || 'DEFAULT';
  }

  selectedAiLabel() {
    const selected = this.selectedAiProvider();
    if (selected === 'DEFAULT') {
      return `Default: ${this.defaultAiProvider()}`;
    }
    return selected;
  }

  onTaskProviderChange(provider: string) {
    if (!this.canManageAiRouting()) return;
    this.aiRoutingSaving.set(true);
    this.aiRoutingError.set('');
    this.api.updateAiTaskRoute(this.currentTaskKey(), provider).subscribe({
      next: () => {
        this.aiRoutingSaving.set(false);
        this.loadAiRouting();
      },
      error: (err) => {
        this.aiRoutingSaving.set(false);
        this.aiRoutingError.set(err?.error?.error || 'Unable to update AI route.');
      }
    });
  }

  onDefaultProviderChange(provider: string) {
    if (!this.canManageAiRouting()) return;
    this.aiRoutingSaving.set(true);
    this.aiRoutingError.set('');
    this.api.updateDefaultAiProvider(provider).subscribe({
      next: () => {
        this.aiRoutingSaving.set(false);
        this.loadAiRouting();
      },
      error: (err) => {
        this.aiRoutingSaving.set(false);
        this.aiRoutingError.set(err?.error?.error || 'Unable to update default AI provider.');
      }
    });
  }

  private loadAiRouting() {
    if (!this.canManageAiRouting()) return;
    this.api.getAiRouting().subscribe({
      next: (routing) => {
        this.aiRouting.set(routing);
        this.aiRoutingError.set('');
      },
      error: () => {
        this.aiRoutingError.set('Connect a usable AI provider to enable model routing.');
      }
    });
  }

  private updateCurrentTask(url: string) {
    const route = url.split('?')[0];
    const map: Record<string, { key: string; label: string }> = {
      '/dashboard': { key: 'GROWTH_HOME', label: 'Growth Home' },
      '/leads': { key: 'CRM_LEAD_SCORING', label: 'CRM' },
      '/campaigns': { key: 'CAMPAIGN_ANALYSIS', label: 'Campaigns' },
      '/creatives': { key: 'CREATIVE_STUDIO', label: 'Creative Studio' },
      '/ad-brain': { key: 'AI_INSIGHTS', label: 'AI Insights' },
      '/automation': { key: 'AUTOMATION', label: 'Automation' },
      '/analytics': { key: 'ANALYTICS', label: 'Analytics' },
    };
    const selected = map[route] || { key: 'GENERAL_ASSISTANT', label: 'General' };
    this.currentTaskKey.set(selected.key);
    this.currentTaskLabel.set(selected.label);
  }
}
