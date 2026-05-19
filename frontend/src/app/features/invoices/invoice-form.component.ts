import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import {
  FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { InvoicesService } from './invoices.service';
import { CreateInvoiceRequest, Invoice, UpdateInvoiceRequest } from './invoice.model';
import { ClientsService } from '../clients/clients.service';
import { ProductsService } from '../products/products.service';
import { extractErrorDetail, extractFieldErrors } from '../../core/utils/http-errors';

const VAT_RATES = [0, 6, 12, 21];

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function plusDaysIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

@Component({
  selector: 'app-invoice-form',
  standalone: true,
  imports: [
    CommonModule, DecimalPipe, ReactiveFormsModule, RouterLink,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatSnackBarModule, MatProgressSpinnerModule, MatTooltipModule,
    TranslateModule
  ],
  styles: [`
    .line-card { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: #fafafa; border-radius: 4px; }
    .line-top { display: grid; grid-template-columns: 200px 1fr; gap: 12px; align-items: start; }
    .line-bottom { display: grid; grid-template-columns: 110px 140px 110px 1fr auto; gap: 12px; align-items: center; }
    .line-card mat-form-field { width: 100%; }
    .line-total { text-align: right; font-weight: 500; padding-right: 8px; }
    .line-total small { display: block; font-size: 0.75rem; color: #666; font-weight: 400; }
    .line-actions { display: flex; gap: 4px; }
  `],
  template: `
    <div style="padding:24px; max-width:1100px;">
      <a routerLink="/invoices" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        {{ 'invoices.backToList' | translate }}
      </a>

      <h1 style="margin:0 0 16px;">
        {{ editingId()
            ? (('invoices.form.editTitlePrefix' | translate) + (invoiceNumber() || ('invoices.form.fallbackTitle' | translate)))
            : ('invoices.form.newTitle' | translate) }}
      </h1>

      <ng-container *ngIf="loadingInvoice()">
        <div style="display:flex; justify-content:center; padding:48px;">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      </ng-container>

      <ng-container *ngIf="loadError()">
        <div style="padding:16px; background:#fdecea; color:#b71c1c; border-radius:4px;">
          {{ loadError() }}
        </div>
      </ng-container>

      <form *ngIf="!loadingInvoice() && !loadError()" [formGroup]="form" (ngSubmit)="onSubmit()">
        <div style="display:flex; gap:16px; flex-wrap:wrap;">
          <mat-form-field appearance="outline" style="flex:1 1 280px;">
            <mat-label>{{ 'invoices.form.client' | translate }}</mat-label>
            <mat-select formControlName="clientId">
              <mat-option *ngFor="let c of clientList()" [value]="c.id">{{ c.name }}</mat-option>
            </mat-select>
            <mat-error *ngIf="form.get('clientId')?.hasError('required')">{{ 'invoices.form.requiredClient' | translate }}</mat-error>
            <mat-error *ngIf="form.get('clientId')?.hasError('server')">{{ form.get('clientId')?.getError('server') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:0 1 180px;">
            <mat-label>{{ 'invoices.form.issueDate' | translate }}</mat-label>
            <input matInput type="date" formControlName="issueDate" />
            <mat-error *ngIf="form.get('issueDate')?.hasError('server')">{{ form.get('issueDate')?.getError('server') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:0 1 180px;">
            <mat-label>{{ 'invoices.form.dueDate' | translate }}</mat-label>
            <input matInput type="date" formControlName="dueDate" />
            <mat-error *ngIf="form.get('dueDate')?.hasError('server')">{{ form.get('dueDate')?.getError('server') }}</mat-error>
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>{{ 'invoices.form.paymentTerms' | translate }}</mat-label>
          <textarea matInput rows="2" formControlName="paymentTerms"></textarea>
        </mat-form-field>

        <h3 style="margin:16px 0 8px;">{{ 'invoices.linesHeading' | translate }}</h3>

        <div formArrayName="lines" style="display:flex; flex-direction:column; gap:12px;">
          <div
            *ngFor="let line of lines.controls; let i = index"
            [formGroupName]="i"
            class="line-card">

            <div class="line-top">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'invoices.form.product' | translate }}</mat-label>
                <mat-select formControlName="productId" (selectionChange)="onProductChange(i, $event.value)">
                  <mat-option [value]="''">{{ 'invoices.form.adhoc' | translate }}</mat-option>
                  <mat-option *ngFor="let p of productList()" [value]="p.id">{{ p.name }}</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'invoices.form.description' | translate }}</mat-label>
                <input matInput formControlName="description" />
                <mat-error *ngIf="line.get('description')?.hasError('required')">{{ 'invoices.form.required' | translate }}</mat-error>
                <mat-error *ngIf="line.get('description')?.hasError('maxlength')">{{ 'invoices.form.tooLong' | translate }}</mat-error>
                <mat-error *ngIf="line.get('description')?.hasError('server')">{{ line.get('description')?.getError('server') }}</mat-error>
              </mat-form-field>
            </div>

            <div class="line-bottom">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'invoices.form.qty' | translate }}</mat-label>
                <input matInput type="number" step="1" min="1" formControlName="quantity" />
                <mat-error *ngIf="line.get('quantity')?.hasError('required')">{{ 'invoices.form.required' | translate }}</mat-error>
                <mat-error *ngIf="line.get('quantity')?.hasError('min')">{{ 'invoices.form.errorGtZero' | translate }}</mat-error>
                <mat-error *ngIf="line.get('quantity')?.hasError('pattern')">{{ 'invoices.form.errorGtZero' | translate }}</mat-error>
                <mat-error *ngIf="line.get('quantity')?.hasError('server')">{{ line.get('quantity')?.getError('server') }}</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'invoices.form.unitPrice' | translate }}</mat-label>
                <input matInput type="number" step="0.01" min="0" formControlName="unitPrice" />
                <mat-error *ngIf="line.get('unitPrice')?.hasError('required')">{{ 'invoices.form.required' | translate }}</mat-error>
                <mat-error *ngIf="line.get('unitPrice')?.hasError('min')">{{ 'invoices.form.errorGteZero' | translate }}</mat-error>
                <mat-error *ngIf="line.get('unitPrice')?.hasError('server')">{{ line.get('unitPrice')?.getError('server') }}</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'invoices.form.vat' | translate }}</mat-label>
                <mat-select formControlName="vatRate">
                  <mat-option *ngFor="let r of vatRates" [value]="r">{{ r }}%</mat-option>
                </mat-select>
              </mat-form-field>

              <div class="line-total">
                {{ totalsByLine()[i]?.inclVat | number:'1.2-2' }} €
                <small>{{ 'invoices.totals.ht' | translate }} {{ totalsByLine()[i]?.exclVat | number:'1.2-2' }} €</small>
              </div>

              <div class="line-actions">
                <button mat-icon-button type="button" (click)="moveUp(i)" [disabled]="i === 0" [matTooltip]="'invoices.form.moveUp' | translate">
                  <mat-icon>arrow_upward</mat-icon>
                </button>
                <button mat-icon-button type="button" (click)="moveDown(i)" [disabled]="i === lines.length - 1" [matTooltip]="'invoices.form.moveDown' | translate">
                  <mat-icon>arrow_downward</mat-icon>
                </button>
                <button mat-icon-button type="button" color="warn" (click)="removeLine(i)" [disabled]="lines.length <= 1" [matTooltip]="'invoices.form.remove' | translate">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
            </div>
          </div>
        </div>

        <button mat-stroked-button type="button" (click)="addLine()" style="margin-top:12px;">
          <mat-icon>add</mat-icon> {{ 'invoices.form.addLine' | translate }}
        </button>

        <div style="display:flex; justify-content:flex-end; margin-top:24px;">
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:280px;">
            <dt style="color:#666;">{{ 'invoices.totals.subtotalHt' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ invoiceTotals().subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">{{ 'invoices.totals.vat' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ invoiceTotals().totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600; font-size:1.05rem;">{{ 'invoices.totals.totalTtc' | translate }}</dt>
            <dd style="margin:0; text-align:right; font-weight:600; font-size:1.05rem;">
              {{ invoiceTotals().totalInclVat | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>

        <p *ngIf="error()" style="color:#b71c1c; margin-top:12px;">{{ error() }}</p>

        <div style="display:flex; gap:8px; justify-content:flex-end; margin-top:16px;">
          <a mat-button routerLink="/invoices" [class.mat-button-disabled]="loading()">{{ 'common.cancel' | translate }}</a>
          <button mat-raised-button color="primary" type="submit" [disabled]="loading()">
            {{ (loading() ? 'invoices.form.saving' : (editingId() ? 'invoices.form.saveChanges' : 'invoices.form.create')) | translate }}
          </button>
        </div>
      </form>
    </div>
  `
})
export class InvoiceFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private invoices = inject(InvoicesService);
  private clients = inject(ClientsService);
  private products = inject(ProductsService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);
  private t = inject(TranslateService);

  loading = signal(false);
  error = signal<string | null>(null);
  loadingInvoice = signal(false);
  loadError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  invoiceNumber = signal<string | null>(null);
  totalsByLine = signal<{ exclVat: number; vat: number; inclVat: number }[]>([]);

  vatRates = VAT_RATES;
  clientList = this.clients.clients;
  productList = this.products.products;

  invoiceTotals = computed(() => {
    const t = this.totalsByLine();
    return {
      subtotalExclVat: round2(t.reduce((s, l) => s + l.exclVat, 0)),
      totalVat: round2(t.reduce((s, l) => s + l.vat, 0)),
      totalInclVat: round2(t.reduce((s, l) => s + l.inclVat, 0))
    };
  });

  form: FormGroup = this.fb.group({
    clientId: ['', Validators.required],
    issueDate: [todayIso(), Validators.required],
    dueDate: [plusDaysIso(30), Validators.required],
    paymentTerms: [this.t.instant('invoices.form.defaultPaymentTerms')],
    lines: this.fb.array([this.makeLine()])
  });

  get lines(): FormArray {
    return this.form.get('lines') as FormArray;
  }

  ngOnInit(): void {
    this.clients.loadAll();
    this.products.loadAll();
    this.recomputeTotals();
    this.form.valueChanges.subscribe(() => this.recomputeTotals());

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editingId.set(id);
      this.loadInvoiceForEdit(id);
    }
  }

  private loadInvoiceForEdit(id: string): void {
    this.loadingInvoice.set(true);
    this.loadError.set(null);
    this.invoices.get(id).subscribe({
      next: inv => {
        if (inv.status !== 'DRAFT') {
          this.loadingInvoice.set(false);
          this.snack.open(this.t.instant('invoices.form.onlyDraftEditable'), this.t.instant('common.dismiss'), { duration: 3000 });
          this.router.navigate(['/invoices', inv.id]);
          return;
        }
        this.prefill(inv);
        this.loadingInvoice.set(false);
      },
      error: err => {
        this.loadingInvoice.set(false);
        this.loadError.set(extractErrorDetail(err, this.t.instant('invoices.form.notFound')));
      }
    });
  }

  private prefill(inv: Invoice): void {
    this.invoiceNumber.set(inv.number);
    while (this.lines.length > 0) this.lines.removeAt(0);
    const sortedLines = [...inv.lines].sort((a, b) => a.sortOrder - b.sortOrder);
    for (const l of sortedLines) {
      this.lines.push(this.fb.group({
        productId: [l.productId ?? ''],
        description: [l.description, [Validators.required, Validators.maxLength(500)]],
        quantity: [Math.trunc(l.quantity), [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
        unitPrice: [l.unitPrice, [Validators.required, Validators.min(0)]],
        vatRate: [l.vatRate, Validators.required]
      }));
    }
    this.form.patchValue({
      clientId: inv.clientId,
      issueDate: inv.issueDate,
      dueDate: inv.dueDate,
      paymentTerms: inv.paymentTerms ?? ''
    });
    this.recomputeTotals();
  }

  addLine(): void {
    this.lines.push(this.makeLine());
  }

  removeLine(i: number): void {
    if (this.lines.length > 1) this.lines.removeAt(i);
  }

  moveUp(i: number): void {
    if (i === 0) return;
    const ctrl = this.lines.at(i);
    this.lines.removeAt(i);
    this.lines.insert(i - 1, ctrl);
  }

  moveDown(i: number): void {
    if (i === this.lines.length - 1) return;
    const ctrl = this.lines.at(i);
    this.lines.removeAt(i);
    this.lines.insert(i + 1, ctrl);
  }

  onProductChange(index: number, productId: string): void {
    if (!productId) return;
    const p = this.productList().find(x => x.id === productId);
    if (!p) return;
    this.lines.at(index).patchValue({
      description: p.description || p.name,
      unitPrice: p.unitPrice,
      vatRate: p.vatRate
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const v = this.form.getRawValue();
    const payload: CreateInvoiceRequest & UpdateInvoiceRequest = {
      clientId: v.clientId,
      issueDate: v.issueDate || undefined,
      dueDate: v.dueDate || undefined,
      paymentTerms: v.paymentTerms || undefined,
      lines: v.lines.map((l: any, i: number) => ({
        productId: l.productId || undefined,
        description: l.description,
        quantity: Number(l.quantity),
        unitPrice: Number(l.unitPrice),
        vatRate: Number(l.vatRate),
        sortOrder: i
      }))
    };

    const id = this.editingId();
    const request$ = id ? this.invoices.update(id, payload) : this.invoices.create(payload);

    request$.subscribe({
      next: inv => {
        this.loading.set(false);
        const key = id
          ? (inv.number ? 'invoices.form.updated' : 'invoices.form.draftUpdated')
          : 'invoices.form.draftCreated';
        this.snack.open(this.t.instant(key, { number: inv.number }), this.t.instant('common.dismiss'), { duration: 2500 });
        this.router.navigate(['/invoices', inv.id]);
      },
      error: err => {
        this.loading.set(false);
        const fieldErrors = extractFieldErrors(err);
        if (fieldErrors) {
          for (const [path, msg] of Object.entries(fieldErrors)) {
            const ctrl = this.findControl(path);
            if (ctrl) {
              ctrl.setErrors({ server: msg });
              ctrl.markAsTouched();
            }
          }
          this.error.set(this.t.instant('invoices.form.correctErrors'));
        } else {
          this.error.set(extractErrorDetail(err, this.t.instant('invoices.form.saveFailed')));
        }
      }
    });
  }

  private makeLine(): FormGroup {
    return this.fb.group({
      productId: [''],
      description: ['', [Validators.required, Validators.maxLength(500)]],
      quantity: [1, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
      unitPrice: [0, [Validators.required, Validators.min(0)]],
      vatRate: [21, Validators.required]
    });
  }

  private recomputeTotals(): void {
    const arr = this.lines.value as Array<{ quantity: number; unitPrice: number; vatRate: number }>;
    this.totalsByLine.set(arr.map(l => {
      const qty = Number(l.quantity) || 0;
      const up = Number(l.unitPrice) || 0;
      const vatPct = Number(l.vatRate) || 0;
      const exclVat = round2(qty * up);
      const vat = round2(exclVat * vatPct / 100);
      return { exclVat, vat, inclVat: round2(exclVat + vat) };
    }));
  }

  private findControl(path: string) {
    const lineMatch = path.match(/^lines\[(\d+)\]\.(.+)$/);
    if (lineMatch) {
      const idx = Number(lineMatch[1]);
      const field = lineMatch[2];
      return this.lines.at(idx)?.get(field) ?? null;
    }
    return this.form.get(path);
  }
}
