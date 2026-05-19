import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ExportsService, AccountingExportFilters } from '../../core/services/exports.service';
import { InvoiceStatus } from '../invoices/invoice.model';
import { extractErrorDetail } from '../../core/utils/http-errors';

const STATUSES: InvoiceStatus[] = ['DRAFT', 'SENT', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'];

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-export-dialog',
  standalone: true,
  providers: [provideNativeDateAdapter()],
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatSnackBarModule, MatProgressSpinnerModule,
    TranslateModule
  ],
  template: `
    <h2 mat-dialog-title>{{ 'exports.dialog.title' | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content style="min-width:420px;">
        <p style="color:#555; margin-top:0;">{{ 'exports.dialog.subtitle' | translate }}</p>

        <div style="display:flex; gap:12px; flex-wrap:wrap;">
          <mat-form-field appearance="outline" style="flex:1; min-width:180px;">
            <mat-label>{{ 'exports.dialog.from' | translate }}</mat-label>
            <input matInput [matDatepicker]="fromPicker" formControlName="from" />
            <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>
            <mat-datepicker #fromPicker></mat-datepicker>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:1; min-width:180px;">
            <mat-label>{{ 'exports.dialog.to' | translate }}</mat-label>
            <input matInput [matDatepicker]="toPicker" formControlName="to" />
            <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>
            <mat-datepicker #toPicker></mat-datepicker>
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>{{ 'exports.dialog.status' | translate }}</mat-label>
          <mat-select formControlName="status">
            <mat-option [value]="''">{{ 'exports.dialog.allStatuses' | translate }}</mat-option>
            <mat-option *ngFor="let s of statuses" [value]="s">
              {{ 'invoices.filters.' + statusKey(s) | translate }}
            </mat-option>
          </mat-select>
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button type="button" mat-button (click)="ref.close()" [disabled]="submitting()">
          {{ 'common.cancel' | translate }}
        </button>
        <button type="submit" mat-raised-button color="primary" [disabled]="submitting()">
          <mat-spinner *ngIf="submitting()" diameter="16" style="display:inline-block; margin-right:8px;"></mat-spinner>
          {{ 'exports.dialog.confirm' | translate }}
        </button>
      </mat-dialog-actions>
    </form>
  `
})
export class ExportDialogComponent {
  private fb = inject(FormBuilder);
  private exportsService = inject(ExportsService);
  private snackBar = inject(MatSnackBar);
  private t = inject(TranslateService);

  statuses = STATUSES;
  submitting = signal(false);

  form = this.fb.group({
    from: [null as Date | null],
    to: [null as Date | null],
    status: ['' as InvoiceStatus | '']
  });

  constructor(public ref: MatDialogRef<ExportDialogComponent>) {}

  statusKey(s: InvoiceStatus): string {
    if (s === 'PARTIALLY_PAID') return 'partiallyPaid';
    return s.toLowerCase();
  }

  submit(): void {
    if (this.submitting()) return;
    const v = this.form.getRawValue();
    const filters: AccountingExportFilters = {
      from: v.from ? isoDate(v.from) : undefined,
      to: v.to ? isoDate(v.to) : undefined,
      status: v.status ? v.status as InvoiceStatus : undefined
    };

    this.submitting.set(true);
    this.exportsService.downloadAccountingXlsx(filters).subscribe({
      next: blob => {
        this.submitting.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
        a.href = url;
        a.download = `accounting-export-${stamp}.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
        this.ref.close(true);
      },
      error: err => {
        this.submitting.set(false);
        this.snackBar.open(
          extractErrorDetail(err, this.t.instant('exports.dialog.failed')),
          this.t.instant('common.ok'),
          { duration: 4000 }
        );
      }
    });
  }
}
