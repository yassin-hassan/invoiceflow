import { Component, Inject, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { provideNativeDateAdapter } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { Invoice, InvoiceLine } from '../invoices/invoice.model';
import { CreateCreditNoteRequest, CreditNoteLineRequest } from './credit-note.model';

export interface CreditNoteFormDialogData {
  invoice: Invoice;
}

type LineFormGroup = FormGroup<{
  include: FormControl<boolean>;
  quantity: FormControl<number>;
}>;

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-credit-note-form-dialog',
  standalone: true,
  providers: [provideNativeDateAdapter()],
  imports: [
    CommonModule, DecimalPipe, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatIconModule, MatCheckboxModule, TranslateModule
  ],
  template: `
    <h2 mat-dialog-title>
      {{ 'creditNotes.form.title' | translate:{ number: data.invoice.number || ('creditNotes.form.draftInvoice' | translate) } }}
    </h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content style="min-width:560px; max-width:760px;">
        <div style="color:#666; margin-bottom:16px;">
          {{ 'creditNotes.form.intro' | translate }}
        </div>

        <table style="width:100%; border-collapse:collapse;" formArrayName="lines">
          <thead>
            <tr style="text-align:left; color:#555; font-size:0.85rem;">
              <th style="padding:6px 4px;">{{ 'creditNotes.form.credit' | translate }}</th>
              <th style="padding:6px 4px;">{{ 'creditNotes.form.description' | translate }}</th>
              <th style="padding:6px 4px; text-align:right;">{{ 'creditNotes.form.remaining' | translate }}</th>
              <th style="padding:6px 4px; text-align:right;">{{ 'creditNotes.form.unitPrice' | translate }}</th>
              <th style="padding:6px 4px; text-align:right; width:130px;">{{ 'creditNotes.form.creditQty' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of lineRows; let i = index"
                [formGroupName]="i"
                [style.opacity]="row.remaining > 0 ? 1 : 0.5"
                style="border-top:1px solid #eee;">
              <td style="padding:6px 4px;">
                <mat-checkbox formControlName="include" [disabled]="row.remaining <= 0"></mat-checkbox>
              </td>
              <td style="padding:6px 4px;">
                {{ row.line.description }}
                <div *ngIf="row.alreadyCredited > 0" style="font-size:0.75rem; color:#888;">
                  {{ 'creditNotes.form.alreadyCredited' | translate:{ credited: (row.alreadyCredited | number:'1.0-2'), total: (row.line.quantity | number:'1.0-2') } }}
                </div>
              </td>
              <td style="padding:6px 4px; text-align:right;">{{ row.remaining | number:'1.0-2' }}</td>
              <td style="padding:6px 4px; text-align:right;">{{ row.line.unitPrice | number:'1.2-2' }} €</td>
              <td style="padding:6px 4px; text-align:right;">
                <mat-form-field appearance="outline" subscriptSizing="dynamic" style="width:110px;">
                  <input matInput type="number" step="0.01" min="0" [max]="row.remaining"
                         formControlName="quantity" />
                </mat-form-field>
              </td>
            </tr>
          </tbody>
        </table>

        <div style="display:flex; justify-content:flex-end; margin-top:12px;">
          <strong style="color:#b71c1c;">
            {{ 'creditNotes.form.creditTotal' | translate:{ amount: (totalInclVat() | number:'1.2-2') } }}
          </strong>
        </div>

        <mat-form-field appearance="outline" style="width:100%; margin-top:16px;">
          <mat-label>{{ 'creditNotes.form.issueDate' | translate }}</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="issueDate" />
          <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>{{ 'creditNotes.form.reason' | translate }}</mat-label>
          <textarea matInput rows="3" formControlName="reason" [placeholder]="'creditNotes.form.reasonPlaceholder' | translate"></textarea>
          <mat-error *ngIf="form.controls.reason.hasError('required')">{{ 'creditNotes.form.required' | translate }}</mat-error>
        </mat-form-field>

        <div *ngIf="noLinesSelected()" style="color:#b71c1c; margin-top:8px; font-size:0.85rem;">
          {{ 'creditNotes.form.noLinesSelected' | translate }}
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button type="button" mat-button (click)="ref.close()" [disabled]="submitting()">{{ 'common.cancel' | translate }}</button>
        <button type="submit" mat-raised-button color="primary"
                [disabled]="submitting() || form.invalid || noLinesSelected()">
          {{ 'creditNotes.form.createDraft' | translate }}
        </button>
      </mat-dialog-actions>
    </form>
  `
})
export class CreditNoteFormDialogComponent {
  private fb = inject(FormBuilder);

