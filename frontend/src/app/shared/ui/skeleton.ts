import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      [class]="'ui-skeleton ui-skeleton--' + type"
      [style.width]="width"
      [style.height]="height"
    ></div>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .ui-skeleton {
      background: linear-gradient(
        90deg,
        rgba(255, 255, 255, 0.03) 25%,
        rgba(255, 255, 255, 0.08) 37%,
        rgba(255, 255, 255, 0.03) 63%
      );
      background-size: 400% 100%;
      animation: shimmer 1.4s ease infinite;
    }

    .ui-skeleton--text {
      border-radius: $radius-sm;
      height: 14px;
      margin-bottom: $spacing-8;
    }

    .ui-skeleton--rect {
      border-radius: $radius-md;
      height: 80px;
    }

    .ui-skeleton--circle {
      border-radius: 50%;
      width: 40px;
      height: 40px;
    }

    @keyframes shimmer {
      0% {
        background-position: 100% 50%;
      }
      100% {
        background-position: 0% 50%;
      }
    }
  `]
})
export class UiSkeleton {
  @Input() type: 'text' | 'rect' | 'circle' = 'text';
  @Input() width = '100%';
  @Input() height = '';
}
