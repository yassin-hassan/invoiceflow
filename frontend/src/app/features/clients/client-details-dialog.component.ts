import { Component, Inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Client } from './client.model';

@Component({
  selector: 'app-client-details-dialog',
  standalone: true,
  imports: [CommonModule, DatePipe, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ client.name }}</h2>
    <mat-dialog-content>
      <dl style="display:grid; grid-template-columns:140px 1fr; gap:8px 16px; margin:0; min-width:480px;">
        <dt style="color:#666;">Email</dt>
        <dd style="margin:0;">{{ client.email }}</dd>

        <dt style="color:#666;">Phone</dt>
        <dd style="margin:0;">{{ client.phone || '—' }}</dd>

        <dt style="color:#666;">VAT number</dt>
        <dd style="margin:0;">{{ client.vatNumber || '—' }}</dd>

        <dt style="color:#666;">Created</dt>
        <dd style="margin:0;">{{ client.createdAt | date:'medium' }}</dd>

        <dt style="color:#666; align-self:start;">Billing address</dt>
        <dd style="margin:0;">
          <ng-container *ngIf="hasAddress(); else noAddress">
            <div *ngIf="client.billingAddress?.street">{{ client.billingAddress?.street }}</div>
            <div>
              {{ client.billingAddress?.postalCode }} {{ client.billingAddress?.city }}
            </div>
            <div *ngIf="client.billingAddress?.country">{{ client.billingAddress?.country }}</div>
          </ng-container>
          <ng-template #noAddress>—</ng-template>
        </dd>

        <dt style="color:#666; align-self:start;">Notes</dt>
        <dd style="margin:0; white-space:pre-wrap;">{{ client.notes || '—' }}</dd>
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">Close</button>
      <button mat-raised-button color="primary" (click)="ref.close('edit')">Edit</button>
    </mat-dialog-actions>
  `
})
export class ClientDetailsDialogComponent {
  constructor(
    public ref: MatDialogRef<ClientDetailsDialogComponent, 'edit' | undefined>,
    @Inject(MAT_DIALOG_DATA) public client: Client
  ) {}

  hasAddress(): boolean {
    const a = this.client.billingAddress;
    return !!(a && (a.street || a.postalCode || a.city || a.country));
  }
}
