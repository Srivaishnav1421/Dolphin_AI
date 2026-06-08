import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { filter } from 'rxjs/operators';

export interface AppEvent {
  type: string;      // Event name/type
  payload: any;      // Main event payload
  source?: string;   // Sender component/service identifier
  timestamp: number; // Unix timestamp
}

@Injectable({
  providedIn: 'root'
})
export class EventBusService {
  private eventSubject = new Subject<AppEvent>();

  // Expose the raw event stream if needed
  public events$: Observable<AppEvent> = this.eventSubject.asObservable();

  /**
   * Publish a new event to the global bus.
   */
  public publish(event: Omit<AppEvent, 'timestamp'>): void {
    this.eventSubject.next({
      ...event,
      timestamp: Date.now()
    });
  }

  /**
   * Filter events by a specific type pattern or category.
   */
  public ofType(type: string): Observable<AppEvent> {
    return this.events$.pipe(
      filter(event => event.type === type)
    );
  }

  /**
   * Filter events using a custom predicate function.
   */
  public filter(predicate: (event: AppEvent) => boolean): Observable<AppEvent> {
    return this.events$.pipe(
      filter(predicate)
    );
  }
}
