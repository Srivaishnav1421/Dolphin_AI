import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppIcon } from './icon';

export interface TabItem {
  id: string;
  label: string;
  icon?: string;
}

@Component({
  selector: 'ui-tabs',
  standalone: true,
  imports: [CommonModule, AppIcon],
  template: `
    <div class="ui-tabs-container">
      <div class="ui-tabs-list">
        <button
          *ngFor="let tab of tabs"
          [class.ui-tab-btn--active]="tab.id === activeTab"
          (click)="selectTab(tab.id)"
          class="ui-tab-btn"
        >
          <span *ngIf="tab.icon" class="ui-tab-icon"><app-icon [name]="tab.icon" [size]="15"></app-icon></span>
          {{ tab.label }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-tabs-container {
      width: 100%;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    }

    .ui-tabs-list {
      display: flex;
      gap: $spacing-8;
      margin-bottom: -1px;
      overflow-x: auto;
      padding-bottom: 2px;
    }

    .ui-tab-btn {
      display: inline-flex;
      align-items: center;
      gap: $spacing-8;
      min-height: 38px;
      padding: $spacing-8 $spacing-12;
      background: color-mix(in srgb, var(--bg-card) 42%, transparent);
      border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
      border-radius: 999px;
      outline: none;
      color: $text-muted;
      font-family: $font-family;
      font-size: $font-caption;
      font-weight: 500;
      cursor: pointer;
      white-space: nowrap;
      transition: all $transition-fast;

      &:hover {
        color: $text-secondary;
        background: var(--bg-card-hover);
      }
    }

    .ui-tab-btn--active {
      color: $text-primary;
      border-color: color-mix(in srgb, var(--accent) 36%, var(--border));
      background: color-mix(in srgb, var(--accent) 15%, var(--bg-card));
      box-shadow: var(--shadow-sm);
    }

    .ui-tab-icon {
      display: inline-flex;
      color: var(--accent);
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
