import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EMPTY, Subject, interval, Subscription } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { Client } from '@stomp/stompjs';
import { AuthService } from './auth.service';
import { RuntimeConfigService } from './runtime-config.service';

// Snake_case to match BrainEvent model and all template references
export interface WsEvent {
  id:          string;
  event_type:  string;
  message:     string;
  severity:    'INFO' | 'WARNING' | 'CRITICAL' | 'SUCCESS';
  account_id:  string;
  created_at:  string;
}

@Injectable({ providedIn: 'root' })
export class WebsocketService implements OnDestroy {

  events$    = new Subject<WsEvent>();
  executions$ = new Subject<any>();
  workflows$  = new Subject<any>();
  connected$ = new Subject<boolean>();

  private stompClient: Client | null = null;
  private pollSub: Subscription | null = null;
  private lastEventId: string | null = null;
  private readonly POLL_URL: string;
  private readonly WS_URL: string;
  private readonly TOPIC     = '/topic/brain-events';

  constructor(private http: HttpClient, private auth: AuthService, config: RuntimeConfigService) {
    this.POLL_URL = `${config.apiBase}/api/brain/events/recent`;
    this.WS_URL = `${config.wsBase}/ws/brain`;
  }

  connect(token: string) {
    // 1. Attempt real STOMP WebSocket connection first
    this.connectStomp(token);
  }

  private connectStomp(token: string) {
    if (this.stompClient?.connected) return;

    this.stompClient = new Client({
      // SockJS WebSocket endpoint
      webSocketFactory: () => {
        try {
          // Dynamically import SockJS to avoid SSR issues
          const SockJS = (window as any).SockJS;
          if (SockJS) return new SockJS(this.WS_URL);
        } catch {}
        return new WebSocket(this.WS_URL.replace('http', 'ws'));
      },
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: () => {
        this.connected$.next(true);

        const accountId = this.auth.currentUser()?.account_id;
        const brainEventsTopic = accountId ? `/topic/workspace/${accountId}/brain-events` : this.TOPIC;
        const executionsTopic = accountId ? `/topic/workspace/${accountId}/brain` : '/topic/brain/executions';
        const workflowsTopic = accountId ? `/topic/workspace/${accountId}/workflow` : '/topic/workflows';

        this.stompClient!.subscribe(brainEventsTopic, (msg: any) => {
          try {
            const event: WsEvent = JSON.parse(msg.body);
            if (event?.id && event.id !== this.lastEventId) {
              this.lastEventId = event.id;
              this.events$.next(event);
            }
          } catch { /* ignore parse errors */ }
        });
        this.stompClient!.subscribe(executionsTopic, (msg: any) => {
          try {
            const exec = JSON.parse(msg.body);
            this.executions$.next(exec);
          } catch { /* ignore parse errors */ }
        });
        this.stompClient!.subscribe(workflowsTopic, (msg: any) => {
          try {
            const wfEvent = JSON.parse(msg.body);
            this.workflows$.next(wfEvent);
          } catch { /* ignore parse errors */ }
        });
        // Stop polling now that real WS is up
        this.stopPolling();
      },

      onDisconnect: () => {
        this.connected$.next(false);
        // Fall back to polling if STOMP disconnects
        this.startPolling();
      },

      onStompError: () => {
        // STOMP failed — fall back to REST polling gracefully
        this.startPolling();
      },
    });

    try {
      this.stompClient.activate();
    } catch {
      // WebSocket not available — use polling fallback
      this.startPolling();
    }
  }

  private startPolling() {
    if (this.pollSub) return;
    this.connected$.next(true);

    // Poll every 8 seconds for new brain events. Errors are not converted to
    // fake empty activity because that can make live data look healthy.
    this.pollSub = interval(8000).pipe(
      switchMap(() =>
        this.http.get<WsEvent[]>(this.POLL_URL).pipe(
          catchError(() => {
            this.connected$.next(false);
            return EMPTY;
          })
        )
      )
    ).subscribe(events => {
      if (!events?.length) return;
      const newest = events[0];
      if (newest?.id && newest.id !== this.lastEventId) {
        this.lastEventId = newest.id;
        this.events$.next(newest);
      }
    });
  }

  private stopPolling() {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }

  disconnect() {
    this.stompClient?.deactivate();
    this.stompClient = null;
    this.stopPolling();
    this.connected$.next(false);
  }

  ngOnDestroy() { this.disconnect(); }
}
