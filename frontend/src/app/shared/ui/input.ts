import { Component, Input, Output, EventEmitter, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NG_VALUE_ACCESSOR, ControlValueAccessor, FormsModule } from '@angular/forms';

@Component({
  selector: 'ui-input',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ui-input-wrapper">
      <label *ngIf="label" class="ui-label">{{ label }}</label>
      <input
        [type]="type"
        [placeholder]="placeholder"
        [disabled]="disabled"
        [value]="value"
        (input)="onInput($event)"
        (blur)="onBlur()"
        class="ui-input"
      />
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-input-wrapper {
      display: flex;
      flex-direction: column;
      gap: $spacing-8;
      width: 100%;
    }

    .ui-label {
      font-family: $font-family;
      font-size: $font-meta;
      font-weight: 500;
      color: $text-secondary;
    }

    .ui-input {
      width: 100%;
      background: $bg-surface;
      border: 1px solid $border-color;
      border-radius: $radius-md;
      padding: $spacing-8 $spacing-12;
      color: $text-primary;
      font-family: $font-family;
      font-size: $font-caption;
      outline: none;
      transition: all $transition-fast;

      &:focus {
        border-color: $accent-primary;
        box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15);
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  `],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => UiInput),
      multi: true
    }
  ]
})
export class UiInput implements ControlValueAccessor {
  @Input() label = '';
  @Input() type = 'text';
  @Input() placeholder = '';
  @Input() disabled = false;

  value = '';

  onChange: any = () => {};
  onTouched: any = () => {};

  writeValue(val: any): void {
    this.value = val || '';
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  onInput(event: Event) {
    const val = (event.target as HTMLInputElement).value;
    this.value = val;
    this.onChange(val);
  }

  onBlur() {
    this.onTouched();
  }
}

@Component({
  selector: 'ui-search',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ui-search-box">
      <span class="ui-search-icon">🔍</span>
      <input
        type="text"
        [placeholder]="placeholder"
        [value]="value"
        (input)="onSearchInput($event)"
        class="ui-search-input"
      />
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-search-box {
      display: flex;
      align-items: center;
      gap: $spacing-8;
      width: 100%;
      background: $bg-surface;
      border: 1px solid $border-color;
      border-radius: $radius-md;
      padding: $spacing-8 $spacing-12;
      transition: all $transition-fast;

      &:focus-within {
        border-color: $accent-primary;
        box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.15);
      }
    }

    .ui-search-icon {
      font-size: $font-caption;
      color: $text-muted;
    }

    .ui-search-input {
      width: 100%;
      background: transparent;
      border: none;
      outline: none;
      color: $text-primary;
      font-family: $font-family;
      font-size: $font-caption;
      &::placeholder {
        color: $text-muted;
      }
    }
  `]
})
export class UiSearch {
  @Input() placeholder = 'Search...';
  @Input() value = '';
  @Output() search = new EventEmitter<string>();

  onSearchInput(event: Event) {
    const val = (event.target as HTMLInputElement).value;
    this.value = val;
    this.search.emit(val);
  }
}
