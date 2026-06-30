import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AppIcon } from '../../shared/ui';
import { RuntimeConfigService } from '../../core/services/runtime-config.service';

interface Provider {
  id: string;
  name: string;
  emoji: string;
  description: string;
  fields: ProviderField[];
  status: 'connected' | 'needs-setup' | 'needs-validation' | 'error' | 'inactive';
  lastChecked?: string;
  lastValidatedAt?: string;
  validationMessage?: string;
  maskedKeys?: Record<string, string>;
}

interface ProviderField {
  key: string;
  label: string;
  placeholder: string;
  type: 'password' | 'text' | 'url';
  optional?: boolean;
}

interface RuntimeIdentity {
  profile?: string;
  localModeEnabled?: boolean;
  mockAiEnabled?: boolean;
  database?: {
    connected?: boolean;
    name?: string;
    flyway?: string;
  };
}

@Component({
  selector: 'app-integrations',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIcon],
  template: `
<div class="page animate-fade-in">
  <div class="page-header">
    <div>
      <h1>Integrations</h1>
      <p class="page-header__sub">Secure and effortless connections for ads, WhatsApp, AI tools, and business automation.</p>
    </div>
    <button class="btn btn-ghost btn-sm" (click)="loadAll()" [disabled]="loadingStatus()">Refresh</button>
  </div>

  @if (localModeEnabled()) {
    <div class="local-mode-banner">
      <app-icon name="shield" [size]="16"></app-icon>
      <span>Local approval-first mode is enabled. External publish, send, launch, and live validation actions are blocked or approval-only.</span>
    </div>
  }

  <div class="integration-health-grid">
    <div class="integration-health-item">
      <span>Runtime</span>
      <strong>{{ runtimeIdentity()?.profile || (runtimeError() ? 'Unavailable' : 'Checking') }}</strong>
    </div>
    <div class="integration-health-item">
      <span>Database</span>
      <strong>{{ runtimeIdentity()?.database?.name || 'Unknown' }}</strong>
      <small>{{ runtimeIdentity()?.database?.connected ? 'Connected' : 'Not confirmed' }}</small>
    </div>
    <div class="integration-health-item">
      <span>AI provider</span>
      <strong>{{ activeAiProvider() }}</strong>
      <small>{{ usableAiProviderCount() }} usable provider{{ usableAiProviderCount() === 1 ? '' : 's' }}</small>
    </div>
    <div class="integration-health-item">
      <span>Local safety</span>
      <strong>{{ localModeEnabled() ? 'Enabled' : 'Not enabled' }}</strong>
      <small>{{ runtimeIdentity()?.mockAiEnabled ? 'Mock AI enabled' : 'Live AI only with valid credentials' }}</small>
    </div>
  </div>

  <!-- Status summary bar -->
  <div class="int-summary-bar">
    <div class="int-summary-item">
      <span class="status-badge status-connected">{{ connectedCount() }} Connected</span>
    </div>
    <div class="int-summary-item">
      <span class="status-badge status-needs-setup">{{ needsSetupCount() }} Needs setup</span>
    </div>
    @if (needsValidationCount() > 0) {
      <div class="int-summary-item">
        <span class="status-badge status-needs-validation">{{ needsValidationCount() }} Needs validation</span>
      </div>
    }
    @if (errorCount() > 0) {
      <div class="int-summary-item">
        <span class="status-badge status-error">{{ errorCount() }} Needs attention</span>
      </div>
    }
  </div>

  @if (loadingStatus()) {
    <div class="integration-state">Loading real integration status…</div>
  }

  @if (statusError()) {
    <div class="alert-msg alert-error integration-page-alert">
      {{ statusError() }}
      <button class="btn btn-sm btn-ghost" (click)="loadStatuses()">Retry</button>
    </div>
  }

  @if (!loadingStatus() && !statusError() && connectedCount() === 0 && needsValidationCount() === 0 && errorCount() === 0) {
    <div class="integration-state">No integrations are connected yet. Missing credentials are shown as Needs setup.</div>
  }

  <!-- Provider Cards Grid -->
  <div class="grid-integrations">
    @for (provider of providers(); track provider.id) {
      <div class="integration-card"
           [class.integration-card--connected]="provider.status === 'connected'"
           [class.integration-card--error]="provider.status === 'error'">

        <!-- Card Header -->
        <div class="integration-card__header">
          <div class="integration-card__brand">
            <div class="integration-card__logo">{{ provider.emoji }}</div>
            <div>
              <div class="integration-card__name">{{ provider.name }}</div>
              <div class="integration-card__desc">{{ provider.description }}</div>
            </div>
          </div>
          <span class="status-badge"
                [class.status-connected]="provider.status === 'connected'"
                [class.status-needs-setup]="provider.status === 'needs-setup'"
                [class.status-needs-validation]="provider.status === 'needs-validation'"
                [class.status-error]="provider.status === 'error'"
                [class.status-inactive]="provider.status === 'inactive'">
            {{ statusLabel(provider.status) }}
          </span>
        </div>

        <!-- Fields -->
        <div class="integration-card__fields">
          @for (field of provider.fields; track field.key) {
            <div class="form-field">
              <label>{{ field.label }}
                @if (field.optional) { <span class="text-muted" style="font-weight:400"> (optional)</span> }
              </label>
              @if (hasStoredCredentials(provider) && getFieldValue(provider.id, field.key) && !isEditing(provider.id)) {
                <div class="masked-key-row">
                  <input class="input input--success"
                         [type]="field.type"
                         [value]="getFieldValue(provider.id, field.key)"
                         readonly />
                  <button class="btn btn-sm btn-ghost" (click)="beginEdit(provider.id)">Edit</button>
                </div>
              } @else {
                <input class="input"
                       [type]="field.type"
                       [placeholder]="field.placeholder"
                       [(ngModel)]="formValues[provider.id + '_' + field.key]"
                       autocomplete="off"
                       autocorrect="off"
                       spellcheck="false" />
              }
            </div>
          }
        </div>

        <!-- Success / Error feedback -->
        @if (getFeedback(provider.id); as fb) {
          <div class="alert-msg" [class.alert-success]="fb.ok" [class.alert-error]="!fb.ok">
            @if (fb.ok) {
              <span class="success-check" style="margin-right:6px">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path class="check-path" d="M3 8l3.5 3.5 6.5-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </span>
            }
            {{ fb.message }}
          </div>
        }

        <!-- Actions -->
        <div class="integration-card__actions">
          @if (provider.status === 'needs-setup' || isEditing(provider.id)) {
            <button class="btn btn-primary btn-sm"
                    (click)="connect(provider)"
                    [disabled]="isConnecting(provider.id)">
              @if (isConnecting(provider.id)) {
                <span class="spinner-sm"></span> Connecting…
              } @else {
                Connect
              }
            </button>
          }
          @if (hasStoredCredentials(provider) && !isEditing(provider.id)) {
            <button class="btn btn-ghost btn-sm" (click)="beginEdit(provider.id)">Manage</button>
            <button class="btn btn-danger btn-sm" (click)="disconnect(provider)">Disconnect</button>
          }
          @if (isEditing(provider.id)) {
            <button class="btn btn-ghost btn-sm" (click)="cancelEdit(provider.id)">Cancel</button>
          }
          <button class="btn btn-ghost btn-sm"
                  (click)="testConnection(provider)"
                  [disabled]="provider.status === 'needs-setup' || liveValidationBlocked(provider)">
            Test connection
          </button>
        </div>

        @if (liveValidationBlocked(provider) && provider.status !== 'needs-setup') {
          <div class="local-validation-note">Live connection validation is disabled in local approval-first mode.</div>
        }

        <div class="integration-card__last-checked">
          Last checked: {{ provider.lastValidatedAt || provider.lastChecked || (provider.status === 'connected' ? 'Just now' : 'Not checked yet') }}
          @if (provider.validationMessage) {
            <span class="validation-message">{{ provider.validationMessage }}</span>
          }
        </div>
      </div>
    }
  </div>

  <!-- Security note -->
  <div class="int-security-note">
    <app-icon name="lock" [size]="16"></app-icon>
    Your API keys are encrypted and stored securely. They are never shown in full after saving.
  </div>
</div>
`,
  styles: [`
    .int-summary-bar {
      display: flex; gap: 12px; align-items: center; margin-bottom: 28px; flex-wrap: wrap;
    }
    .status-needs-validation {
      color: var(--warning);
      border-color: color-mix(in srgb, var(--warning), transparent 68%);
      background: color-mix(in srgb, var(--warning), transparent 88%);
    }
    .int-security-note {
      display: flex; align-items: center; gap: 8px;
      font-size: 12px; color: var(--text-muted);
      margin-top: 28px; padding: 12px 16px;
      background: var(--bg-surface); border-radius: var(--radius-lg);
      border: 1px solid var(--border);
    }
    .local-mode-banner {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-bottom: 16px;
      padding: 12px 14px;
      border: 1px solid color-mix(in srgb, var(--warning), transparent 60%);
      background: color-mix(in srgb, var(--warning), transparent 90%);
      color: var(--text-primary);
      border-radius: var(--radius-md);
      font-size: 13px;
    }
    .integration-health-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin-bottom: 18px;
    }
    .integration-health-item {
      padding: 12px;
      border: 1px solid var(--border);
      background: var(--bg-surface);
      border-radius: var(--radius-md);
      min-height: 78px;
    }
    .integration-health-item span,
    .integration-health-item small {
      display: block;
      color: var(--text-muted);
      font-size: 12px;
    }
    .integration-health-item strong {
      display: block;
      margin: 4px 0;
      font-size: 16px;
      color: var(--text-primary);
      word-break: break-word;
    }
    .integration-state {
      margin-bottom: 16px;
      padding: 12px 14px;
      color: var(--text-muted);
      background: var(--bg-surface);
      border: 1px dashed var(--border);
      border-radius: var(--radius-md);
      font-size: 13px;
    }
    .integration-page-alert {
      margin-bottom: 16px;
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: center;
    }
    .local-validation-note {
      margin-top: 8px;
      color: var(--text-muted);
      font-size: 12px;
      line-height: 1.4;
    }
    .masked-key-row { display: flex; gap: 8px; align-items: center; }
    .masked-key-row .input { flex: 1; font-family: monospace; letter-spacing: 0.1em; color: var(--text-secondary); }
    .validation-message {
      display: block;
      margin-top: 4px;
      color: var(--text-muted);
      line-height: 1.4;
    }
    .success-check svg .check-path {
      stroke-dasharray: 20; stroke-dashoffset: 20;
      animation: check-draw 0.35s cubic-bezier(0.16,1,0.3,1) 0.1s forwards;
    }
    @keyframes check-draw { to { stroke-dashoffset: 0; } }
  `]
})
export class Integrations {
  private base: string;
  private readonly liveValidationProviderIds = new Set(['openai', 'gemini', 'anthropic', 'huggingface']);

