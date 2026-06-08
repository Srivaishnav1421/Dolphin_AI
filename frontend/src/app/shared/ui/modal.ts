import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="show" class="ui-modal-backdrop" (click)="onClose()">
      <div class="ui-modal-container" (click)="$event.stopPropagation()">
        <div class="ui-modal-header">
          <h3 class="ui-modal-title">{{ title }}</h3>
          <button class="ui-modal-close-btn" (click)="onClose()">✕</button>
        </div>
        <div class="ui-modal-body">
          <ng-content></ng-content>
        </div>
      </div>
    </div>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .ui-modal-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: $z-modal;
      animation: fadeIn $transition-fast forwards;
    }

    .ui-modal-container {
      background: $bg-elevated;
      border: 1px solid $border-color;
      border-radius: $radius-lg;
      width: 90%;
      max-width: 500px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 10px 10px -5px rgba(0, 0, 0, 0.4);
      display: flex;
      flex-direction: column;
      animation: slideUp $transition-normal forwards;
    }

    .ui-modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: $spacing-16 $spacing-24;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    }

    .ui-modal-title {
      font-family: $font-family;
      font-size: $font-body;
      font-weight: 600;
      color: $text-primary;
    }

    .ui-modal-close-btn {
      background: transparent;
      border: none;
      outline: none;
      color: $text-muted;
      font-size: $font-body;
      cursor: pointer;
      transition: color $transition-fast;

      &:hover {
        color: $text-primary;
      }
    }

    .ui-modal-body {
      padding: $spacing-24;
      overflow-y: auto;
      max-height: 80vh;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @keyframes slideUp {
      from { transform: translateY(12px) scale(0.98); }
      to { transform: translateY(0) scale(1); }
    }
  `]
})
export class UiModal {
  @Input() show = false;
  @Input() title = '';
  @Output() closed = new EventEmitter<void>();

  onClose() {
    this.closed.emit();
  }
}
