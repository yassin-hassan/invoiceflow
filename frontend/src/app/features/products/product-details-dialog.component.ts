import { Component, Inject } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Product } from './product.model';

@Component({
  selector: 'app-product-details-dialog',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ product.name }}</h2>
    <mat-dialog-content>
      <dl style="display:grid; grid-template-columns:140px 1fr; gap:8px 16px; margin:0; min-width:480px;">
        <dt style="color:#666;">Reference</dt>
        <dd style="margin:0;">{{ product.reference }}</dd>

        <dt style="color:#666;">Unit price</dt>
        <dd style="margin:0;">{{ product.unitPrice | number:'1.2-2' }} €</dd>

        <dt style="color:#666;">VAT rate</dt>
        <dd style="margin:0;">{{ product.vatRate }}%</dd>

        <dt style="color:#666;">Unit</dt>
        <dd style="margin:0;">{{ product.unit }}</dd>

        <dt style="color:#666;">Created</dt>
        <dd style="margin:0;">{{ product.createdAt | date:'medium' }}</dd>

        <dt style="color:#666; align-self:start;">Description</dt>
        <dd style="margin:0; white-space:pre-wrap;">{{ product.description || '—' }}</dd>
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Close</button>
      <button mat-raised-button color="primary" (click)="ref.close('edit')">Edit</button>
    </mat-dialog-actions>
  `
})
export class ProductDetailsDialogComponent {
  constructor(
    public ref: MatDialogRef<ProductDetailsDialogComponent, 'edit' | undefined>,
    @Inject(MAT_DIALOG_DATA) public product: Product
  ) {}
}
