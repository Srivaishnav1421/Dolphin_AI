import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  readonly apiBase = this.resolveApiBase();
  readonly wsBase = this.resolveWsBase();

  private resolveApiBase(): string {
    const configured = (window as any).__DOLPHIN_CONFIG__?.apiBase as string | undefined;
    if (configured?.trim()) return configured.replace(/\/$/, '');

    const { protocol, hostname, port } = window.location;
    const isDevServer = /^42\d{2}$/.test(port);
    if (isDevServer) {
      const apiHost = hostname === '127.0.0.1' ? 'localhost' : hostname;
      return `${protocol}//${apiHost}:8000`;
    }

    return window.location.origin;
  }

  private resolveWsBase(): string {
    const configured = (window as any).__DOLPHIN_CONFIG__?.wsBase as string | undefined;
    if (configured?.trim()) return configured.replace(/\/$/, '');

    const apiBase = this.apiBase;
    if (apiBase.startsWith('https://')) return apiBase.replace('https://', 'wss://');
    if (apiBase.startsWith('http://')) return apiBase.replace('http://', 'ws://');
    return apiBase;
  }
}
