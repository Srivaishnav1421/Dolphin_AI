import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';
import { AppIcon, UiPanel, UiTabs } from '../../shared/ui';
import { Integrations } from '../integrations/integrations';

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, UiPanel, UiTabs, Integrations, AppIcon],
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

  activeTab = signal<'general' | 'workspace' | 'users' | 'billing' | 'automation-insights' | 'integrations' | 'security' | 'system'>('general');
  tabItems = [
    { id: 'general', label: 'General', icon: 'settings' },
    { id: 'workspace', label: 'Workspace', icon: 'business' },
    { id: 'users', label: 'Team', icon: 'group' },
    { id: 'billing', label: 'Billing', icon: 'payments' },
    { id: 'automation-insights', label: 'Automation & Insights', icon: 'auto_awesome' },
    { id: 'integrations', label: 'Integrations', icon: 'key' },
    { id: 'security', label: 'Security', icon: 'verified_user' },
    { id: 'system', label: 'System', icon: 'monitor' }
  ];

  users = signal<any[]>([]);
  teamLoading = signal(false);
  teamSavingId = signal('');
  readonly roleOptions = [
    { value: 'OWNER', label: 'Owner' },
    { value: 'ADMIN', label: 'Admin' },
    { value: 'MANAGER', label: 'Manager' },
    { value: 'EMPLOYEE', label: 'Employee' },
    { value: 'VIEWER', label: 'Viewer' }
  ];
  readonly roleAccessRows = [
    { role: 'OWNER', dashboard: 'Executive growth dashboard', read: 'All workspace data', write: 'All modules', admin: 'Billing, team, security, integrations' },
    { role: 'ADMIN', dashboard: 'Operations dashboard', read: 'All workspace data', write: 'Campaigns, leads, automations, settings', admin: 'Team roles except owner takeover' },
    { role: 'MANAGER', dashboard: 'Manager performance dashboard', read: 'Campaigns, leads, insights, automations', write: 'Campaign actions and approvals', admin: 'Read-only team visibility' },
    { role: 'EMPLOYEE', dashboard: 'Work queue dashboard', read: 'Assigned leads and campaigns', write: 'Lead notes, follow-ups, assigned work', admin: 'No admin access' },
    { role: 'VIEWER', dashboard: 'Read-only reporting dashboard', read: 'Reports and summaries', write: 'None', admin: 'No admin access' }
  ];

  // Billing state
  wallet = signal<any | null>(null);
  invoices = signal<any[]>([]);
  paymentConfig = signal<any | null>(null);
  billingUnavailable = signal(false);
  recharging = signal(false);
  selectedPlan = signal('growth');
  billingPlans = [
    { id: 'starter', name: 'Starter Growth', price: 2999, leads: '5,000 leads', automations: '5 automations', ai: '2,000 AI conversations' },
    { id: 'growth', name: 'Growth OS', price: 7999, leads: '25,000 leads', automations: '20 automations', ai: '10,000 AI conversations' },
    { id: 'scale', name: 'Scale Suite', price: 19999, leads: '100,000 leads', automations: 'Unlimited automations', ai: '50,000 AI conversations' }
  ];
  loading = signal(true);
  saving = signal(false);
  message = signal('');
  isSuccess = signal(true);
  runtimeIdentity = signal<any | null>(null);
  runtimeUnavailable = signal(false);
  localModeEnabled = signal(true);

  automationMetrics = signal<any | null>(null);
  automationUnavailable = signal(false);

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

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private route: ActivatedRoute,
    public runtimeConfig: RuntimeConfigService
  ) {}

  ngOnInit() {
    this.loadConfig();
    this.loadTeam();
    this.loadAiData();
    this.loadRuntimeIdentity();
    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        const t = params['tab'];
        if (['general', 'workspace', 'integrations', 'automation-insights', 'users', 'billing', 'security', 'system'].includes(t)) {
          this.setTab(t as any);
        }
      }
    });
  }

  loadConfig() {
    this.loading.set(true);
    this.api.getWorkspaceConfig().subscribe({
      next: (res) => {
        if (res) {
          this.config.set({
            ...this.config(),
            ...res,
            whatsappToken: '',
            whatsappVerifyToken: ''
          });
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  loadAiData() {
    this.automationUnavailable.set(false);
    forkJoin({
      pipeline: this.api.getPipelineHealth(),
      workflow: this.api.getWorkflowStats(),
      aiUsage: this.api.getAiUsageStats(),
      insights: this.api.getRecentEvents()
    }).subscribe({
      next: ({ pipeline, workflow, aiUsage, insights }) => {
        this.automationMetrics.set({
          aiConversations: Number(aiUsage?.totalRequests ?? aiUsage?.requests ?? 0),
          leadsProcessed: Number(pipeline?.processedCount ?? pipeline?.totalLeads ?? 0),
          automationsExecuted: Number(workflow?.completedCount ?? workflow?.totalExecutions ?? workflow?.total ?? 0),
          insightsGenerated: Array.isArray(insights) ? insights.length : Number((insights as any)?.count ?? 0),
          tasksCompleted: Number(workflow?.completedCount ?? pipeline?.tasksCompleted ?? 0)
        });
      },
      error: () => {
        this.automationMetrics.set(null);
        this.automationUnavailable.set(true);
      }
    });
  }

  saveConfig() {
    // Only permit Owners/Admins to update
    const role = this.auth.currentUser()?.role;
    if (role !== 'OWNER' && role !== 'ADMIN') {
      this.isSuccess.set(false);
      this.message.set('Only workspace Owners or Admins can save changes.');
      setTimeout(() => this.message.set(''), 4000);
      return;
    }

    this.saving.set(true);
    this.message.set('');
    this.api.updateWorkspaceConfig(this.config()).subscribe({
      next: (res) => {
        this.config.set(res);
        this.isSuccess.set(true);
        this.message.set('Settings saved successfully.');
        this.saving.set(false);
        setTimeout(() => this.message.set(''), 3000);
      },
      error: (err) => {
        this.isSuccess.set(false);
        this.message.set('Could not save settings: ' + (err.error?.error || err.message));
        this.saving.set(false);
      },
    });
  }

  setTab(tab: 'general' | 'workspace' | 'users' | 'billing' | 'automation-insights' | 'integrations' | 'security' | 'system') {
    if (tab === 'system' && !this.canSeeRuntimeIdentity()) {
      return;
    }
    if (tab === 'billing' && this.localModeEnabled()) {
      this.activeTab.set('billing');
      return;
    }
    this.activeTab.set(tab);
    if (tab === 'users') {
      this.loadTeam();
    }
    if (tab === 'automation-insights') {
      this.loadAiData();
    }
    if (tab === 'billing') {
      this.loadBillingData();
    }
  }

  visibleTabItems() {
    return this.tabItems.filter(tab => tab.id !== 'system' || this.canSeeRuntimeIdentity());
  }

  loadRuntimeIdentity() {
    this.runtimeUnavailable.set(false);
    this.api.getRuntimeIdentity().subscribe({
      next: (identity) => {
        this.runtimeIdentity.set(identity);
        this.localModeEnabled.set(identity?.local_mode_enabled ?? identity?.localModeEnabled ?? true);
      },
      error: () => {
        this.runtimeIdentity.set(null);
        this.runtimeUnavailable.set(true);
        this.localModeEnabled.set(true);
      }
    });
  }

  canSeeRuntimeIdentity() {
    const identity = this.runtimeIdentity();
    const role = this.auth.currentUser()?.role;
    return !!identity && (identity.profile !== 'prod' || role === 'SYSTEM_ADMIN');
  }

  loadTeam() {
    this.teamLoading.set(true);
    this.api.getWorkspaceTeam().subscribe({
      next: (users) => {
        this.users.set(users ?? []);
        this.teamLoading.set(false);
      },
      error: () => {
        this.users.set([]);
        this.teamLoading.set(false);
      }
    });
  }

  canManageAccess() {
    const role = this.auth.currentUser()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  }

  canEditRole(user: any) {
    if (!this.canManageAccess()) return false;
    const currentRole = this.auth.currentUser()?.role;
    if (user.role === 'OWNER' && currentRole !== 'OWNER') return false;
    return true;
  }

  roleOptionsFor(user: any) {
    const currentRole = this.auth.currentUser()?.role;
    if (currentRole === 'OWNER') {
      return this.roleOptions;
    }
    if (user.role === 'OWNER') {
      return this.roleOptions.filter(role => role.value === 'OWNER');
    }
    return this.roleOptions.filter(role => role.value !== 'OWNER');
  }

  updateUserRole(user: any, newRole: string) {
    if (user.role === newRole) return;
    if (!this.canEditRole(user)) {
      this.isSuccess.set(false);
      this.message.set('Only Owner/Admin can update access. Owners can only be changed by another owner.');
      setTimeout(() => this.message.set(''), 4000);
      return;
    }
    this.teamSavingId.set(user.id);
    this.api.updateWorkspaceTeamRole(user.id, newRole).subscribe({
      next: (updated) => {
        this.users.update(list => list.map(u => u.id === updated.id ? updated : u));
        this.isSuccess.set(true);
        this.message.set(`Updated access for ${updated.email}.`);
        this.teamSavingId.set('');
        setTimeout(() => this.message.set(''), 3000);
      },
      error: (err) => {
        this.isSuccess.set(false);
        this.message.set(err.error?.error || 'Could not update team access.');
        this.teamSavingId.set('');
      }
    });
  }

  loadBillingData() {
    this.billingUnavailable.set(false);
    this.api.getPaymentConfig().subscribe({
      next: (config) => {
        this.paymentConfig.set({
          configured: Boolean(config?.configured),
          provider: config?.provider ?? 'RAZORPAY',
          currency: config?.currency ?? 'INR',
          upi_supported: config?.upi_supported !== false,
        });
      },
      error: () => {
        this.paymentConfig.set(null);
        this.billingUnavailable.set(true);
      }
    });
    this.api.getWallet().subscribe({
      next: (w) => { if (w) this.wallet.set(w); },
      error: () => {
        this.wallet.set(null);
        this.billingUnavailable.set(true);
      }
    });
    this.api.getInvoices().subscribe({
      next: (inv) => { if (inv) this.invoices.set(inv.sort((a,b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())); },
      error: () => {
        this.invoices.set([]);
        this.billingUnavailable.set(true);
      }
    });
  }

  selectedPlanDetails() {
    return this.billingPlans.find(plan => plan.id === this.selectedPlan()) ?? this.billingPlans[1];
  }

  invoiceDownloadUrl(invoiceId: string): string {
    return `${this.runtimeConfig.apiBase}/api/invoices/${invoiceId}/download`;
  }

  startSubscriptionPayment() {
    if (!this.paymentConfig()?.configured) {
      this.isSuccess.set(false);
      this.message.set('Razorpay checkout is not configured yet. Add the gateway keys before accepting UPI payments.');
      setTimeout(() => this.message.set(''), 5000);
      return;
    }

    const plan = this.selectedPlanDetails();
    this.recharging.set(true);
    this.message.set('');

    this.loadRazorpayCheckout()
      .then(() => {
        this.api.createPaymentOrder(plan.price).subscribe({
          next: (order) => {
            const options = {
              key: order.key_id,
              amount: order.amount,
              currency: order.currency,
              name: 'DolphinAI',
              description: `${plan.name} monthly subscription`,
              order_id: order.order_id,
              method: {
                upi: true,
                card: true,
                netbanking: true,
                wallet: true,
              },
              prefill: {
                name: this.auth.currentUser()?.name ?? '',
                email: this.auth.currentUser()?.email ?? this.config().billingEmail ?? '',
              },
              notes: {
                plan_id: plan.id,
                workspace: (this.auth.currentUser() as any)?.workspace_id ?? 'workspace',
              },
              handler: (response: any) => {
                this.api.verifyPayment({
                  razorpay_payment_id: response.razorpay_payment_id,
                  razorpay_order_id: response.razorpay_order_id,
                  razorpay_signature: response.razorpay_signature,
                }).subscribe({
                  next: () => {
                    this.isSuccess.set(true);
                    this.message.set(`${plan.name} payment captured. Subscription wallet updated.`);
                    this.recharging.set(false);
                    this.loadBillingData();
                  },
                  error: (err) => {
                    this.isSuccess.set(false);
                    this.message.set(err.error?.error || 'Payment captured but verification failed.');
                    this.recharging.set(false);
                  }
                });
              },
              modal: {
                ondismiss: () => this.recharging.set(false),
              },
            };
            new window.Razorpay!(options).open();
          },
          error: (err) => {
            this.isSuccess.set(false);
            this.message.set(err.error?.error || 'Could not start Razorpay checkout.');
            this.recharging.set(false);
          }
        });
      })
      .catch(() => {
        this.isSuccess.set(false);
        this.message.set('Could not load Razorpay Checkout. Check your network and gateway setup.');
        this.recharging.set(false);
      });
  }

  private loadRazorpayCheckout(): Promise<void> {
    if (window.Razorpay) return Promise.resolve();
    return new Promise((resolve, reject) => {
      const existing = document.querySelector('script[src="https://checkout.razorpay.com/v1/checkout.js"]');
      if (existing) {
        existing.addEventListener('load', () => resolve(), { once: true });
        existing.addEventListener('error', () => reject(), { once: true });
        return;
      }
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve();
      script.onerror = () => reject();
      document.body.appendChild(script);
    });
  }

  copyVerifyToken(val: string) {
    navigator.clipboard.writeText(val);
    this.isSuccess.set(true);
    this.message.set('Copied to clipboard.');
    setTimeout(() => this.message.set(''), 2000);
  }

  metricValue(key: 'aiConversations' | 'leadsProcessed' | 'automationsExecuted' | 'insightsGenerated' | 'tasksCompleted'): number {
    return Number(this.automationMetrics()?.[key] ?? 0);
  }

  metricsAreEmpty(): boolean {
    const metrics = this.automationMetrics();
    return !!metrics
      && this.metricValue('aiConversations') === 0
      && this.metricValue('leadsProcessed') === 0
      && this.metricValue('automationsExecuted') === 0
      && this.metricValue('insightsGenerated') === 0
      && this.metricValue('tasksCompleted') === 0;
  }
}
