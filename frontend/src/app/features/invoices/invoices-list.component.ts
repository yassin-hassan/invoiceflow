import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { InvoicesService } from './invoices.service';
import { Invoice, InvoiceStatus } from './invoice.model';
import { InvoiceStatusChipComponent } from './invoice-status-chip.component';
import { ExportDialogComponent } from '../exports/export-dialog.component';

type StatusFilter = 'ALL' | InvoiceStatus;

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-invoices-list',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, FormsModule,
    MatTableModule, MatSortModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatButtonToggleModule, MatProgressSpinnerModule, MatIconModule,
    MatDialogModule, InvoiceStatusChipComponent, TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">{{ 'invoices.title' | translate }}</h1>
        <div style="display:flex; gap:8px;">
          <button mat-stroked-button (click)="openExport()">
            <mat-icon>download</mat-icon> {{ 'exports.button' | translate }}
          </button>
          <button mat-raised-button color="primary" (click)="openCreate()">
            <mat-icon>add</mat-icon> {{ 'invoices.new' | translate }}
          </button>
        </div>
      </div>

      <ng-container *ngIf="service.loading()">
        <div style="display:flex; justify-content:center; padding:48px;">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      </ng-container>

      <ng-container *ngIf="!service.loading() && service.error()">
        <div style="padding:16px; background:#fdecea; color:#b71c1c; border-radius:4px;">
          {{ service.error() }}
        </div>
      </ng-container>

      <ng-container *ngIf="!service.loading() && !service.error()">
        <ng-container *ngIf="service.invoices().length === 0">
          <div style="text-align:center; padding:48px; color:#777;">
            <mat-icon style="font-size:48px; width:48px; height:48px;">receipt_long</mat-icon>
            <p style="margin-top:16px;">{{ 'invoices.empty' | translate }}</p>
          </div>
        </ng-container>

        <ng-container *ngIf="service.invoices().length > 0">
          <div style="display:flex; gap:16px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
            <mat-form-field appearance="outline" style="width:320px; margin:0;">
              <mat-label>{{ 'common.search' | translate }}</mat-label>
              <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" [placeholder]="'invoices.searchPlaceholder' | translate" />
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>

            <mat-button-toggle-group
              [value]="statusFilter()"
              (change)="statusFilter.set($event.value)"
              style="height:40px; flex-wrap:wrap;">
              <mat-button-toggle value="ALL">{{ 'invoices.filters.all' | translate }}</mat-button-toggle>
              <mat-button-toggle value="DRAFT">{{ 'invoices.filters.draft' | translate }}</mat-button-toggle>
              <mat-button-toggle value="SENT">{{ 'invoices.filters.sent' | translate }}</mat-button-toggle>
              <mat-button-toggle value="PARTIALLY_PAID">{{ 'invoices.filters.partiallyPaid' | translate }}</mat-button-toggle>
              <mat-button-toggle value="PAID">{{ 'invoices.filters.paid' | translate }}</mat-button-toggle>
              <mat-button-toggle value="OVERDUE">{{ 'invoices.filters.overdue' | translate }}</mat-button-toggle>
              <mat-button-toggle value="CANCELLED">{{ 'invoices.filters.cancelled' | translate }}</mat-button-toggle>
            </mat-button-toggle-group>
          </div>

          <ng-container *ngIf="visible().length === 0">
            <div style="padding:24px; color:#777;">{{ 'invoices.noMatchFilters' | translate }}</div>
          </ng-container>

          <table
            *ngIf="visible().length > 0"
            mat-table
            matSort
            (matSortChange)="onSort($event)"
            [dataSource]="visible()"
            style="width:100%; background:white;">

            <ng-container matColumnDef="number">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'invoices.columns.number' | translate }}</th>
              <td mat-cell *matCellDef="let inv">
                <span *ngIf="inv.number; else draftLabel">{{ inv.number }}</span>
                <ng-template #draftLabel><span style="color:#999; font-style:italic;">{{ 'common.draft' | translate }}</span></ng-template>
              </td>
            </ng-container>

            <ng-container matColumnDef="client">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'invoices.columns.client' | translate }}</th>
              <td mat-cell *matCellDef="let inv">{{ inv.clientName }}</td>
            </ng-container>

            <ng-container matColumnDef="issueDate">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'invoices.columns.issueDate' | translate }}</th>
              <td mat-cell *matCellDef="let inv">{{ inv.issueDate | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="dueDate">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'invoices.columns.dueDate' | translate }}</th>
              <td mat-cell *matCellDef="let inv">{{ inv.dueDate | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>{{ 'invoices.columns.status' | translate }}</th>
              <td mat-cell *matCellDef="let inv">
                <app-invoice-status-chip [status]="inv.status"></app-invoice-status-chip>
              </td>
            </ng-container>

            <ng-container matColumnDef="totalInclVat">
              <th mat-header-cell *matHeaderCellDef mat-sort-header style="text-align:right;">{{ 'invoices.columns.totalTtc' | translate }}</th>
              <td mat-cell *matCellDef="let inv" style="text-align:right;">
                {{ inv.totalInclVat | number:'1.2-2' }} €
              </td>
            </ng-container>

            <ng-container matColumnDef="amountDue">
              <th mat-header-cell *matHeaderCellDef mat-sort-header style="text-align:right;">{{ 'invoices.columns.amountDue' | translate }}</th>
              <td mat-cell *matCellDef="let inv" style="text-align:right;"
                  [style.color]="inv.amountDue > 0 ? '#b71c1c' : '#1b5e20'"
                  [style.font-weight]="inv.amountDue > 0 ? '500' : '400'">
                {{ inv.amountDue | number:'1.2-2' }} €
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row
                *matRowDef="let row; columns: columns;"
                (click)="openDetails(row)"
                [style.cursor]="'pointer'"
                [style.border-left]="isVisuallyOverdue(row) ? '3px solid #b71c1c' : '3px solid transparent'"></tr>
          </table>
        </ng-container>
      </ng-container>
    </div>
  `
})
export class InvoicesListComponent implements OnInit {
  service = inject(InvoicesService);
  private router = inject(Router);
  private dialog = inject(MatDialog);
  columns = ['number', 'client', 'issueDate', 'dueDate', 'status', 'totalInclVat', 'amountDue'];

  search = signal('');
  statusFilter = signal<StatusFilter>('ALL');
  sort = signal<Sort>({ active: 'issueDate', direction: 'desc' });

  visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    let list = this.service.invoices();

    if (status !== 'ALL') {
      list = list.filter(inv => inv.status === status);
    }
    if (term) {
      list = list.filter(inv =>
        (inv.number?.toLowerCase().includes(term) ?? false) ||
        inv.clientName.toLowerCase().includes(term)
      );
    }

    const { active, direction } = this.sort();
    if (!direction) return list;

    const sorted = [...list].sort((a, b) => this.compare(a, b, active));
    return direction === 'asc' ? sorted : sorted.reverse();
  });

  ngOnInit(): void {
    this.service.loadAll();
  }

  onSort(s: Sort): void {
    this.sort.set(s);
  }

  openCreate(): void {
    this.router.navigate(['/invoices/new']);
  }

  openExport(): void {
    this.dialog.open(ExportDialogComponent, { width: '480px' });
  }

  openDetails(invoice: Invoice): void {
    this.router.navigate(['/invoices', invoice.id]);
  }

  isVisuallyOverdue(inv: Invoice): boolean {
    if (inv.status === 'PAID' || inv.status === 'CANCELLED') return false;
    return inv.amountDue > 0 && inv.dueDate < todayIso();
  }

  private compare(a: Invoice, b: Invoice, key: string): number {
    if (key === 'number') return (a.number ?? '').localeCompare(b.number ?? '');
    if (key === 'client') return a.clientName.localeCompare(b.clientName);
    if (key === 'issueDate') return a.issueDate.localeCompare(b.issueDate);
    if (key === 'dueDate') return a.dueDate.localeCompare(b.dueDate);
    if (key === 'totalInclVat') return a.totalInclVat - b.totalInclVat;
    if (key === 'amountDue') return a.amountDue - b.amountDue;
    return 0;
  }
}
