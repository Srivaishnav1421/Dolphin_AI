import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TimelineStep {
  id: string;
  label: string;
  status: 'completed' | 'active' | 'pending' | 'failed';
  timestamp?: string;
}

@Component({
  selector: 'ui-timeline',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="ui-timeline" [class.ui-timeline--horizontal]="horizontal">
      <div *ngFor="let step of steps; let i = index; let last = last" class="ui-timeline-item" [class.ui-timeline-item--last]="last">
        <div class="ui-timeline-node-wrapper">
          <div [class]="'ui-timeline-node ui-timeline-node--' + step.status" (click)="onStepClick(step.id)">
            <span *ngIf="step.status === 'completed'" class="ui-timeline-icon">✓</span>
            <span *ngIf="step.status === 'failed'" class="ui-timeline-icon">✕</span>
            <span *ngIf="step.status === 'active'" class="ui-timeline-pulse"></span>
          </div>
          <div *ngIf="!last" [class]="'ui-timeline-line ui-timeline-line--' + step.status"></div>
        </div>
        
        <div class="ui-timeline-content">
          <div class="ui-timeline-label" [class.ui-timeline-label--active]="step.status === 'active'">
            {{ step.label }}
          </div>
          <div *ngIf="step.timestamp" class="ui-timeline-time">
            {{ step.timestamp }}
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .ui-timeline {
      display: flex;
      flex-direction: column;
      width: 100%;
    }

    .ui-timeline--horizontal {
      flex-direction: row;
      justify-content: space-between;
      align-items: center;
      
      .ui-timeline-item {
        flex: 1;
        flex-direction: column;
        align-items: center;
        text-align: center;
        
        &--last {
          flex: none;
        }
      }

      .ui-timeline-node-wrapper {
        flex-direction: row;
        align-items: center;
        width: 100%;
      }

      .ui-timeline-line {
        width: 100%;
        height: 2px;
        margin-top: 0;
        margin-left: 0;
      }
    }

    .ui-timeline-item {
      display: flex;
      gap: $spacing-12;
      position: relative;
    }

    .ui-timeline-node-wrapper {
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    .ui-timeline-node {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: $font-meta;
      font-weight: 700;
      transition: all $transition-fast;
      z-index: 2;
    }

    .ui-timeline-node--completed {
      background: $color-success;
      color: $bg-primary;
    }

    .ui-timeline-node--failed {
      background: $color-danger;
      color: $text-primary;
    }

    .ui-timeline-node--active {
      background: $accent-primary;
      color: $text-primary;
      box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.15);
    }

    .ui-timeline-node--pending {
      background: $bg-elevated;
      border: 2px solid $border-color;
      color: $text-muted;
    }

    .ui-timeline-line {
      width: 2px;
      flex-grow: 1;
      min-height: 24px;
      background: $border-color;
      z-index: 1;
    }

    .ui-timeline-line--completed {
      background: $color-success;
    }

    .ui-timeline-line--failed {
      background: $color-danger;
    }

    .ui-timeline-line--active {
      background: $accent-primary;
    }

    .ui-timeline-content {
      padding-bottom: $spacing-24;
      display: flex;
      flex-direction: column;
      gap: $spacing-4;
    }

    .ui-timeline-label {
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 500;
      color: $text-secondary;
    }

    .ui-timeline-label--active {
      color: $text-primary;
      font-weight: 600;
    }

    .ui-timeline-time {
      font-family: $font-family;
      font-size: $font-meta;
      color: $text-muted;
    }

    .ui-timeline-pulse {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: $text-primary;
      animation: pulse 1.5s infinite;
    }

    @keyframes pulse {
      0% { transform: scale(0.9); opacity: 0.6; }
      50% { transform: scale(1.2); opacity: 1; }
      100% { transform: scale(0.9); opacity: 0.6; }
    }
  `]
})
export class UiTimeline {
  @Input() steps: TimelineStep[] = [];
  @Input() horizontal = false;
  @Output() stepSelected = new EventEmitter<string>();

  onStepClick(id: string) {
    this.stepSelected.emit(id);
  }
}