  // Per-field form values — never persisted to localStorage
  formValues: Record<string, string> = {};
  private connectingSet = new Set<string>();
  private editingSet    = new Set<string>();
  private collapsedSet  = new Set<string>();
  private feedbackMap = signal<Record<string, { ok: boolean; message: string }>>({});
  loadingStatus = signal(true);
  statusError = signal<string | null>(null);
  runtimeIdentity = signal<RuntimeIdentity | null>(null);
  runtimeError = signal(false);
  aiStatus = signal<any | null>(null);
  aiStatusError = signal(false);

  providers = signal<Provider[]>([
    {
      id: 'meta', name: 'Meta Ads', emoji: '📘',
      description: 'Connect Meta Ads to track campaigns, leads and ad performance.',
      status: 'needs-setup',
      fields: [
        { key: 'access_token', label: 'Access Token', placeholder: 'Enter your Meta access token', type: 'password' },
        { key: 'ad_account_id', label: 'Ad Account ID', placeholder: 'act_XXXXXXXXXXXXXXX', type: 'text' }
      ]
    },
    {
      id: 'whatsapp', name: 'WhatsApp', emoji: '💬',
      description: 'Connect WhatsApp to manage customer conversations.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key',     label: 'API Key',             placeholder: 'Enter your WhatsApp API key', type: 'password' },
        { key: 'phone_id',   label: 'Phone Number ID',      placeholder: 'Phone number ID from Meta', type: 'text' },
        { key: 'business_id',label: 'Business Account ID',  placeholder: 'WhatsApp Business account ID', type: 'text' }
      ]
    },
    {
      id: 'google', name: 'Google Ads', emoji: '🔵',
      description: 'Connect Google Ads to sync campaigns and performance.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key',     label: 'API Key',      placeholder: 'Enter your Google Ads API key', type: 'password' },
        { key: 'customer_id', label: 'Customer ID',  placeholder: '123-456-7890', type: 'text' }
      ]
    },
    {
      id: 'tiktok', name: 'TikTok Ads', emoji: '🎵',
      description: 'Connect TikTok Ads to track campaign results.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key',        label: 'API Key',        placeholder: 'Enter your TikTok API key', type: 'password' },
        { key: 'advertiser_id',  label: 'Advertiser ID',  placeholder: 'Your TikTok advertiser ID', type: 'text' }
      ]
    },
    {
      id: 'n8n', name: 'n8n', emoji: '⚙️',
      description: 'Connect n8n to run your business automations.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key',  label: 'API Key',       placeholder: 'Enter your n8n API key', type: 'password' },
        { key: 'base_url', label: 'Workspace URL',  placeholder: 'https://your-n8n.example.com', type: 'url' }
      ]
    },
    {
      id: 'openai', name: 'OpenAI', emoji: '🤖',
      description: 'Connect OpenAI to power smart replies and insights.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key', label: 'API Key', placeholder: 'sk-proj-…', type: 'password' }
      ]
    },
    {
      id: 'gemini', name: 'Gemini', emoji: '✨',
      description: 'Connect Gemini to support smart AI tasks.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key', label: 'API Key', placeholder: 'AIza…', type: 'password' }
      ]
    },
    {
      id: 'anthropic', name: 'Anthropic', emoji: '🧠',
      description: 'Connect Anthropic to support advanced AI responses.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key', label: 'API Key', placeholder: 'sk-ant-…', type: 'password' }
      ]
    },
    {
      id: 'ollama', name: 'Ollama', emoji: '🦙',
      description: 'Connect a local AI server for private AI tasks.',
      status: 'needs-setup',
      fields: [
        { key: 'base_url', label: 'Server URL', placeholder: 'http://localhost:11434', type: 'url' }
      ]
    },
    {
      id: 'huggingface', name: 'Hugging Face', emoji: '🤗',
      description: 'Connect Hugging Face to use hosted AI models.',
      status: 'needs-setup',
      fields: [
        { key: 'api_key', label: 'API Key', placeholder: 'hf_…', type: 'password' }
      ]
    }
  ]);

  connectedCount  = computed(() => this.providers().filter(p => p.status === 'connected').length);
  needsSetupCount = computed(() => this.providers().filter(p => p.status === 'needs-setup').length);
  needsValidationCount = computed(() => this.providers().filter(p => p.status === 'needs-validation').length);
  errorCount      = computed(() => this.providers().filter(p => p.status === 'error').length);
  localModeEnabled = computed(() => this.runtimeIdentity()?.localModeEnabled === true);
  activeAiProvider = computed(() => {
    if (this.aiStatusError()) return 'Unavailable';
    return this.aiStatus()?.activeProvider || 'Checking';
  });
  usableAiProviderCount = computed(() => {
    const providers = this.aiStatus()?.providers;
    return Array.isArray(providers) ? providers.filter((p: any) => p?.usable === true).length : 0;
  });

  constructor(private http: HttpClient, config: RuntimeConfigService) {
    this.base = config.apiBase;
    this.loadAll();
  }

  loadAll() {
    this.loadRuntime();
    this.loadAiStatus();
    this.loadStatuses();
  }

  loadStatuses() {
    this.loadingStatus.set(true);
    this.statusError.set(null);
    this.http.get<any>(`${this.base}/api/integrations/status`).subscribe({
      next: (res) => {
        this.loadingStatus.set(false);
        if (!res) return;
        this.providers.update(list => list.map(p => {
          const info = res[p.id];
          if (!info) return p;
          const status = this.statusFromBackend(info);
          const updated = {
            ...p,
            status,
            lastChecked: info.lastChecked || undefined,
            lastValidatedAt: info.lastValidatedAt || undefined,
            validationMessage: info.validationMessage || undefined,
            maskedKeys: info.maskedKeys || {}
          } as Provider;
          if (updated.status === 'connected') this.collapsedSet.add(p.id);
          return updated;
        }));
      },
      error: () => {
        this.loadingStatus.set(false);
        this.statusError.set('Integration status could not be loaded from the backend. Existing cards remain editable, but status is not confirmed.');
      }
    });
  }

  loadRuntime() {
    this.runtimeError.set(false);
    this.http.get<RuntimeIdentity>(`${this.base}/api/system/runtime`).subscribe({
      next: (identity) => this.runtimeIdentity.set(identity),
      error: () => {
        this.runtimeIdentity.set(null);
        this.runtimeError.set(true);
      }
    });
  }

  loadAiStatus() {
    this.aiStatusError.set(false);
    this.http.get<any>(`${this.base}/api/admin/ai-infrastructure/providers`).subscribe({
      next: (status) => this.aiStatus.set(status),
      error: () => {
        this.aiStatus.set(null);
        this.aiStatusError.set(true);
      }
    });
  }

  connect(provider: Provider) {
    // Validate required fields are filled
    const missing = provider.fields
      .filter(f => !f.optional && !this.formValues[provider.id + '_' + f.key]?.trim());
    if (missing.length > 0) {
      this.setFeedback(provider.id, false, `Please fill in: ${missing.map(f => f.label).join(', ')}`);
      return;
    }

    this.connectingSet.add(provider.id);
    const body: Record<string, string> = {};
    provider.fields.forEach(f => {
      const val = this.formValues[provider.id + '_' + f.key];
      if (val) body[f.key] = val;
    });

    this.http.post<any>(`${this.base}/api/integrations/${provider.id}/connect`, body).subscribe({
      next: (res) => {
        this.connectingSet.delete(provider.id);
        this.providers.update(list => list.map(p =>
          p.id === provider.id
            ? {
                ...p,
                status: this.statusFromBackend(res),
                lastChecked: 'Just now',
                validationMessage: res?.message || 'Credentials stored. Run Test connection to verify live access.',
                maskedKeys: res?.maskedKeys || {}
              }
            : p
        ));
        this.clearFormFields(provider);
        this.editingSet.delete(provider.id);
        this.setFeedback(provider.id, true, res?.message || 'Credentials stored. Run Test connection to verify live access.');
        setTimeout(() => this.clearFeedback(provider.id), 4000);
      },
      error: (err) => {
        this.connectingSet.delete(provider.id);
        const msg = err?.error?.message || 'Connection failed. Please check your details and try again.';
        this.setFeedback(provider.id, false, msg);
        this.providers.update(list => list.map(p =>
          p.id === provider.id ? { ...p, status: 'error' } : p
        ));
      }
    });
  }

  testConnection(provider: Provider) {
    if (this.liveValidationBlocked(provider)) {
      this.setFeedback(provider.id, false, 'Live validation is disabled in local approval-first mode. No external provider call was made.');
      return;
    }
    this.http.post<any>(`${this.base}/api/integrations/${provider.id}/test`, {}).subscribe({
      next: (res) => {
        this.providers.update(list => list.map(p =>
          p.id === provider.id
            ? {
                ...p,
                lastChecked: 'Just now',
                lastValidatedAt: 'Just now',
                validationMessage: res?.message || 'Connection validated successfully.',
                status: res?.status === 'connected' ? 'connected' : 'needs-validation'
              }
            : p
        ));
        this.setFeedback(provider.id, true, res?.message || 'Credentials saved. Live validation is pending.');
        setTimeout(() => this.clearFeedback(provider.id), 5000);
      },
      error: (err) => {
        this.providers.update(list => list.map(p =>
          p.id === provider.id
            ? { ...p, status: 'error', validationMessage: err?.error?.message || 'Connection test failed.' }
            : p
        ));
        this.setFeedback(provider.id, false, err?.error?.message || 'Connection test failed. Please check your details.');
      }
    });
  }

  disconnect(provider: Provider) {
    this.http.delete(`${this.base}/api/integrations/${provider.id}`).subscribe({
      next: () => {
        this.providers.update(list => list.map(p =>
          p.id === provider.id ? { ...p, status: 'needs-setup', maskedKeys: {}, lastChecked: undefined } : p
        ));
        this.collapsedSet.delete(provider.id);
        this.editingSet.delete(provider.id);
      },
      error: () => {
        // Still allow local disconnect UX
        this.providers.update(list => list.map(p =>
          p.id === provider.id ? { ...p, status: 'needs-setup', maskedKeys: {} } : p
        ));
        this.collapsedSet.delete(provider.id);
      }
    });
  }

  beginEdit(id: string)   { this.editingSet.add(id); this.collapsedSet.delete(id); }
  cancelEdit(id: string)  { this.editingSet.delete(id); }
  expandCard(id: string)  { this.collapsedSet.delete(id); }

  isConnecting(id: string) { return this.connectingSet.has(id); }
  isEditing(id: string)    { return this.editingSet.has(id); }
  isCollapsed(id: string)  { return this.collapsedSet.has(id); }
  hasStoredCredentials(provider: Provider) {
    return provider.status !== 'needs-setup' && !!provider.maskedKeys && Object.keys(provider.maskedKeys).length > 0;
  }

  getFieldValue(providerId: string, fieldKey: string): string {
    const provider = this.providers().find(p => p.id === providerId);
    if (!provider?.maskedKeys) return '';
    return provider.maskedKeys[fieldKey] || '';
  }

  getFeedback(id: string) { return this.feedbackMap()[id] || null; }
  setFeedback(id: string, ok: boolean, message: string) {
    this.feedbackMap.update(map => ({ ...map, [id]: { ok, message } }));
  }
  clearFeedback(id: string) {
    this.feedbackMap.update(map => {
      const next = { ...map };
      delete next[id];
      return next;
    });
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      'connected':   'Connected',
      'needs-setup': 'Needs setup',
      'needs-validation': 'Needs validation',
      'error':       'Needs attention',
      'inactive':    'Inactive'
    };
    return map[status] || status;
  }

  liveValidationBlocked(provider: Provider): boolean {
    return this.localModeEnabled() && this.liveValidationProviderIds.has(provider.id);
  }

  private clearFormFields(provider: Provider) {
    provider.fields.forEach(f => {
      delete this.formValues[provider.id + '_' + f.key];
    });
  }

  private statusFromBackend(info: any): Provider['status'] {
    if (!info) return 'needs-setup';
    if (info.validationStatus === 'FAILED' || info.error) return 'error';
    if (info.connected || info.validationStatus === 'VALIDATED') return 'connected';
    if (info.configured || info.validationStatus === 'PENDING_VALIDATION') return 'needs-validation';
    return 'needs-setup';
  }
}
