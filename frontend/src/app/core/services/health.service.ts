import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { RuntimeConfigService } from './runtime-config.service';

export interface DatabaseHealth {
  database: 'UP' | 'DOWN';
  connected: boolean;
  latency_ms?: number;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  private base: string;

  constructor(private http: HttpClient, config: RuntimeConfigService) {
    this.base = config.apiBase;
  }

  getDatabaseHealth(): Observable<DatabaseHealth> {
    return this.http.get<DatabaseHealth>(`${this.base}/api/health/database`, {
      params: { _: Date.now() },
      headers: {
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache'
      }
    }).pipe(
      map(status => ({
        ...status,
        connected: status.database === 'UP' && status.connected === true
      }))
    );
  }
}
