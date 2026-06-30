import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      [type]="type"
      [disabled]="disabled || loading"
      [class]="'ui-btn ui-btn--' + variant"
      (click)="onClick($event)"
    >
      <span *ngIf="loading" class="spinner"></span>
      <ng-content *ngIf="!loading"></ng-content>
    </button>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: $spacing-8;
      padding: $spacing-8 $spacing-16;
      border-radius: $radius-md;
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 500;
      cursor: pointer;
      border: none;
      outline: none;
      transition: all $transition-fast;
      white-space: nowrap;

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }

    .ui-btn--primary {
      background: $accent-primary;
      color: $text-primary;
      &:hover:not(:disabled) {
        opacity: 0.9;
        box-shadow: $shadow-sm;
      }
    }

    .ui-btn--secondary {
      background: $bg-elevated;
      color: $text-secondary;
      border: 1px solid $border-color;
      &:hover:not(:disabled) {
        background: $border-color;
        color: $text-primary;
      }
    }

    .ui-btn--danger {
      background: rgba(239, 68, 68, 0.1);
      color: $color-danger;
      border: 1px solid rgba(239, 68, 68, 0.2);
      &:hover:not(:disabled) {
        background: rgba(239, 68, 68, 0.2);
      }
    }

    .spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: currentColor;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class UiButton {
  @Input() type: 'button' | 'submit' = 'button';
  @Input() variant: 'primary' | 'secondary' | 'danger' = 'secondary';
  @Input() disabled = false;
  @Input() loading = false;
  @Output() clicked = new EventEmitter<MouseEvent>();

  onClick(event: MouseEvent) {
    if (!this.disabled && !this.loading) {
      this.clicked.emit(event);
    }
  }
}
