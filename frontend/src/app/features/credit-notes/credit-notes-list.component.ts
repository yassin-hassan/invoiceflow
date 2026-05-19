import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { CreditNotesService } from './credit-notes.service';
import { CreditNote, CreditNoteStatus } from './credit-note.model';
import { CreditNoteStatusChipComponent } from './credit-note-status-chip.component';

type StatusFilter = 'ALL' | CreditNoteStatus;

@Component({
  selector: 'app-credit-notes-list',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, FormsModule,
    MatTableModule, MatSortModule, MatFormFieldModule, MatInputModule,
    MatButtonToggleModule, MatProgressSpinnerModule, MatIconModule,
    CreditNoteStatusChipComponent, TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">{{ 'creditNotes.title' | translate }}</h1>
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
        <ng-container *ngIf="service.creditNotes().length === 0">
          <div style="text-align:center; padding:48px; color:#777;">
            <mat-icon style="font-size:48px; width:48px; height:48px;">undo</mat-icon>
            <p style="margin-top:16px;">{{ 'creditNotes.empty' | translate }}</p>
            <p style="font-size:0.9rem;">{{ 'creditNotes.emptyHint' | translate }}</p>
          </div>
        </ng-container>

        <ng-container *ngIf="service.creditNotes().length > 0">
          <div style="display:flex; gap:16px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
            <mat-form-field appearance="outline" style="width:320px; margin:0;">
              <mat-label>{{ 'common.search' | translate }}</mat-label>
              <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" [placeholder]="'creditNotes.searchPlaceholder' | translate" />
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>

            <mat-button-toggle-group
              [value]="statusFilter()"
              (change)="statusFilter.set($event.value)"
              style="height:40px;">
              <mat-button-toggle value="ALL">{{ 'creditNotes.filters.all' | translate }}</mat-button-toggle>
              <mat-button-toggle value="DRAFT">{{ 'creditNotes.filters.draft' | translate }}</mat-button-toggle>
              <mat-button-toggle value="ISSUED">{{ 'creditNotes.filters.issued' | translate }}</mat-button-toggle>
            </mat-button-toggle-group>
          </div>

          <ng-container *ngIf="visible().length === 0">
            <div style="padding:24px; color:#777;">{{ 'creditNotes.noMatchFilters' | translate }}</div>
          </ng-container>

          <table
            *ngIf="visible().length > 0"
            mat-table
            matSort
            (matSortChange)="onSort($event)"
            [dataSource]="visible()"
            style="width:100%; background:white;">

            <ng-container matColumnDef="number">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'creditNotes.columns.number' | translate }}</th>
              <td mat-cell *matCellDef="let cn">
                <span *ngIf="cn.number; else draftLabel">{{ cn.number }}</span>
                <ng-template #draftLabel><span style="color:#999; font-style:italic;">{{ 'common.draft' | translate }}</span></ng-template>
              </td>
            </ng-container>

            <ng-container matColumnDef="invoice">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'creditNotes.columns.invoice' | translate }}</th>
              <td mat-cell *matCellDef="let cn">{{ cn.originalInvoiceNumber || '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="client">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'creditNotes.columns.client' | translate }}</th>
              <td mat-cell *matCellDef="let cn">{{ cn.clientName }}</td>
            </ng-container>

            <ng-container matColumnDef="issueDate">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'creditNotes.columns.issueDate' | translate }}</th>
              <td mat-cell *matCellDef="let cn">{{ cn.issueDate | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>{{ 'creditNotes.columns.status' | translate }}</th>
              <td mat-cell *matCellDef="let cn">
                <app-credit-note-status-chip [status]="cn.status"></app-credit-note-status-chip>
              </td>
            </ng-container>

            <ng-container matColumnDef="totalInclVat">
              <th mat-header-cell *matHeaderCellDef mat-sort-header style="text-align:right;">{{ 'creditNotes.columns.totalTtc' | translate }}</th>
              <td mat-cell *matCellDef="let cn" style="text-align:right; color:#b71c1c;">
                -{{ cn.totalInclVat | number:'1.2-2' }} €
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row
                *matRowDef="let row; columns: columns;"
                (click)="open(row)"
                [style.cursor]="'pointer'"></tr>
          </table>
        </ng-container>
      </ng-container>
    </div>
  `
})
export class CreditNotesListComponent implements OnInit {
  service = inject(CreditNotesService);
  private router = inject(Router);
  columns = ['number', 'invoice', 'client', 'issueDate', 'status', 'totalInclVat'];

  search = signal('');
  statusFilter = signal<StatusFilter>('ALL');
  sort = signal<Sort>({ active: 'issueDate', direction: 'desc' });

  visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    let list = this.service.creditNotes();

    if (status !== 'ALL') {
      list = list.filter(cn => cn.status === status);
    }
    if (term) {
      list = list.filter(cn =>
        (cn.number?.toLowerCase().includes(term) ?? false) ||
        (cn.originalInvoiceNumber?.toLowerCase().includes(term) ?? false) ||
        cn.clientName.toLowerCase().includes(term)
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

  open(cn: CreditNote): void {
    this.router.navigate(['/credit-notes', cn.id]);
  }

  private compare(a: CreditNote, b: CreditNote, key: string): number {
    if (key === 'number') return (a.number ?? '').localeCompare(b.number ?? '');
    if (key === 'invoice') return (a.originalInvoiceNumber ?? '').localeCompare(b.originalInvoiceNumber ?? '');
    if (key === 'client') return a.clientName.localeCompare(b.clientName);
    if (key === 'issueDate') return a.issueDate.localeCompare(b.issueDate);
    if (key === 'totalInclVat') return a.totalInclVat - b.totalInclVat;
    return 0;
  }
}
