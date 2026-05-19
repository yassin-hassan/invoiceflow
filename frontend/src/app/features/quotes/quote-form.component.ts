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
import { QuotesService } from './quotes.service';
import { CreateQuoteRequest, Quote, UpdateQuoteRequest } from './quote.model';
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
  selector: 'app-quote-form',
  standalone: true,
  imports: [
    CommonModule, DecimalPipe, ReactiveFormsModule, RouterLink,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatSnackBarModule, MatProgressSpinnerModule, MatTooltipModule,
    TranslateModule
  ],
  template: `
    <div style="padding:24px; max-width:1100px;">
      <a routerLink="/quotes" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        {{ 'quotes.backToList' | translate }}
      </a>

      <h1 style="margin:0 0 16px;">
        {{ editingId()
            ? (('quotes.form.editTitlePrefix' | translate) + (quoteNumber() || ('quotes.form.fallbackTitle' | translate)))
            : ('quotes.form.newTitle' | translate) }}
      </h1>

      <ng-container *ngIf="loadingQuote()">
        <div style="display:flex; justify-content:center; padding:48px;">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      </ng-container>

      <ng-container *ngIf="loadError()">
        <div style="padding:16px; background:#fdecea; color:#b71c1c; border-radius:4px;">
          {{ loadError() }}
        </div>
      </ng-container>

      <form *ngIf="!loadingQuote() && !loadError()" [formGroup]="form" (ngSubmit)="onSubmit()">
        <div style="display:flex; gap:16px; flex-wrap:wrap;">
          <mat-form-field appearance="outline" style="flex:1 1 280px;">
            <mat-label>{{ 'quotes.form.client' | translate }}</mat-label>
            <mat-select formControlName="clientId">
              <mat-option *ngFor="let c of clientList()" [value]="c.id">{{ c.name }}</mat-option>
            </mat-select>
            <mat-error *ngIf="form.get('clientId')?.hasError('required')">{{ 'quotes.form.requiredClient' | translate }}</mat-error>
            <mat-error *ngIf="form.get('clientId')?.hasError('server')">{{ form.get('clientId')?.getError('server') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:0 1 180px;">
            <mat-label>{{ 'quotes.form.issueDate' | translate }}</mat-label>
            <input matInput type="date" formControlName="issueDate" />
            <mat-error *ngIf="form.get('issueDate')?.hasError('server')">{{ form.get('issueDate')?.getError('server') }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:0 1 180px;">
            <mat-label>{{ 'quotes.form.expiryDate' | translate }}</mat-label>
            <input matInput type="date" formControlName="expiryDate" />
            <mat-error *ngIf="form.get('expiryDate')?.hasError('server')">{{ form.get('expiryDate')?.getError('server') }}</mat-error>
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>{{ 'quotes.form.notes' | translate }}</mat-label>
          <textarea matInput rows="2" formControlName="notes"></textarea>
        </mat-form-field>

        <h3 style="margin:16px 0 8px;">{{ 'quotes.linesHeading' | translate }}</h3>

        <div formArrayName="lines" style="display:flex; flex-direction:column; gap:12px;">
          <div
            *ngFor="let line of lines.controls; let i = index"
            [formGroupName]="i"
            style="display:grid; grid-template-columns: 200px 1fr 90px 110px 100px 110px auto; gap:8px; align-items:start; padding:12px; background:#fafafa; border-radius:4px;">

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'quotes.form.product' | translate }}</mat-label>
              <mat-select formControlName="productId" (selectionChange)="onProductChange(i, $event.value)">
                <mat-option [value]="''">{{ 'quotes.form.adhoc' | translate }}</mat-option>
                <mat-option *ngFor="let p of productList()" [value]="p.id">{{ p.name }}</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'quotes.form.description' | translate }}</mat-label>
              <input matInput formControlName="description" />
              <mat-error *ngIf="line.get('description')?.hasError('required')">{{ 'quotes.form.required' | translate }}</mat-error>
              <mat-error *ngIf="line.get('description')?.hasError('maxlength')">{{ 'quotes.form.tooLong' | translate }}</mat-error>
              <mat-error *ngIf="line.get('description')?.hasError('server')">{{ line.get('description')?.getError('server') }}</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'quotes.form.qty' | translate }}</mat-label>
              <input matInput type="number" step="0.01" min="0.01" formControlName="quantity" />
              <mat-error *ngIf="line.get('quantity')?.hasError('required')">{{ 'quotes.form.required' | translate }}</mat-error>
              <mat-error *ngIf="line.get('quantity')?.hasError('min')">&gt; 0</mat-error>
              <mat-error *ngIf="line.get('quantity')?.hasError('server')">{{ line.get('quantity')?.getError('server') }}</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'quotes.form.unitPrice' | translate }}</mat-label>
              <input matInput type="number" step="0.01" min="0" formControlName="unitPrice" />
              <mat-error *ngIf="line.get('unitPrice')?.hasError('required')">{{ 'quotes.form.required' | translate }}</mat-error>
              <mat-error *ngIf="line.get('unitPrice')?.hasError('min')">≥ 0</mat-error>
              <mat-error *ngIf="line.get('unitPrice')?.hasError('server')">{{ line.get('unitPrice')?.getError('server') }}</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'quotes.form.vat' | translate }}</mat-label>
              <mat-select formControlName="vatRate">
                <mat-option *ngFor="let r of vatRates" [value]="r">{{ r }}%</mat-option>
              </mat-select>
            </mat-form-field>

            <div style="text-align:right; padding-top:14px; font-weight:500;">
              {{ totalsByLine()[i]?.inclVat | number:'1.2-2' }} €
              <div style="font-size:0.75rem; color:#666; font-weight:400;">
                {{ 'quotes.totals.ht' | translate }} {{ totalsByLine()[i]?.exclVat | number:'1.2-2' }} €
              </div>
            </div>

            <div style="display:flex; flex-direction:column; gap:4px;">
              <button mat-icon-button type="button" (click)="moveUp(i)" [disabled]="i === 0" [matTooltip]="'quotes.form.moveUp' | translate">
                <mat-icon>arrow_upward</mat-icon>
              </button>
              <button mat-icon-button type="button" (click)="moveDown(i)" [disabled]="i === lines.length - 1" [matTooltip]="'quotes.form.moveDown' | translate">
                <mat-icon>arrow_downward</mat-icon>
              </button>
              <button mat-icon-button type="button" color="warn" (click)="removeLine(i)" [disabled]="lines.length <= 1" [matTooltip]="'quotes.form.remove' | translate">
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          </div>
        </div>

        <button mat-stroked-button type="button" (click)="addLine()" style="margin-top:12px;">
          <mat-icon>add</mat-icon> {{ 'quotes.form.addLine' | translate }}
        </button>

        <div style="display:flex; justify-content:flex-end; margin-top:24px;">
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:280px;">
            <dt style="color:#666;">{{ 'quotes.totals.subtotalHt' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ quoteTotals().subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">{{ 'quotes.totals.vat' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ quoteTotals().totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600; font-size:1.05rem;">{{ 'quotes.totals.totalTtc' | translate }}</dt>
            <dd style="margin:0; text-align:right; font-weight:600; font-size:1.05rem;">
              {{ quoteTotals().totalInclVat | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>

        <p *ngIf="error()" style="color:#b71c1c; margin-top:12px;">{{ error() }}</p>

        <div style="display:flex; gap:8px; justify-content:flex-end; margin-top:16px;">
          <a mat-button routerLink="/quotes" [class.mat-button-disabled]="loading()">{{ 'common.cancel' | translate }}</a>
          <button mat-raised-button color="primary" type="submit" [disabled]="loading()">
            {{ (loading() ? 'quotes.form.saving' : (editingId() ? 'quotes.form.saveChanges' : 'quotes.form.create')) | translate }}
          </button>
        </div>
      </form>
    </div>
  `
})
export class QuoteFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private quotes = inject(QuotesService);
  private clients = inject(ClientsService);
  private products = inject(ProductsService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);
  private t = inject(TranslateService);

  loading = signal(false);
  error = signal<string | null>(null);
  loadingQuote = signal(false);
  loadError = signal<string | null>(null);
  editingId = signal<string | null>(null);
  quoteNumber = signal<string | null>(null);
  totalsByLine = signal<{ exclVat: number; vat: number; inclVat: number }[]>([]);

  vatRates = VAT_RATES;
  clientList = this.clients.clients;
  productList = this.products.products;

  quoteTotals = computed(() => {
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
    expiryDate: [plusDaysIso(30), Validators.required],
    notes: [''],
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
      this.loadQuoteForEdit(id);
    }
  }

  private loadQuoteForEdit(id: string): void {
    this.loadingQuote.set(true);
    this.loadError.set(null);
    this.quotes.get(id).subscribe({
      next: q => {
        if (q.status !== 'DRAFT') {
          this.loadingQuote.set(false);
          this.snack.open(this.t.instant('quotes.form.onlyDraftEditable'), this.t.instant('common.dismiss'), { duration: 3000 });
          this.router.navigate(['/quotes', q.id]);
          return;
        }
        this.prefill(q);
        this.loadingQuote.set(false);
      },
      error: err => {
        this.loadingQuote.set(false);
        this.loadError.set(extractErrorDetail(err, this.t.instant('quotes.form.notFound')));
      }
    });
  }

  private prefill(q: Quote): void {
    this.quoteNumber.set(q.number);
    while (this.lines.length > 0) this.lines.removeAt(0);
    const sortedLines = [...q.lines].sort((a, b) => a.sortOrder - b.sortOrder);
    for (const l of sortedLines) {
      this.lines.push(this.fb.group({
        productId: [l.productId ?? ''],
        description: [l.description, [Validators.required, Validators.maxLength(500)]],
        quantity: [l.quantity, [Validators.required, Validators.min(0.01)]],
        unitPrice: [l.unitPrice, [Validators.required, Validators.min(0)]],
        vatRate: [l.vatRate, Validators.required]
      }));
    }
    this.form.patchValue({
      clientId: q.clientId,
      issueDate: q.issueDate,
      expiryDate: q.expiryDate,
      notes: q.notes ?? ''
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
    const payload: CreateQuoteRequest & UpdateQuoteRequest = {
      clientId: v.clientId,
      issueDate: v.issueDate || undefined,
      expiryDate: v.expiryDate || undefined,
      notes: v.notes || undefined,
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
    const request$ = id ? this.quotes.update(id, payload) : this.quotes.create(payload);

    request$.subscribe({
      next: q => {
        this.loading.set(false);
        const key = id ? 'quotes.form.updated' : 'quotes.form.created';
        this.snack.open(this.t.instant(key, { number: q.number }), this.t.instant('common.dismiss'), { duration: 2500 });
        this.router.navigate(['/quotes', q.id]);
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
          this.error.set(this.t.instant('quotes.form.correctErrors'));
        } else {
          this.error.set(extractErrorDetail(err, this.t.instant('quotes.form.saveFailed')));
        }
      }
    });
  }

  private makeLine(): FormGroup {
    return this.fb.group({
      productId: [''],
      description: ['', [Validators.required, Validators.maxLength(500)]],
      quantity: [1, [Validators.required, Validators.min(0.01)]],
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
