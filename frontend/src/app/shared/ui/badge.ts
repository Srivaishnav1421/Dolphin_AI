import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="'ui-badge ui-badge--' + variant">
      <span *ngIf="dot" class="ui-badge__dot"></span>
      <ng-content></ng-content>
    </span>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .ui-badge {
      display: inline-flex;
      align-items: center;
      gap: $spacing-4;
      padding: 2px $spacing-8;
      border-radius: 12px;
      font-family: $font-family;
      font-size: $font-meta;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .ui-badge--success {
      background: rgba(16, 185, 129, 0.1);
      color: $color-success;
      border: 1px solid rgba(16, 185, 129, 0.2);
      .ui-badge__dot { background: $color-success; }
    }

    .ui-badge--warning {
      background: rgba(245, 158, 11, 0.1);
      color: $color-warning;
      border: 1px solid rgba(245, 158, 11, 0.2);
      .ui-badge__dot { background: $color-warning; }
    }

    .ui-badge--danger {
      background: rgba(239, 68, 68, 0.1);
      color: $color-danger;
      border: 1px solid rgba(239, 68, 68, 0.2);
      .ui-badge__dot { background: $color-danger; }
    }

    .ui-badge--info {
      background: rgba(99, 102, 241, 0.1);
      color: $accent-primary;
      border: 1px solid rgba(99, 102, 241, 0.2);
      .ui-badge__dot { background: $accent-primary; }
    }

    .ui-badge--muted {
      background: rgba(113, 113, 122, 0.1);
      color: $text-muted;
      border: 1px solid rgba(113, 113, 122, 0.2);
      .ui-badge__dot { background: $text-muted; }
    }

    .ui-badge__dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;
    }
  `]
})
export class UiBadge {
  @Input() variant: 'success' | 'warning' | 'danger' | 'info' | 'muted' = 'muted';
  @Input() dot = false;
}
