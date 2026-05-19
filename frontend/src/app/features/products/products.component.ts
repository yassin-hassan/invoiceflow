import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProductsService } from './products.service';
import { Product } from './product.model';
import { ProductFormDialogComponent } from './product-form-dialog.component';
import { ProductDetailsDialogComponent } from './product-details-dialog.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { extractErrorDetail } from '../../core/utils/http-errors';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, FormsModule,
    MatTableModule, MatSortModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressSpinnerModule, MatIconModule, MatSnackBarModule,
    TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">{{ 'products.title' | translate }}</h1>
        <button mat-raised-button color="primary" (click)="openCreate()">
          <mat-icon>add</mat-icon> {{ 'products.new' | translate }}
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
        <ng-container *ngIf="service.products().length === 0">
          <div style="text-align:center; padding:48px; color:#777;">
            <mat-icon style="font-size:48px; width:48px; height:48px;">inventory_2</mat-icon>
            <p style="margin-top:16px;">{{ 'products.empty' | translate }}</p>
          </div>
        </ng-container>

        <ng-container *ngIf="service.products().length > 0">
          <mat-form-field appearance="outline" style="width:320px; margin-bottom:8px;">
            <mat-label>{{ 'common.search' | translate }}</mat-label>
            <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" [placeholder]="'products.searchPlaceholder' | translate" />
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>

          <ng-container *ngIf="visible().length === 0">
            <div style="padding:24px; color:#777;">{{ 'products.noMatch' | translate:{ search: search() } }}</div>
          </ng-container>

          <table
            *ngIf="visible().length > 0"
            mat-table
            matSort
            (matSortChange)="onSort($event)"
            [dataSource]="visible()"
            style="width:100%; background:white;">

            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'products.columns.name' | translate }}</th>
              <td mat-cell *matCellDef="let p">{{ p.name }}</td>
            </ng-container>

            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'products.columns.reference' | translate }}</th>
              <td mat-cell *matCellDef="let p">{{ p.reference }}</td>
            </ng-container>

            <ng-container matColumnDef="unitPrice">
              <th mat-header-cell *matHeaderCellDef mat-sort-header style="text-align:right;">{{ 'products.columns.unitPrice' | translate }}</th>
              <td mat-cell *matCellDef="let p" style="text-align:right;">
                {{ p.unitPrice | number:'1.2-2' }} €
              </td>
            </ng-container>

            <ng-container matColumnDef="vatRate">
              <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'products.columns.vat' | translate }}</th>
              <td mat-cell *matCellDef="let p" style="text-align:right;">{{ p.vatRate }}%</td>
            </ng-container>

            <ng-container matColumnDef="unit">
              <th mat-header-cell *matHeaderCellDef>{{ 'products.columns.unit' | translate }}</th>
              <td mat-cell *matCellDef="let p">{{ ('products.units.' + p.unit) | translate }}</td>
            </ng-container>

            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'products.columns.created' | translate }}</th>
              <td mat-cell *matCellDef="let p">{{ p.createdAt | date:'mediumDate' }}</td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef style="width:112px;"></th>
              <td mat-cell *matCellDef="let p">
                <button mat-icon-button (click)="openEdit(p); $event.stopPropagation()" [attr.aria-label]="'common.edit' | translate">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button (click)="remove(p); $event.stopPropagation()" [attr.aria-label]="'common.delete' | translate">
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
export class ProductsComponent implements OnInit {
  service = inject(ProductsService);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private t = inject(TranslateService);
  columns = ['name', 'reference', 'unitPrice', 'vatRate', 'unit', 'createdAt', 'actions'];

  search = signal('');
  sort = signal<Sort>({ active: 'createdAt', direction: 'desc' });

  visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    const filtered = term
      ? this.service.products().filter(p =>
          p.name.toLowerCase().includes(term) || p.reference.toLowerCase().includes(term))
      : this.service.products();

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
    const ref = this.dialog.open(ProductFormDialogComponent, { data: {} });
    ref.afterClosed().subscribe((created?: Product) => {
      if (created) this.service.products.update(list => [created, ...list]);
    });
  }

  openEdit(product: Product): void {
    const ref = this.dialog.open(ProductFormDialogComponent, { data: { product } });
    ref.afterClosed().subscribe((updated?: Product) => {
      if (updated) {
        this.service.products.update(list =>
          list.map(p => p.id === updated.id ? updated : p)
        );
      }
    });
  }

  openDetails(product: Product): void {
    const ref = this.dialog.open(ProductDetailsDialogComponent, { data: product });
    ref.afterClosed().subscribe(action => {
      if (action === 'edit') this.openEdit(product);
    });
  }

  remove(product: Product): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant('products.deleteConfirm.title'),
        message: this.t.instant('products.deleteConfirm.message', { name: product.name }),
        confirmLabel: this.t.instant('products.deleteConfirm.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'warn'
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.service.remove(product.id).subscribe({
        next: res => {
          this.service.products.update(list => list.filter(p => p.id !== product.id));
          const msg = res.mode === 'ARCHIVED'
            ? this.t.instant('products.archived', { name: product.name })
            : this.t.instant('products.deleted', { name: product.name });
          this.snack.open(msg, this.t.instant('common.dismiss'), { duration: 3000 });
        },
        error: err => {
          this.snack.open(
            extractErrorDetail(err, this.t.instant('products.deleteFailed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }

  private compare(a: Product, b: Product, key: string): number {
    if (key === 'name') return a.name.localeCompare(b.name);
    if (key === 'reference') return a.reference.localeCompare(b.reference);
    if (key === 'unitPrice') return a.unitPrice - b.unitPrice;
    if (key === 'createdAt') return a.createdAt.localeCompare(b.createdAt);
    return 0;
  }
}
