import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ClientsService } from './clients.service';
import { Client } from './client.model';
import { ClientFormDialogComponent } from './client-form-dialog.component';
import { ClientDetailsDialogComponent } from './client-details-dialog.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { extractErrorDetail } from '../../core/utils/http-errors';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [
    CommonModule, DatePipe, FormsModule,
    MatTableModule, MatSortModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressSpinnerModule, MatIconModule, MatSnackBarModule,
    TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">{{ 'clients.title' | translate }}</h1>
        <button mat-raised-button color="primary" (click)="openCreate()">
          <mat-icon>add</mat-icon> {{ 'clients.new' | translate }}
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
        <ng-container *ngIf="service.clients().length === 0">
          <div style="text-align:center; padding:48px; color:#777;">
            <mat-icon style="font-size:48px; width:48px; height:48px;">people_outline</mat-icon>
            <p style="margin-top:16px;">{{ 'clients.empty' | translate }}</p>
          </div>
        </ng-container>

        <ng-container *ngIf="service.clients().length > 0">
          <mat-form-field appearance="outline" style="width:320px; margin-bottom:8px;">
            <mat-label>{{ 'common.search' | translate }}</mat-label>
            <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" [placeholder]="'clients.searchPlaceholder' | translate" />
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>

          <ng-container *ngIf="visible().length === 0">
            <div style="padding:24px; color:#777;">{{ 'clients.noMatch' | translate:{ search: search() } }}</div>
          </ng-container>

          <table
            *ngIf="visible().length > 0"
            mat-table
            matSort
            (matSortChange)="onSort($event)"
            [dataSource]="visible()"
            style="width:100%; background:white;">

            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'clients.columns.name' | translate }}</th>
              <td mat-cell *matCellDef="let c">{{ c.name }}</td>
            </ng-container>

            <ng-container matColumnDef="email">
              <th mat-header-cell *matHeaderCellDef>{{ 'clients.columns.email' | translate }}</th>
              <td mat-cell *matCellDef="let c">{{ c.email }}</td>
            </ng-container>

            <ng-container matColumnDef="phone">
              <th mat-header-cell *matHeaderCellDef>{{ 'clients.columns.phone' | translate }}</th>
              <td mat-cell *matCellDef="let c">{{ c.phone || '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="vatNumber">
              <th mat-header-cell *matHeaderCellDef>{{ 'clients.columns.vat' | translate }}</th>
              <td mat-cell *matCellDef="let c">{{ c.vatNumber || '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'clients.columns.created' | translate }}</th>
              <td mat-cell *matCellDef="let c">{{ c.createdAt | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef style="width:112px;"></th>
              <td mat-cell *matCellDef="let c">
                <button mat-icon-button (click)="openEdit(c); $event.stopPropagation()" [attr.aria-label]="'common.edit' | translate">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button (click)="archive(c); $event.stopPropagation()" [attr.aria-label]="'common.delete' | translate">
                  <mat-icon>delete</mat-icon>
                </button>
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
export class ClientsComponent implements OnInit {
  service = inject(ClientsService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private t = inject(TranslateService);
  columns = ['name', 'email', 'phone', 'vatNumber', 'createdAt', 'actions'];

  search = signal('');
  sort = signal<Sort>({ active: 'createdAt', direction: 'desc' });

  visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    const filtered = term
      ? this.service.clients().filter(c =>
          c.name.toLowerCase().includes(term) || c.email.toLowerCase().includes(term))
      : this.service.clients();

    const { active, direction } = this.sort();
    if (!direction) return filtered;

    const sorted = [...filtered].sort((a, b) => this.compare(a, b, active));
    return direction === 'asc' ? sorted : sorted.reverse();
  });

  ngOnInit(): void {
    this.service.loadAll();
  }

  onSort(s: Sort): void {
    this.sort.set(s);
  }

  openCreate(): void {
    const ref = this.dialog.open(ClientFormDialogComponent, { data: {} });
    ref.afterClosed().subscribe((created?: Client) => {
      if (created) this.service.clients.update(list => [created, ...list]);
    });
  }

  openEdit(client: Client): void {
    const ref = this.dialog.open(ClientFormDialogComponent, { data: { client } });
    ref.afterClosed().subscribe((updated?: Client) => {
      if (updated) {
        this.service.clients.update(list =>
          list.map(c => c.id === updated.id ? updated : c)
        );
      }
    });
  }

  openDetails(client: Client): void {
    const ref = this.dialog.open(ClientDetailsDialogComponent, { data: client });
    ref.afterClosed().subscribe(action => {
      if (action === 'edit') this.openEdit(client);
    });
  }

  archive(client: Client): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant('clients.deleteConfirm.title'),
        message: this.t.instant('clients.deleteConfirm.message', { name: client.name }),
        confirmLabel: this.t.instant('clients.deleteConfirm.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'warn'
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.service.archive(client.id).subscribe({
        next: res => {
          this.service.clients.update(list => list.filter(c => c.id !== client.id));
          const msg = res.mode === 'ARCHIVED'
            ? this.t.instant('clients.archived', { name: client.name })
            : this.t.instant('clients.deleted', { name: client.name });
          this.snack.open(msg, this.t.instant('common.dismiss'), { duration: 3000 });
        },
        error: err => {
          this.snack.open(
            extractErrorDetail(err, this.t.instant('clients.deleteFailed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }

  private compare(a: Client, b: Client, key: string): number {
    if (key === 'name') return a.name.localeCompare(b.name);
    if (key === 'createdAt') return a.createdAt.localeCompare(b.createdAt);
    return 0;
  }
}
