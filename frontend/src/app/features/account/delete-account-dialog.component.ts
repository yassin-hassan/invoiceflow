import { Component, Inject, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';

import { AccountDeletionService } from '../../core/services/account-deletion.service';
import { extractErrorDetail } from '../../core/utils/http-errors';

export interface DeleteAccountDialogData {
  userEmail: string;
}

@Component({
  selector: 'app-delete-account-dialog',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatCheckboxModule, MatSnackBarModule, MatProgressSpinnerModule,
    TranslateModule
  ],
  template: `
    <h2 mat-dialog-title>{{ 'account.danger.dialog.title' | translate }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content style="min-width:420px;">
        <p style="color:#555; margin-top:0;">{{ 'account.danger.dialog.body' | translate }}</p>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>{{ 'account.danger.dialog.emailLabel' | translate }}</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="off" />
          <mat-error *ngIf="form.get('email')?.touched && form.get('email')?.invalid">
            {{ 'account.danger.dialog.emailMismatch' | translate }}
          </mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" style="width:100%;">
          <mat-label>{{ 'account.danger.dialog.passwordLabel' | translate }}</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password" />
          <mat-error *ngIf="form.get('password')?.touched && form.get('password')?.invalid">
            {{ 'account.danger.dialog.passwordRequired' | translate }}
          </mat-error>
        </mat-form-field>

        <mat-checkbox formControlName="acknowledge" style="display:block; margin-top:8px;">
          {{ 'account.danger.dialog.acknowledge' | translate }}
        </mat-checkbox>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button type="button" mat-button (click)="ref.close()" [disabled]="submitting()">
          {{ 'common.cancel' | translate }}
        </button>
        <button type="submit" mat-raised-button color="warn"
                [disabled]="submitting() || !canSubmit()">
          <mat-spinner *ngIf="submitting()" diameter="16" style="display:inline-block; margin-right:8px;"></mat-spinner>
          {{ (submitting() ? 'account.danger.dialog.deleting' : 'account.danger.dialog.confirm') | translate }}
        </button>
      </mat-dialog-actions>
    </form>
  `
})
export class DeleteAccountDialogComponent {
  private fb = inject(FormBuilder);
  private service = inject(AccountDeletionService);
  private snackBar = inject(MatSnackBar);
  private t = inject(TranslateService);

  submitting = signal(false);

  form = this.fb.group({
    email: ['', [Validators.required, this.emailMatchValidator()]],
    password: ['', Validators.required],
    acknowledge: [false, Validators.requiredTrue]
  });

  constructor(
    public ref: MatDialogRef<DeleteAccountDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: DeleteAccountDialogData
  ) {}

  private emailMatchValidator() {
    return (control: { value: string | null }) => {
      const v = (control.value || '').trim().toLowerCase();
      return v === this.data?.userEmail?.toLowerCase() ? null : { mismatch: true };
    };
  }

  canSubmit(): boolean {
    return this.form.valid;
  }

  submit(): void {
    if (this.submitting() || !this.form.valid) return;
    const password = this.form.value.password ?? '';

    this.submitting.set(true);
    this.service.deleteAccount(password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.ref.close(true);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const fallback = err.status === 401
          ? this.t.instant('account.danger.wrongPassword')
          : this.t.instant('account.danger.failed');
        this.snackBar.open(
          extractErrorDetail(err, fallback),
          this.t.instant('common.ok'),
          { duration: 4000 }
        );
      }
    });
  }
}
