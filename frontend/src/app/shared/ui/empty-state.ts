import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="ui-empty-state">
      <div class="ui-empty-state__icon">{{ icon }}</div>
      <h3 class="ui-empty-state__title">{{ title }}</h3>
      <p class="ui-empty-state__desc">{{ description }}</p>
      <div *ngIf="actionLabel" class="ui-empty-state__action">
        <button (click)="onAction()" class="ui-empty-state__btn">
          {{ actionLabel }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: $spacing-48 $spacing-24;
      width: 100%;
    }

    .ui-empty-state__icon {
      font-size: 40px;
      margin-bottom: $spacing-16;
      color: $text-muted;
    }

    .ui-empty-state__title {
      font-family: $font-family;
      font-size: $font-body;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: $spacing-8;
    }

    .ui-empty-state__desc {
      font-family: $font-family;
      font-size: $font-caption;
      color: $text-secondary;
      max-width: 320px;
      margin-bottom: $spacing-24;
      line-height: 1.5;
    }

    .ui-empty-state__btn {
      background: $accent-primary;
      color: $text-primary;
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 500;
      padding: $spacing-8 $spacing-16;
      border-radius: $radius-md;
      border: none;
      outline: none;
      cursor: pointer;
      transition: opacity $transition-fast;

      &:hover {
        opacity: 0.9;
      }
    }
  `]
})
export class UiEmptyState {
  @Input() icon = '📁';
  @Input() title = 'No items found';
  @Input() description = 'Get started by creating a new entry.';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();

  onAction() {
    this.action.emit();
  }
}
