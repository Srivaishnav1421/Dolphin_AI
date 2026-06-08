import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TraceGroupItem {
  traceId: string;
  name: string;
  stepsCount: number;
  status: string;
  timestamp: string;
}

@Component({
  selector: 'app-trace-explorer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="trace-explorer">
      <div class="trace-explorer__header">
        <h4>Recent Trace Chains</h4>
      </div>
      <div class="trace-explorer__list">
        <div
          *ngFor="let trace of traces"
          [class.trace-explorer-item--active]="trace.traceId === activeTraceId"
          (click)="selectTrace(trace.traceId)"
          class="trace-explorer-item"
        >
          <div class="trace-explorer-item__body">
            <span class="trace-item-title">{{ trace.name }}</span>
            <span [class]="'trace-status-dot trace-status-dot--' + trace.status.toLowerCase()"></span>
          </div>
          <div class="trace-explorer-item__footer">
            <span>{{ trace.stepsCount }} nodes</span>
            <span>•</span>
            <span>{{ trace.timestamp | slice:11:16 }}</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .trace-explorer {
      width: 100%;
      background: $bg-surface;
      border: 1px solid $border-color;
      border-radius: $radius-lg;
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    .trace-explorer__header {
      padding: $spacing-12 $spacing-16;
      border-bottom: 1px solid rgba(255, 255, 255, 0.02);
      h4 {
        font-family: $font-family;
        font-size: $font-caption;
        font-weight: 600;
        color: $text-primary;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
    }

    .trace-explorer__list {
      flex: 1;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      padding: $spacing-8 0;
    }

    .trace-explorer-item {
      padding: $spacing-12 $spacing-16;
      cursor: pointer;
      border-left: 2px solid transparent;
      transition: all $transition-fast;

      &:hover {
        background: rgba(255, 255, 255, 0.015);
      }
    }

    .trace-explorer-item--active {
      background: rgba(99, 102, 241, 0.05);
      border-left-color: $accent-primary;
      .trace-item-title {
        color: $accent-primary;
      }
    }

    .trace-explorer-item__body {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: $spacing-4;
    }

    .trace-item-title {
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 500;
      color: $text-secondary;
    }

    .trace-status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;
      
      &--completed { background: $color-success; }
      &--running { background: $accent-primary; }
      &--failed { background: $color-danger; }
      &--waiting_for_approval { background: $color-warning; }
    }

    .trace-explorer-item__footer {
      display: flex;
      gap: $spacing-8;
      font-family: $font-family;
      font-size: 10px;
      color: $text-muted;
    }
  `]
})
export class TraceExplorer {
  @Input() traces: TraceGroupItem[] = [];
  @Input() activeTraceId: string | null = null;
  @Output() traceSelected = new EventEmitter<string>();

  selectTrace(id: string) {
    this.traceSelected.emit(id);
  }
}
