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
import { TranslateModule } from '@ngx-translate/core';
import { QuotesService } from './quotes.service';
import { Quote, QuoteStatus } from './quote.model';
import { StatusChipComponent } from './status-chip.component';

type StatusFilter = 'ALL' | QuoteStatus;

@Component({
  selector: 'app-quotes-list',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, FormsModule,
    MatTableModule, MatSortModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatButtonToggleModule, MatProgressSpinnerModule, MatIconModule,
    StatusChipComponent, TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">{{ 'quotes.title' | translate }}</h1>
        <button mat-raised-button color="primary" (click)="openCreate()">
          <mat-icon>add</mat-icon> {{ 'quotes.new' | translate }}
        </button>
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
        <ng-container *ngIf="service.quotes().length === 0">
          <div style="text-align:center; padding:48px; color:#777;">
            <mat-icon style="font-size:48px; width:48px; height:48px;">request_quote</mat-icon>
            <p style="margin-top:16px;">{{ 'quotes.empty' | translate }}</p>
          </div>
        </ng-container>

        <ng-container *ngIf="service.quotes().length > 0">
          <div style="display:flex; gap:16px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
            <mat-form-field appearance="outline" style="width:320px; margin:0;">
              <mat-label>{{ 'common.search' | translate }}</mat-label>
              <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" [placeholder]="'quotes.searchPlaceholder' | translate" />
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>

            <mat-button-toggle-group
              [value]="statusFilter()"
              (change)="statusFilter.set($event.value)"
              style="height:40px;">
              <mat-button-toggle value="ALL">{{ 'quotes.filters.all' | translate }}</mat-button-toggle>
              <mat-button-toggle value="DRAFT">{{ 'quotes.filters.draft' | translate }}</mat-button-toggle>
              <mat-button-toggle value="SENT">{{ 'quotes.filters.sent' | translate }}</mat-button-toggle>
              <mat-button-toggle value="ACCEPTED">{{ 'quotes.filters.accepted' | translate }}</mat-button-toggle>
              <mat-button-toggle value="REJECTED">{{ 'quotes.filters.rejected' | translate }}</mat-button-toggle>
              <mat-button-toggle value="CONVERTED">{{ 'quotes.filters.converted' | translate }}</mat-button-toggle>
            </mat-button-toggle-group>
          </div>

          <ng-container *ngIf="visible().length === 0">
            <div style="padding:24px; color:#777;">{{ 'quotes.noMatchFilters' | translate }}</div>
          </ng-container>

          <table
            *ngIf="visible().length > 0"
            mat-table
            matSort
            (matSortChange)="onSort($event)"
            [dataSource]="visible()"
            style="width:100%; background:white;">

            <ng-container matColumnDef="number">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'quotes.columns.number' | translate }}</th>
              <td mat-cell *matCellDef="let q">{{ q.number }}</td>
            </ng-container>

            <ng-container matColumnDef="client">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'quotes.columns.client' | translate }}</th>
              <td mat-cell *matCellDef="let q">{{ q.clientName }}</td>
            </ng-container>

            <ng-container matColumnDef="issueDate">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'quotes.columns.issueDate' | translate }}</th>
              <td mat-cell *matCellDef="let q">{{ q.issueDate | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>{{ 'quotes.columns.status' | translate }}</th>
              <td mat-cell *matCellDef="let q">
                <app-status-chip [status]="q.status"></app-status-chip>
              </td>
            </ng-container>

            <ng-container matColumnDef="totalInclVat">
              <th mat-header-cell *matHeaderCellDef mat-sort-header style="text-align:right;">{{ 'quotes.columns.totalTtc' | translate }}</th>
              <td mat-cell *matCellDef="let q" style="text-align:right;">
                {{ q.totalInclVat | number:'1.2-2' }} €
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row
                *matRowDef="let row; columns: columns;"
                (click)="openDetails(row)"
                style="cursor:pointer;"></tr>
          </table>
        </ng-container>
      </ng-container>
    </div>
  `
})
export class QuotesListComponent implements OnInit {
  service = inject(QuotesService);
  private router = inject(Router);
  columns = ['number', 'client', 'issueDate', 'status', 'totalInclVat'];

  search = signal('');
  statusFilter = signal<StatusFilter>('ALL');
  sort = signal<Sort>({ active: 'issueDate', direction: 'desc' });

  visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    let list = this.service.quotes();

    if (status !== 'ALL') {
      list = list.filter(q => q.status === status);
    }
    if (term) {
      list = list.filter(q =>
        q.number.toLowerCase().includes(term) ||
        q.clientName.toLowerCase().includes(term)
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
    this.router.navigate(['/quotes/new']);
  }

  openDetails(quote: Quote): void {
    this.router.navigate(['/quotes', quote.id]);
  }

  private compare(a: Quote, b: Quote, key: string): number {
    if (key === 'number') return a.number.localeCompare(b.number);
    if (key === 'client') return a.clientName.localeCompare(b.clientName);
    if (key === 'issueDate') return a.issueDate.localeCompare(b.issueDate);
    if (key === 'totalInclVat') return a.totalInclVat - b.totalInclVat;
    return 0;
  }
}
