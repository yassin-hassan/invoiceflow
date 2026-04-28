import { Component, Inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ClientsService } from './clients.service';
import { Client, CreateClientRequest, UpdateClientRequest } from './client.model';
import { extractErrorDetail, extractFieldErrors } from '../../core/utils/http-errors';

export interface ClientFormDialogData {
  client?: Client;
}

@Component({
  selector: 'app-client-form-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.client ? 'Edit client' : 'New client' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="onSubmit()" style="display:flex; flex-direction:column; gap:8px; min-width:480px;">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          <mat-error *ngIf="form.get('name')?.hasError('required')">Name is required</mat-error>
          <mat-error *ngIf="serverErrors()['name']">{{ serverErrors()['name'] }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" type="email" />
          <mat-error *ngIf="form.get('email')?.hasError('required')">Email is required</mat-error>
          <mat-error *ngIf="form.get('email')?.hasError('email')">Invalid email address</mat-error>
          <mat-error *ngIf="serverErrors()['email']">{{ serverErrors()['email'] }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
          <mat-error *ngIf="serverErrors()['phone']">{{ serverErrors()['phone'] }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>VAT number</mat-label>
          <input matInput formControlName="vatNumber" />
          <mat-error *ngIf="serverErrors()['vatNumber']">{{ serverErrors()['vatNumber'] }}</mat-error>
        </mat-form-field>

        <div formGroupName="billingAddress" style="display:flex; flex-direction:column; gap:8px;">
          <h3 style="margin:8px 0 0; font-size:0.95rem; color:#555;">Billing address (optional)</h3>
          <mat-form-field appearance="outline">
            <mat-label>Street</mat-label>
            <input matInput formControlName="street" />
          </mat-form-field>
          <div style="display:flex; gap:8px;">
            <mat-form-field appearance="outline" style="flex:1;">
              <mat-label>Postal code</mat-label>
              <input matInput formControlName="postalCode" />
            </mat-form-field>
            <mat-form-field appearance="outline" style="flex:2;">
              <mat-label>City</mat-label>
              <input matInput formControlName="city" />
            </mat-form-field>
          </div>
          <mat-form-field appearance="outline">
            <mat-label>Country</mat-label>
            <input matInput formControlName="country" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline">
          <mat-label>Notes</mat-label>
          <textarea matInput rows="3" formControlName="notes"></textarea>
        </mat-form-field>

        <p *ngIf="error()" style="color:red; font-size:0.85rem; margin:0;">{{ error() }}</p>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close()" [disabled]="loading()">Cancel</button>
      <button mat-raised-button color="primary" type="button" (click)="onSubmit()" [disabled]="loading()">
        {{ loading() ? 'Saving...' : (data.client ? 'Save' : 'Create') }}
      </button>
    </mat-dialog-actions>
  `
})
export class ClientFormDialogComponent {
  form: FormGroup;
  loading = signal(false);
  error = signal('');
  serverErrors = signal<Record<string, string>>({});

  constructor(
    private fb: FormBuilder,
    private clients: ClientsService,
    public ref: MatDialogRef<ClientFormDialogComponent, Client>,
    @Inject(MAT_DIALOG_DATA) public data: ClientFormDialogData
  ) {
    const c = data.client;
    this.form = this.fb.group({
      name: [c?.name ?? '', Validators.required],
      email: [c?.email ?? '', [Validators.required, Validators.email]],
      phone: [c?.phone ?? ''],
      vatNumber: [c?.vatNumber ?? ''],
      notes: [c?.notes ?? ''],
      billingAddress: this.fb.group({
        street: [c?.billingAddress?.street ?? ''],
        postalCode: [c?.billingAddress?.postalCode ?? ''],
        city: [c?.billingAddress?.city ?? ''],
        country: [c?.billingAddress?.country ?? '']
      })
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
    const request$ = this.data.client
      ? this.clients.update(this.data.client.id, payload)
      : this.clients.create(payload);

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
          this.error.set(extractErrorDetail(err, 'Could not save client.'));
        }
      }
    });
  }

  private toPayload(): CreateClientRequest & UpdateClientRequest {
    const v = this.form.value;
    const addr = v.billingAddress;
    const hasAddress = addr.street || addr.postalCode || addr.city || addr.country;
    return {
      name: v.name,
      email: v.email,
      phone: v.phone || undefined,
      vatNumber: v.vatNumber || undefined,
      notes: v.notes || undefined,
      billingAddress: hasAddress ? addr : undefined
    };
  }
}
