import { Component, Inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';
import { Client } from './client.model';

@Component({
  selector: 'app-client-details-dialog',
  standalone: true,
  imports: [CommonModule, DatePipe, MatDialogModule, MatButtonModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ client.name }}</h2>
    <mat-dialog-content>
      <dl style="display:grid; grid-template-columns:140px 1fr; gap:8px 16px; margin:0; min-width:480px;">
        <dt style="color:#666;">{{ 'clients.details.email' | translate }}</dt>
        <dd style="margin:0;">{{ client.email }}</dd>

        <dt style="color:#666;">{{ 'clients.details.phone' | translate }}</dt>
        <dd style="margin:0;">{{ client.phone || '—' }}</dd>

        <dt style="color:#666;">{{ 'clients.details.vatNumber' | translate }}</dt>
        <dd style="margin:0;">{{ client.vatNumber || '—' }}</dd>

        <dt style="color:#666;">{{ 'clients.details.created' | translate }}</dt>
        <dd style="margin:0;">{{ client.createdAt | date:'medium' }}</dd>

        <dt style="color:#666; align-self:start;">{{ 'clients.details.billingAddress' | translate }}</dt>
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

        <dt style="color:#666; align-self:start;">{{ 'clients.details.notes' | translate }}</dt>
        <dd style="margin:0; white-space:pre-wrap;">{{ client.notes || '—' }}</dd>
      </dl>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close()">{{ 'common.close' | translate }}</button>
      <button mat-raised-button color="primary" (click)="ref.close('edit')">{{ 'common.edit' | translate }}</button>
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
