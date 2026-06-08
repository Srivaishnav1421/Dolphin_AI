import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class StateService {
  // Theme management
  public theme = signal<'light' | 'dark' | 'system'>('system');

  // Active Workspace / Tenant Context
  public currentWorkspace = signal<string | null>(null);
  public activeAgentSession = signal<string | null>(null);
  
  // Realtime Workflow Execution context
  public activeWorkflowRun = signal<any | null>(null);
  public activeTraceId = signal<string | null>(null);
  
  // Layout Options & Global Modals state
  public isSidebarExpanded = signal<boolean>(true);
  public isCommandPaletteOpen = signal<boolean>(false);
  
  // System metrics telemetry status
  public systemHealth = signal<{ latency: number; status: string }>({
    latency: 150,
    status: 'operational'
  });

  constructor() {
    const saved = localStorage.getItem('dolphin-theme') as 'light' | 'dark' | 'system';
    if (saved) {
      this.theme.set(saved);
    }
    this.applyTheme();

    // Listen for system preference changes if theme is set to 'system'
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (this.theme() === 'system') {
        this.applyTheme();
      }
    });
  }

  setTheme(t: 'light' | 'dark' | 'system') {
    this.theme.set(t);
    localStorage.setItem('dolphin-theme', t);
    this.applyTheme();
  }

  private applyTheme() {
    const t = this.theme();
    const resolved = t === 'system'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : t;
    document.documentElement.setAttribute('data-theme', resolved);
  }
}
