import { Component, Inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { ProductsService } from './products.service';
import { Product, CreateProductRequest, UpdateProductRequest } from './product.model';
import { extractErrorDetail, extractFieldErrors } from '../../core/utils/http-errors';

export interface ProductFormDialogData {
  product?: Product;
}

const VAT_RATES = [0, 6, 12, 21];
const UNITS = ['hour', 'day', 'piece', 'forfait'];

@Component({
  selector: 'app-product-form-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.product ? 'Edit product' : 'New product' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="onSubmit()" style="display:flex; flex-direction:column; gap:8px; min-width:480px;">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          <mat-error *ngIf="form.get('name')?.hasError('required')">Name is required</mat-error>
          <mat-error *ngIf="serverErrors()['name']">{{ serverErrors()['name'] }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Reference</mat-label>
          <input matInput formControlName="reference" />
          <mat-error *ngIf="form.get('reference')?.hasError('required')">Reference is required</mat-error>
          <mat-error *ngIf="serverErrors()['reference']">{{ serverErrors()['reference'] }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput rows="2" formControlName="description"></textarea>
        </mat-form-field>

        <div style="display:flex; gap:8px;">
          <mat-form-field appearance="outline" style="flex:1;">
            <mat-label>Unit price (€)</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="unitPrice" />
            <mat-error *ngIf="form.get('unitPrice')?.hasError('required')">Unit price is required</mat-error>
            <mat-error *ngIf="form.get('unitPrice')?.hasError('min')">Must be greater than 0</mat-error>
            <mat-error *ngIf="serverErrors()['unitPrice']">{{ serverErrors()['unitPrice'] }}</mat-error>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:1;">
            <mat-label>VAT rate</mat-label>
            <mat-select formControlName="vatRate">
              <mat-option *ngFor="let r of vatRates" [value]="r">{{ r }}%</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" style="flex:1;">
            <mat-label>Unit</mat-label>
            <mat-select formControlName="unit">
              <mat-option *ngFor="let u of units" [value]="u">{{ u }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <p *ngIf="error()" style="color:red; font-size:0.85rem; margin:0;">{{ error() }}</p>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close()" [disabled]="loading()">Cancel</button>
      <button mat-raised-button color="primary" type="button" (click)="onSubmit()" [disabled]="loading()">
        {{ loading() ? 'Saving...' : (data.product ? 'Save' : 'Create') }}
      </button>
    </mat-dialog-actions>
  `
})
export class ProductFormDialogComponent {
  form: FormGroup;
  loading = signal(false);
  error = signal('');
  serverErrors = signal<Record<string, string>>({});

  vatRates = VAT_RATES;
  units = UNITS;

  constructor(
    private fb: FormBuilder,
    private products: ProductsService,
    public ref: MatDialogRef<ProductFormDialogComponent, Product>,
    @Inject(MAT_DIALOG_DATA) public data: ProductFormDialogData
  ) {
    const p = data.product;
    this.form = this.fb.group({
      name: [p?.name ?? '', Validators.required],
      reference: [p?.reference ?? '', Validators.required],
      description: [p?.description ?? ''],
      unitPrice: [p?.unitPrice ?? null, [Validators.required, Validators.min(0.01)]],
      vatRate: [p?.vatRate ?? 21, Validators.required],
      unit: [p?.unit ?? 'hour', Validators.required]
    });

    this.form.valueChanges.subscribe(() => {
      if (Object.keys(this.serverErrors()).length) this.serverErrors.set({});
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.serverErrors.set({});

    const payload = this.toPayload();
    const request$ = this.data.product
      ? this.products.update(this.data.product.id, payload)
      : this.products.create(payload);

    request$.subscribe({
      next: saved => {
        this.loading.set(false);
        this.ref.close(saved);
      },
      error: err => {
        this.loading.set(false);
        const fieldErrors = extractFieldErrors(err);
        if (fieldErrors) {
          this.serverErrors.set(fieldErrors);
          for (const [name, msg] of Object.entries(fieldErrors)) {
            this.form.get(name)?.setErrors({ server: msg });
            this.form.get(name)?.markAsTouched();
          }
        } else {
          this.error.set(extractErrorDetail(err, 'Could not save product.'));
        }
      }
    });
  }

  private toPayload(): CreateProductRequest & UpdateProductRequest {
    const v = this.form.value;
    return {
      name: v.name,
      reference: v.reference,
      description: v.description || undefined,
      unitPrice: Number(v.unitPrice),
      vatRate: Number(v.vatRate),
      unit: v.unit
    };
  }
}
