import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StateService } from '../../core/services/state.service';
import { CommandService, CommandItem } from '../../core/services/command.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'ui-command-palette',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div *ngIf="state.isCommandPaletteOpen()" class="palette-backdrop" (click)="close()">
      <div class="palette-container" (click)="$event.stopPropagation()">
        <div class="palette-input-wrapper">
          <span class="palette-search-icon">⚡</span>
          <input
            #searchInput
            type="text"
            placeholder="Type a command or search..."
            [(ngModel)]="searchQuery"
            (input)="onSearch()"
            (keydown)="onKeydown($event)"
            class="palette-input"
          />
          <span class="palette-esc-badge">ESC</span>
        </div>
        
        <div class="palette-results" *ngIf="filteredCommands.length > 0">
          <div *ngFor="let cat of categories" class="palette-category-group">
            <div class="palette-category-header">{{ cat }}</div>
            <div
              *ngFor="let cmd of getCommandsByCategory(cat)"
              [class.palette-item--selected]="cmd === selectedCommand"
              (click)="execute(cmd)"
              (mouseenter)="selectItem(cmd)"
              class="palette-item"
            >
              <div class="palette-item-info">
                <span class="palette-item-name">{{ cmd.name }}</span>
              </div>
              <span *ngIf="cmd.shortcut" class="palette-item-shortcut">{{ cmd.shortcut }}</span>
            </div>
          </div>
        </div>

        <div *ngIf="filteredCommands.length === 0" class="palette-empty">
          No commands found matching "{{ searchQuery }}"
        </div>
        
        <div class="palette-footer">
          <span>↑↓ to navigate</span>
          <span>↵ to execute</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    @import '../../../styles/design-tokens.scss';

    .palette-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(8px);
      display: flex;
      justify-content: center;
      padding-top: 15vh;
      z-index: $z-tooltip;
      animation: fadeIn $transition-fast forwards;
    }

    .palette-container {
      background: $bg-surface;
      border: 1px solid $border-color;
      border-radius: $radius-lg;
      width: 90%;
      max-width: 600px;
      max-height: 400px;
      box-shadow: 0 30px 60px rgba(0, 0, 0, 0.6);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      animation: zoomIn $transition-fast forwards;
    }

    .palette-input-wrapper {
      display: flex;
      align-items: center;
      gap: $spacing-12;
      padding: $spacing-16 $spacing-24;
      border-bottom: 1px solid $border-color;
    }

    .palette-search-icon {
      font-size: $font-body;
      color: $text-muted;
    }

    .palette-input {
      flex: 1;
      background: transparent;
      border: none;
      outline: none;
      color: $text-primary;
      font-family: $font-family;
      font-size: $font-body;
      &::placeholder {
        color: $text-muted;
      }
    }

    .palette-esc-badge {
      font-family: $font-family;
      font-size: 10px;
      font-weight: 700;
      color: $text-muted;
      border: 1px solid $border-color;
      padding: 2px $spacing-8;
      border-radius: $radius-sm;
    }

    .palette-results {
      flex: 1;
      overflow-y: auto;
      padding: $spacing-8 0;
    }

    .palette-category-group {
      margin-bottom: $spacing-8;
    }

    .palette-category-header {
      padding: $spacing-8 $spacing-24;
      font-family: $font-family;
      font-size: 10px;
      font-weight: 700;
      color: $text-muted;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .palette-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: $spacing-8 $spacing-24;
      cursor: pointer;
      transition: all $transition-fast;

      &:hover {
        background: rgba(255, 255, 255, 0.02);
      }
    }

    .palette-item--selected {
      background: rgba(99, 102, 241, 0.1);
      .palette-item-name {
        color: $accent-primary;
      }
    }

    .palette-item-name {
      font-family: $font-family;
      font-size: $font-caption;
      color: $text-secondary;
      font-weight: 500;
    }

    .palette-item-shortcut {
      font-family: $font-family;
      font-size: $font-meta;
      color: $text-muted;
      border: 1px solid rgba(255, 255, 255, 0.05);
      background: rgba(255, 255, 255, 0.02);
      padding: 1px 4px;
      border-radius: $radius-sm;
    }

    .palette-empty {
      padding: $spacing-32;
      text-align: center;
      color: $text-muted;
      font-family: $font-family;
      font-size: $font-caption;
    }

    .palette-footer {
      display: flex;
      gap: $spacing-16;
      padding: $spacing-8 $spacing-24;
      border-top: 1px solid rgba(255, 255, 255, 0.03);
      background: rgba(0, 0, 0, 0.2);
      font-family: $font-family;
      font-size: 10px;
      color: $text-muted;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @keyframes zoomIn {
      from { transform: scale(0.97); opacity: 0.5; }
      to { transform: scale(1); opacity: 1; }
    }
  `]
})
export class UiCommandPalette implements OnInit {
  searchQuery = '';
  filteredCommands: CommandItem[] = [];
  categories: string[] = [];
  selectedCommand: CommandItem | null = null;

  constructor(public state: StateService, private commandService: CommandService) {}

  ngOnInit() {
    this.resetSearch();
  }

  onSearch() {
    const query = this.searchQuery.toLowerCase().trim();
    const all = this.commandService.getCommands();
    if (!query) {
      this.filteredCommands = all;
    } else {
      this.filteredCommands = all.filter(cmd => 
        cmd.name.toLowerCase().includes(query) || 
        cmd.category.toLowerCase().includes(query)
      );
    }
    this.updateCategories();
    this.selectFirst();
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      this.close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.navigateSelection(1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.navigateSelection(-1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (this.selectedCommand) {
        this.execute(this.selectedCommand);
      }
    }
  }

  navigateSelection(direction: number) {
    if (this.filteredCommands.length === 0) return;
    const index = this.filteredCommands.indexOf(this.selectedCommand!);
    let nextIndex = index + direction;
    if (nextIndex < 0) nextIndex = this.filteredCommands.length - 1;
    if (nextIndex >= this.filteredCommands.length) nextIndex = 0;
    this.selectedCommand = this.filteredCommands[nextIndex];
  }

  selectItem(cmd: CommandItem) {
    this.selectedCommand = cmd;
  }

  execute(cmd: CommandItem) {
    cmd.action();
    this.close();
  }

  close() {
    this.state.isCommandPaletteOpen.set(false);
    this.resetSearch();
  }

  private resetSearch() {
    this.searchQuery = '';
    this.filteredCommands = this.commandService.getCommands();
    this.updateCategories();
    this.selectFirst();
  }

  private selectFirst() {
    this.selectedCommand = this.filteredCommands.length > 0 ? this.filteredCommands[0] : null;
  }

  private updateCategories() {
    const cats = new Set(this.filteredCommands.map(cmd => cmd.category));
    this.categories = Array.from(cats);
  }

  getCommandsByCategory(cat: string): CommandItem[] {
    return this.filteredCommands.filter(cmd => cmd.category === cat);
  }
}
