import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface FeedItem {
  id: string;
  type: string;
  message: string;
  timestamp: string;
  badge?: string;
  badgeVariant?: 'success' | 'warning' | 'danger' | 'info' | 'muted';
}

@Component({
  selector: 'ui-activity-feed',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="ui-feed">
      <div *ngFor="let item of items" class="ui-feed-item">
        <div class="ui-feed-marker">
          <span class="ui-feed-dot" [class]="'ui-feed-dot--' + (item.badgeVariant || 'muted')"></span>
          <span class="ui-feed-line"></span>
        </div>
        
        <div class="ui-feed-content">
          <div class="ui-feed-meta">
            <span class="ui-feed-time">{{ item.timestamp }}</span>
            <span *ngIf="item.badge" [class]="'ui-feed-badge ui-feed-badge--' + (item.badgeVariant || 'muted')">
              {{ item.badge }}
            </span>
          </div>
          <p class="ui-feed-text">{{ item.message }}</p>
        </div>
      </div>
      
      <div *ngIf="items.length === 0" class="ui-feed-empty">
        No activities registered.
      </div>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-feed {
      display: flex;
      flex-direction: column;
      width: 100%;
    }

    .ui-feed-item {
      display: flex;
      gap: $spacing-16;
      position: relative;
      
      &:last-child .ui-feed-line {
        display: none;
      }
    }

    .ui-feed-marker {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding-top: 4px;
    }

    .ui-feed-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: $text-muted;
      border: 2px solid $bg-primary;
      z-index: 2;
    }

    .ui-feed-dot--success { background: $color-success; }
    .ui-feed-dot--warning { background: $color-warning; }
    .ui-feed-dot--danger  { background: $color-danger; }
    .ui-feed-dot--info    { background: $accent-primary; }
    .ui-feed-dot--muted   { background: $text-muted; }

    .ui-feed-line {
      width: 2px;
      flex-grow: 1;
      background: $border-color;
      margin: $spacing-4 0;
      z-index: 1;
    }

    .ui-feed-content {
      flex: 1;
      padding-bottom: $spacing-16;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .ui-feed-meta {
      display: flex;
      align-items: center;
      gap: $spacing-8;
    }

    .ui-feed-time {
      font-family: $font-family;
      font-size: $font-meta;
      color: $text-muted;
    }

    .ui-feed-badge {
      font-family: $font-family;
      font-size: 10px;
      font-weight: 700;
      padding: 0px $spacing-4;
      border-radius: $radius-sm;
      text-transform: uppercase;
    }

    .ui-feed-badge--success { background: rgba(16, 185, 129, 0.1); color: $color-success; }
    .ui-feed-badge--warning { background: rgba(245, 158, 11, 0.1); color: $color-warning; }
    .ui-feed-badge--danger  { background: rgba(239, 68, 68, 0.1); color: $color-danger; }
    .ui-feed-badge--info    { background: rgba(99, 102, 241, 0.1); color: $accent-primary; }
    .ui-feed-badge--muted   { background: rgba(113, 113, 122, 0.1); color: $text-muted; }

    .ui-feed-text {
      font-family: $font-family;
      font-size: $font-caption;
      color: $text-secondary;
      line-height: 1.4;
    }

    .ui-feed-empty {
      padding: $spacing-16 0;
      color: $text-muted;
      font-family: $font-family;
      font-size: $font-caption;
    }
  `]
})
export class UiActivityFeed {
  @Input() items: FeedItem[] = [];
}
