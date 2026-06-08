import { Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { StateService } from '../../core/services/state.service';
import { CommandService } from '../../core/services/command.service';
import { UiModal, UiCommandPalette } from '../../shared/ui';

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
    UiCommandPalette
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  constructor(
    public auth: AuthService,
    private http: HttpClient,
    public state: StateService,
    private command: CommandService // Auto-initializes global keyboard listener
  ) {}

  navItems = [
    { path: '/dashboard', label: 'Dashboard',   icon: 'grid_view' },
    { path: '/leads',     label: 'CRM',         icon: 'group' },
    { path: '/campaigns', label: 'Campaigns',   icon: 'campaign' },
    { path: '/creatives', label: 'AI Studio',   icon: 'palette' },
    { path: '/ad-brain',  label: 'AI Brain',    icon: 'psychology' },
    { path: '/automation', label: 'Automation',  icon: 'settings_accessibility' },
    { path: '/analytics', label: 'Analytics',   icon: 'analytics' },
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

  changePassword() {
    if (!this.pwd.old || !this.pwd.new_pwd) { this.pwdMsg.set('⚠️ Fill all fields.'); return; }
    if (this.pwd.new_pwd !== this.pwd.confirm) { this.pwdMsg.set('⚠️ Passwords do not match.'); return; }
    if (this.pwd.new_pwd.length < 6) { this.pwdMsg.set('⚠️ Min 6 characters.'); return; }

    this.pwdSaving.set(true);
    this.http.post('http://localhost:8000/api/auth/change-password', {
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
}
