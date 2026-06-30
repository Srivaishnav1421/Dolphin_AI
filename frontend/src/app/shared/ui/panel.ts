import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div [class]="'ui-panel ui-panel--' + elevation" [style.padding]="padding">
      <div *ngIf="title" class="ui-panel__header">
        <h4 class="ui-panel__title">{{ title }}</h4>
        <div class="ui-panel__actions">
          <ng-content select="[actions]"></ng-content>
        </div>
      </div>
      <div class="ui-panel__content">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-panel {
      border-radius: $radius-lg;
      width: 100%;
      box-shadow: $shadow-sm;
      transition: border-color $transition-fast;
    }

    .ui-panel--surface {
      background: $bg-surface;
      border: 1px solid $border-color;
    }

    .ui-panel--elevated {
      background: var(--bg-card);
      border: 1px solid var(--border);
    }

    .ui-panel--flat {
      background: $bg-primary;
      border: 1px solid $border-color;
    }

    .ui-panel__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: $spacing-16;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
      padding-bottom: $spacing-8;
    }

    .ui-panel__title {
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 600;
      color: $text-primary;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
  `]
})
export class UiPanel {
  @Input() title = '';
  @Input() elevation: 'surface' | 'elevated' | 'flat' = 'surface';
  @Input() padding = '20px';
}
