import { Component, Inject, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideNativeDateAdapter } from '@angular/material/core';
import { RecordPaymentRequest } from './invoice.model';

export interface PaymentDialogData {
  invoiceNumber: string;
  amountDue: number;
}

const PAYMENT_METHODS = [
  { value: 'Bank transfer', label: 'Bank transfer' },
  { value: 'Cash', label: 'Cash' },
  { value: 'Card', label: 'Card' },
  { value: 'Cheque', label: 'Cheque' },
  { value: 'Other', label: 'Other' }
];

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-payment-dialog',
  standalone: true,
  providers: [provideNativeDateAdapter()],
  imports: [
    CommonModule, DecimalPipe, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule
  ],
  template: `
    <h2 mat-dialog-title>Record payment — {{ data.invoiceNumber }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content style="min-width:420px;">
        <div style="color:#555; margin-bottom:16px;">
          Amount due: <strong>{{ data.amountDue | number:'1.2-2' }} €</strong>
        </div>

        <div style="display:flex; gap:12px; flex-wrap:wrap;">
          <mat-form-field appearance="outline" style="flex:1; min-width:160px;">
            <mat-label>Amount (€)</mat-label>
            <input matInput type="number" step="0.01" min="0.01" formControlName="amount" />
            <mat-error *ngIf="form.controls.amount.hasError('required')">Required</mat-error>
            <mat-error *ngIf="form.controls.amount.hasError('min')">Must be greater than 0</mat-error>
            <mat-error *ngIf="form.controls.amount.hasError('max')">Cannot exceed amount due</mat-error>
            <mat-error *ngIf="form.controls.amount.hasError('server')">{{ form.controls.amount.errors?.['server'] }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:1; min-width:180px;">
            <mat-label>Method</mat-label>
            <mat-select formControlName="method">
              <mat-option *ngFor="let m of methods" [value]="m.value">{{ m.label }}</mat-option>
            </mat-select>
            <mat-error *ngIf="form.controls.method.hasError('required')">Required</mat-error>
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>Date received</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="paidAt" />
          <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          <mat-error *ngIf="form.controls.paidAt.hasError('required')">Required</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>Notes (optional)</mat-label>
          <textarea matInput rows="2" formControlName="notes"></textarea>
        </mat-form-field>

        <div *ngIf="form.errors?.['server']" style="color:#b71c1c; margin-top:8px;">
          {{ form.errors?.['server'] }}
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button type="button" mat-button (click)="ref.close()" [disabled]="submitting">Cancel</button>
        <button type="submit" mat-raised-button color="primary" [disabled]="submitting || form.invalid">
          Record payment
        </button>
      </mat-dialog-actions>
    </form>
  `
})
export class PaymentDialogComponent {
  private fb = inject(FormBuilder);
  methods = PAYMENT_METHODS;
  submitting = false;
  form;

  constructor(
    public ref: MatDialogRef<PaymentDialogComponent, RecordPaymentRequest>,
    @Inject(MAT_DIALOG_DATA) public data: PaymentDialogData
  ) {
    this.form = this.fb.group({
      amount: [data.amountDue, [Validators.required, Validators.min(0.01), Validators.max(data.amountDue)]],
      method: ['Bank transfer', Validators.required],
      paidAt: [new Date(), Validators.required],
      notes: ['']
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const paidAt = v.paidAt instanceof Date ? v.paidAt.toISOString().slice(0, 10) : todayIso();
    this.ref.close({
      amount: Number(v.amount),
      method: v.method!,
      paidAt,
      notes: v.notes?.trim() || undefined
    });
  }
}