  submitting = signal(false);
  totalInclVat = signal(0);
  hasSelection = signal(false);
  lineRows: { line: InvoiceLine; alreadyCredited: number; remaining: number }[] = [];
  form;

  constructor(
    public ref: MatDialogRef<CreditNoteFormDialogComponent, CreateCreditNoteRequest>,
    @Inject(MAT_DIALOG_DATA) public data: CreditNoteFormDialogData
  ) {
    const sortedLines = [...data.invoice.lines].sort((a, b) => a.sortOrder - b.sortOrder);
    const issuedByLine = new Map<string, number>();
    for (const cn of data.invoice.creditNotes) {
      if (cn.status !== 'ISSUED') continue;
      for (const cnLine of cn.lines) {
        issuedByLine.set(cnLine.invoiceLineId,
          (issuedByLine.get(cnLine.invoiceLineId) ?? 0) + cnLine.quantity);
      }
    }
    this.lineRows = sortedLines.map(l => {
      const alreadyCredited = issuedByLine.get(l.id) ?? 0;
      const remaining = Math.max(0, Math.round((l.quantity - alreadyCredited) * 100) / 100);
      return { line: l, alreadyCredited, remaining };
    });

    const lineForms = this.lineRows.map(row => this.fb.group({
      include: this.fb.nonNullable.control(false),
      quantity: this.fb.nonNullable.control(row.remaining, [Validators.min(0), Validators.max(row.remaining)])
    }));

    this.form = this.fb.group({
      reason: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(1)]),
      issueDate: this.fb.control<Date | null>(new Date()),
      lines: this.fb.array(lineForms)
    });

    this.form.valueChanges.subscribe(() => this.recompute());
    this.recompute();
  }

  get linesArray(): FormArray<LineFormGroup> {
    return this.form.controls.lines as FormArray<LineFormGroup>;
  }

  private recompute(): void {
    this.totalInclVat.set(this.computeTotal());
    this.hasSelection.set(this.linesArray.controls.some(g =>
      g.controls.include.value && Number(g.controls.quantity.value) > 0
    ));
  }

  private computeTotal(): number {
    let total = 0;
    this.linesArray.controls.forEach((group, idx) => {
      if (!group.controls.include.value) return;
      const qty = Number(group.controls.quantity.value) || 0;
      if (qty <= 0) return;
      const line = this.lineRows[idx].line;
      const excl = qty * line.unitPrice;
      const vat = excl * line.vatRate / 100;
      total += excl + vat;
    });
    return Math.round(total * 100) / 100;
  }

  noLinesSelected(): boolean {
    return !this.hasSelection();
  }

  submit(): void {
    if (this.form.invalid || this.noLinesSelected()) return;
    const reason = this.form.controls.reason.value.trim();
    if (!reason) return;
    const issueDateValue = this.form.controls.issueDate.value;
    const issueDate = issueDateValue instanceof Date
      ? issueDateValue.toISOString().slice(0, 10)
      : todayIso();

    const lines: CreditNoteLineRequest[] = [];
    let order = 0;
    this.linesArray.controls.forEach((group, idx) => {
      if (!group.controls.include.value) return;
      const qty = Number(group.controls.quantity.value) || 0;
      if (qty <= 0) return;
      lines.push({
        invoiceLineId: this.lineRows[idx].line.id,
        quantity: qty,
        sortOrder: order++
      });
    });

    const req: CreateCreditNoteRequest = { reason, issueDate, lines };
    this.ref.close(req);
  }
}

// Re-exports kept light; nothing else needed here.
