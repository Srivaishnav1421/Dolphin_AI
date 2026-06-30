import { Component, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-timeline-playback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="playback-controls">
      <div class="playback-buttons">
        <button (click)="stepBack()" [disabled]="selectedIndex <= 0" class="playback-btn" title="Previous step">◀</button>
        <button (click)="togglePlay()" class="playback-btn playback-btn--main">
          {{ isPlaying ? '⏸' : '▶' }}
        </button>
        <button (click)="stepForward()" [disabled]="selectedIndex >= maxSteps - 1" class="playback-btn" title="Next step">▶</button>
      </div>

      <div class="playback-timeline">
        <input
          type="range"
          min="0"
          [max]="maxSteps - 1"
          [value]="selectedIndex"
          (input)="onSliderChange($event)"
          class="playback-slider"
        />
        <span class="playback-meta">Step {{ selectedIndex + 1 }} of {{ maxSteps }}</span>
      </div>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .playback-controls {
      display: flex;
      align-items: center;
      gap: $spacing-24;
      background: $bg-surface;
      border: 1px solid $border-color;
      padding: $spacing-12 $spacing-24;
      border-radius: $radius-lg;
      width: 100%;
    }

    .playback-buttons {
      display: flex;
      gap: $spacing-8;
    }

    .playback-btn {
      background: $bg-elevated;
      border: 1px solid $border-color;
      color: $text-secondary;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: $font-caption;
      transition: all $transition-fast;

      &:hover:not(:disabled) {
        background: $border-color;
        color: $text-primary;
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }

      &--main {
        background: $accent-primary;
        color: $text-primary;
        border-color: $accent-primary;
        &:hover {
          opacity: 0.9;
        }
      }
    }

    .playback-timeline {
      flex: 1;
      display: flex;
      align-items: center;
      gap: $spacing-16;
    }

    .playback-slider {
      flex: 1;
      accent-color: $accent-primary;
      cursor: pointer;
    }

    .playback-meta {
      font-family: $font-family;
      font-size: $font-meta;
      color: $text-muted;
      white-space: nowrap;
    }
  `]
})
export class TimelinePlayback implements OnInit, OnDestroy {
  @Input() maxSteps = 1;
  @Input() selectedIndex = 0;
  @Output() indexChange = new EventEmitter<number>();

  isPlaying = false;
  private intervalId: any = null;

  ngOnInit() {}

  ngOnDestroy() {
    this.stopPlayback();
  }

  togglePlay() {
    this.isPlaying = !this.isPlaying;
    if (this.isPlaying) {
      this.startPlayback();
    } else {
      this.stopPlayback();
    }
  }

  startPlayback() {
    this.intervalId = setInterval(() => {
      if (this.selectedIndex < this.maxSteps - 1) {
        this.selectedIndex++;
        this.indexChange.emit(this.selectedIndex);
      } else {
        this.selectedIndex = 0; // Loop playback
        this.indexChange.emit(this.selectedIndex);
      }
    }, 1500);
  }

  stopPlayback() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  stepBack() {
    this.stopPlayback();
    this.isPlaying = false;
    if (this.selectedIndex > 0) {
      this.selectedIndex--;
      this.indexChange.emit(this.selectedIndex);
    }
  }

  stepForward() {
    this.stopPlayback();
    this.isPlaying = false;
    if (this.selectedIndex < this.maxSteps - 1) {
      this.selectedIndex++;
      this.indexChange.emit(this.selectedIndex);
    }
  }

  onSliderChange(event: Event) {
    this.stopPlayback();
    this.isPlaying = false;
    const val = parseInt((event.target as HTMLInputElement).value, 10);
    this.selectedIndex = val;
    this.indexChange.emit(val);
  }
}
