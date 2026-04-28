import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService, RegisterRequest } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule
  ],
  template: `
    <div style="display:flex; justify-content:center; align-items:center; min-height:100vh; background:#f5f5f5;">
      <mat-card style="width:440px; padding:16px;">
        <mat-card-header>
          <mat-card-title style="font-size:1.5rem;">InvoiceFlow</mat-card-title>
          <mat-card-subtitle>{{ success() ? 'Check your email' : 'Create your account' }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content style="margin-top:16px;">

          <ng-container *ngIf="!success()">
            <form [formGroup]="form" (ngSubmit)="onSubmit()">
              <div style="display:flex; gap:8px;">
                <mat-form-field appearance="outline" style="flex:1;">
                  <mat-label>First name</mat-label>
                  <input matInput formControlName="firstName" />
                  <mat-error *ngIf="serverErrors()['firstName']">{{ serverErrors()['firstName'] }}</mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline" style="flex:1;">
                  <mat-label>Last name</mat-label>
                  <input matInput formControlName="lastName" />
                  <mat-error *ngIf="serverErrors()['lastName']">{{ serverErrors()['lastName'] }}</mat-error>
                </mat-form-field>
              </div>
              <mat-form-field appearance="outline" style="width:100%;">
                <mat-label>Email</mat-label>
                <input matInput formControlName="email" type="email" />
                <mat-error *ngIf="form.get('email')?.hasError('email')">Invalid email address</mat-error>
                <mat-error *ngIf="serverErrors()['email']">{{ serverErrors()['email'] }}</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline" style="width:100%; margin-top:8px;">
                <mat-label>Password</mat-label>
                <input matInput formControlName="password" type="password" />
                <mat-error *ngIf="form.get('password')?.hasError('pattern')">
                  Min 8 characters, one uppercase, one lowercase, one digit
                </mat-error>
                <mat-error *ngIf="serverErrors()['password']">{{ serverErrors()['password'] }}</mat-error>
              </mat-form-field>
              <p *ngIf="error()" style="color:red; font-size:0.85rem;">{{ error() }}</p>
              <button mat-raised-button color="primary" style="width:100%; margin-top:8px;" type="submit" [disabled]="loading()">
                {{ loading() ? 'Creating account...' : 'Create account' }}
              </button>
              <p style="text-align:center; margin-top:16px; font-size:0.875rem;">
                Already have an account? <a routerLink="/login">Sign in</a>
              </p>
            </form>
          </ng-container>

          <ng-container *ngIf="success()">
            <p style="font-size:3rem; text-align:center; margin:0;">📧</p>
            <p style="text-align:center; color:#555;">
              We sent a verification link to <strong>{{ submittedEmail() }}</strong>.
              Click the link in the email to activate your account, then sign in.
            </p>
            <button mat-raised-button color="primary" style="width:100%; margin-top:16px;" routerLink="/login">
              Go to sign in
            </button>
          </ng-container>

        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class RegisterComponent {
  form: FormGroup;
  loading = signal(false);
  error = signal('');
  success = signal(false);
  submittedEmail = signal('');
  serverErrors = signal<Record<string, string>>({});

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/)]]
    });

    this.form.valueChanges.subscribe(() => {
      if (Object.keys(this.serverErrors()).length) this.serverErrors.set({});
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');
    this.serverErrors.set({});
    const payload = this.form.value as RegisterRequest;
    this.auth.register(payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.submittedEmail.set(payload.email);
        this.success.set(true);
      },
      error: err => {
        this.loading.set(false);
        if (err.status === 400 && err.error?.errors) {
          this.serverErrors.set(err.error.errors);
          this.error.set('');
        } else {
          this.error.set(err.error?.detail || 'Registration failed. Please try again.');
        }
      }
    });
  }
}
