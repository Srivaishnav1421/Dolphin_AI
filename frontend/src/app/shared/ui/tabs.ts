import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TabItem {
  id: string;
  label: string;
  icon?: string;
}

@Component({
  selector: 'ui-tabs',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="ui-tabs-container">
      <div class="ui-tabs-list">
        <button
          *ngFor="let tab of tabs"
          [class.ui-tab-btn--active]="tab.id === activeTab"
          (click)="selectTab(tab.id)"
          class="ui-tab-btn"
        >
          <span *ngIf="tab.icon" class="ui-tab-icon">{{ tab.icon }}</span>
          {{ tab.label }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .ui-tabs-container {
      width: 100%;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    }

    .ui-tabs-list {
      display: flex;
      gap: $spacing-16;
      margin-bottom: -1px;
    }

    .ui-tab-btn {
      display: inline-flex;
      align-items: center;
      gap: $spacing-8;
      padding: $spacing-8 0;
      background: transparent;
      border: none;
      outline: none;
      color: $text-muted;
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 500;
      cursor: pointer;
      border-bottom: 2px solid transparent;
      transition: all $transition-fast;

      &:hover {
        color: $text-secondary;
      }
    }

    .ui-tab-btn--active {
      color: $text-primary;
      border-bottom-color: $accent-primary;
    }

    .ui-tab-icon {
      font-size: $font-body;
    }
  `]
})
export class UiTabs {
  @Input() tabs: TabItem[] = [];
  @Input() activeTab = '';
  @Output() tabChange = new EventEmitter<string>();

  selectTab(id: string) {
    this.activeTab = id;
    this.tabChange.emit(id);
  }
}
