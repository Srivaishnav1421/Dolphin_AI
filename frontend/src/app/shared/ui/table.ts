import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ui-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="ui-table-container">
      <table class="ui-table">
        <thead>
          <tr>
            <th *ngFor="let col of columns" class="ui-table__th">
              {{ col.header }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data" class="ui-table__tr">
            <td *ngFor="let col of columns" class="ui-table__td">
              <ng-container *ngIf="col.cellRenderer; else defaultCell">
                <ng-container *ngTemplateOutlet="col.cellRenderer; context: { $implicit: item }"></ng-container>
              </ng-container>
              <ng-template #defaultCell>
                {{ item[col.field] }}
              </ng-template>
            </td>
          </tr>
          <tr *ngIf="data.length === 0">
            <td [attr.colspan]="columns.length" class="ui-table__empty">
              No records found.
            </td>
          </tr>
        </tbody>
      </table>
      
      <div *ngIf="pagination" class="ui-table-pagination">
        <span class="ui-table-pagination__info">
          Page {{ page }} of {{ maxPage }}
        </span>
        <div class="ui-table-pagination__actions">
          <button [disabled]="page === 1" (click)="onPageChange(page - 1)" class="ui-table-pagination__btn">Prev</button>
          <button [disabled]="page === maxPage || maxPage <= 1" (click)="onPageChange(page + 1)" class="ui-table-pagination__btn">Next</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .ui-table-container {
      width: 100%;
      overflow-x: auto;
    }

    .ui-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }

    .ui-table__th {
      padding: $spacing-12 $spacing-16;
      font-family: $font-family;
      font-size: $font-meta;
      font-weight: 700;
      color: $text-muted;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      border-bottom: 1px solid $border-color;
    }

    .ui-table__tr {
      transition: background-color $transition-fast;
      &:hover {
        background: rgba(255, 255, 255, 0.015);
      }
    }

    .ui-table__td {
      padding: $spacing-12 $spacing-16;
      font-family: $font-family;
      font-size: $font-caption;
      color: $text-secondary;
      border-bottom: 1px solid rgba(255, 255, 255, 0.02);
    }

    .ui-table__empty {
      padding: $spacing-32;
      text-align: center;
      color: $text-muted;
      font-family: $font-family;
      font-size: $font-caption;
    }

    .ui-table-pagination {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: $spacing-12 $spacing-16;
      border-top: 1px solid $border-color;
    }

    .ui-table-pagination__info {
      font-family: $font-family;
      font-size: $font-meta;
      color: $text-muted;
    }

    .ui-table-pagination__actions {
      display: flex;
      gap: $spacing-8;
    }

    .ui-table-pagination__btn {
      background: $bg-elevated;
      color: $text-secondary;
      border: 1px solid $border-color;
      border-radius: $radius-sm;
      padding: $spacing-4 $spacing-8;
      font-family: $font-family;
      font-size: $font-meta;
      cursor: pointer;
      transition: all $transition-fast;

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }

      &:hover:not(:disabled) {
        background: $border-color;
        color: $text-primary;
      }
    }
  `]
})
export class UiTable {
  @Input() columns: Array<{ header: string; field: string; cellRenderer?: any }> = [];
  @Input() data: any[] = [];
  @Input() pagination = false;
  @Input() page = 1;
  @Input() maxPage = 1;
  @Output() pageChange = new EventEmitter<number>();

  onPageChange(p: number) {
    this.pageChange.emit(p);
  }
}
