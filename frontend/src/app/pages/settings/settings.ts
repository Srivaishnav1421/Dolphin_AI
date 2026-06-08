import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { UiPanel, UiTabs } from '../../shared/ui';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, UiPanel, UiTabs],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings implements OnInit {
  config = signal<any>({
    whatsappPhoneId: '',
    whatsappToken: '',
    whatsappVerifyToken: '',
    brandName: '',
    brandLogoUrl: '',
    billingEmail: '',
    gstin: '',
    legalName: '',
    billingAddress: '',
    stateCode: 'MH',
    panNumber: '',
    bankDetails: '',
  });

  activeTab = signal<'general' | 'workspace' | 'integrations' | 'automation-insights' | 'users' | 'billing' | 'security'>('general');
  tabItems = [
    { id: 'general', label: 'General', icon: '⚙️' },
    { id: 'workspace', label: 'Workspace Details', icon: '🏢' },
    { id: 'integrations', label: 'Integrations', icon: '🔌' },
    { id: 'automation-insights', label: 'Automation & Insights', icon: '⚡' },
    { id: 'users', label: 'Users & Roles', icon: '👥' },
    { id: 'billing', label: 'Billing & Subscriptions', icon: '💰' },
    { id: 'security', label: 'Security', icon: '🛡️' }
  ];

  // Users & Roles state
  users = signal<any[]>([
    { name: 'Srivan Ch', email: 'srivan@dolphin.ai', role: 'OWNER', status: 'ACTIVE', avatar: '👑' },
    { name: 'Ananya Sharma', email: 'ananya@dolphin.ai', role: 'ADMIN', status: 'ACTIVE', avatar: '👩‍💼' },
    { name: 'Vijay Kumar', email: 'vijay@dolphin.ai', role: 'MARKETING_MANAGER', status: 'ACTIVE', avatar: '👨‍💻' },
    { name: 'Deepika Patel', email: 'deepika@dolphin.ai', role: 'SALES_MANAGER', status: 'ACTIVE', avatar: '👩‍💼' },
    { name: 'Amit Singh', email: 'amit@dolphin.ai', role: 'ANALYST', status: 'ACTIVE', avatar: '👨‍🎨' },
    { name: 'Rohan Joshi', email: 'rohan@dolphin.ai', role: 'VIEWER', status: 'ACTIVE', avatar: '👨‍💼' }
  ]);

  // Billing state
  wallet = signal<any>({ balance: 0.0, dailyBudgetLimit: 10000.0 });
  invoices = signal<any[]>([]);
  rechargeAmount = signal<number>(5000);
  recharging = signal(false);
  loading = signal(true);
  saving = signal(false);
  message = signal('');
  isSuccess = signal(true);

  automationMetrics = signal<any>({
    leadsProcessed: 12490,
    campaignAutomations: 45190,
    conversationsGenerated: 3120,
    insightsCreated: 1450,
    tasksCompleted: 8452
  });

  indianStates = [
    { code: 'AN', name: 'Andaman and Nicobar Islands' },
    { code: 'AP', name: 'Andhra Pradesh' },
    { code: 'AR', name: 'Arunachal Pradesh' },
    { code: 'AS', name: 'Assam' },
    { code: 'BR', name: 'Bihar' },
    { code: 'CH', name: 'Chandigarh' },
    { code: 'CG', name: 'Chhattisgarh' },
    { code: 'DN', name: 'Dadra and Nagar Haveli and Daman and Diu' },
    { code: 'DL', name: 'Delhi' },
    { code: 'GA', name: 'Goa' },
    { code: 'GJ', name: 'Gujarat' },
    { code: 'HR', name: 'Haryana' },
    { code: 'HP', name: 'Himachal Pradesh' },
    { code: 'JK', name: 'Jammu and Kashmir' },
    { code: 'JH', name: 'Jharkhand' },
    { code: 'KA', name: 'Karnataka' },
    { code: 'KL', name: 'Kerala' },
    { code: 'LA', name: 'Ladakh' },
    { code: 'LD', name: 'Lakshadweep' },
    { code: 'MP', name: 'Madhya Pradesh' },
    { code: 'MH', name: 'Maharashtra' },
    { code: 'MN', name: 'Manipur' },
    { code: 'ML', name: 'Meghalaya' },
    { code: 'MZ', name: 'Mizoram' },
    { code: 'NL', name: 'Nagaland' },
    { code: 'OD', name: 'Odisha' },
    { code: 'PY', name: 'Puducherry' },
    { code: 'PB', name: 'Punjab' },
    { code: 'RJ', name: 'Rajasthan' },
    { code: 'SK', name: 'Sikkim' },
    { code: 'TN', name: 'Tamil Nadu' },
    { code: 'TS', name: 'Telangana' },
    { code: 'TR', name: 'Tripura' },
    { code: 'UP', name: 'Uttar Pradesh' },
    { code: 'UK', name: 'Uttarakhand' },
    { code: 'WB', name: 'West Bengal' }
  ];

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit() {
    this.loadConfig();
    this.loadAiData();
  }

  loadConfig() {
    this.loading.set(true);
    this.api.getWorkspaceConfig().subscribe({
      next: (res) => {
        if (res) {
          // If verify token is empty, auto-generate a random one for them
          if (!res.whatsappVerifyToken) {
            res.whatsappVerifyToken = 'CD_verify_' + Math.random().toString(36).substring(2, 10).toUpperCase();
          }
          this.config.set(res);
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  loadAiData() {
    this.api.getPipelineHealth().subscribe({
      next: (res) => {
        if (res) {
          this.automationMetrics.update((m: any) => ({
            ...m,
            leadsProcessed: res.processedCount || 12490,
            tasksCompleted: res.tasksCompleted || 8452
          }));
        }
      }
    });
  }

  saveConfig() {
    // Only permit Owners/Admins to update
    const role = this.auth.currentUser()?.role;
    if (role !== 'OWNER' && role !== 'ADMIN') {
      this.isSuccess.set(false);
      this.message.set('❌ Only workspace Owners or Admins can save configurations.');
      setTimeout(() => this.message.set(''), 4000);
      return;
    }

    this.saving.set(true);
    this.message.set('');
    this.api.updateWorkspaceConfig(this.config()).subscribe({
      next: (res) => {
        this.config.set(res);
        this.isSuccess.set(true);
        this.message.set('✅ Workspace configurations saved successfully!');
        this.saving.set(false);
        setTimeout(() => this.message.set(''), 3000);
      },
      error: (err) => {
        this.isSuccess.set(false);
        this.message.set('❌ Failed to update configurations: ' + (err.error?.error || err.message));
        this.saving.set(false);
      },
    });
  }

  setTab(tab: 'general' | 'workspace' | 'integrations' | 'automation-insights' | 'users' | 'billing' | 'security') {
    this.activeTab.set(tab);
    if (tab === 'automation-insights') {
      this.loadAiData();
    }
    if (tab === 'billing') {
      this.loadBillingData();
    }
  }

  updateUserRole(email: string, newRole: string) {
    this.users.update(list => list.map(u => u.email === email ? { ...u, role: newRole } : u));
    this.isSuccess.set(true);
    this.message.set(`👥 Updated role for ${email} to ${newRole}`);
    setTimeout(() => this.message.set(''), 3000);
  }

  loadBillingData() {
    this.api.getWallet().subscribe({
      next: (w) => { if (w) this.wallet.set(w); }
    });
    this.api.getInvoices().subscribe({
      next: (inv) => { if (inv) this.invoices.set(inv.sort((a,b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())); }
    });
  }

  copyVerifyToken(val: string) {
    navigator.clipboard.writeText(val);
    this.isSuccess.set(true);
    this.message.set('📋 Token copied to clipboard!');
    setTimeout(() => this.message.set(''), 2000);
  }
}
