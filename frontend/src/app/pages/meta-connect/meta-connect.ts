import { Component, OnInit, signal, HostListener } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { MetaConnection } from '../../shared/models';

@Component({
  selector: 'app-meta-connect',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './meta-connect.html',
  styleUrl: './meta-connect.scss',
})
export class MetaConnect implements OnInit {
  connections = signal<MetaConnection[]>([]);
  status      = signal<any>(null);
  loading     = signal(true);
  syncing     = signal(false);
  connecting  = signal(false);

  constructor(private api: ApiService) {}

  @HostListener('window:message', ['$event'])
  onMessage(event: MessageEvent) {
    if (event.data === 'META_CONNECTED') {
      this.load();
    }
  }

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.api.getMetaStatus().subscribe({
      next: s => { this.status.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.api.getMetaConnections().subscribe({
      next: c => this.connections.set(c),
      error: () => {},
    });
  }

  connectMeta() {
    this.connecting.set(true);
    this.api.getMetaAuthUrl().subscribe({
      next: res => {
        this.connecting.set(false);
        // Open Meta OAuth in new window
        window.open(res.auth_url, '_blank', 'width=600,height=700');
      },
      error: () => this.connecting.set(false),
    });
  }

  syncNow() {
    this.syncing.set(true);
    this.api.syncMeta().subscribe({
      next: () => { this.syncing.set(false); this.load(); },
      error: () => this.syncing.set(false),
    });
  }

  toggleAutoManage(conn: MetaConnection) {
    this.api.updateMetaSettings(conn.id, {
      auto_manage_enabled: !conn.auto_manage_enabled,
    }).subscribe(() => this.load());
  }

  tokenStatusClass(s: string) {
    return { VALID: 'active', EXPIRED: 'hot', REVOKED: 'hot', EXPIRING_SOON: 'warm' }[s] ?? 'info';
  }
}
