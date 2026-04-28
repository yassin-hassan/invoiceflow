import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule
  ],
  template: `
    <div style="display:flex; justify-content:center; align-items:center; height:100vh; background:#f5f5f5;">
      <mat-card style="width:440px; padding: 16px;">
        <mat-card-header>
          <mat-card-title style="font-size:1.5rem;">InvoiceFlow</mat-card-title>
          <mat-card-subtitle>Sign in to your account</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content style="margin-top: 16px;">

          <!-- Login form -->
          <form *ngIf="!showTwoFactor()" [formGroup]="loginForm" (ngSubmit)="onLogin()">
            <mat-form-field appearance="outline" style="width:100%;">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" type="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" style="width:100%; margin-top:8px;">
              <mat-label>Password</mat-label>
              <input matInput formControlName="password" type="password" />
            </mat-form-field>
            <p *ngIf="error()" style="color: red; font-size:0.85rem;">{{ error() }}</p>
            <p *ngIf="info()" style="color: #2e7d32; font-size:0.85rem;">{{ info() }}</p>
            <button
              *ngIf="needsVerification()"
              mat-stroked-button
              color="primary"
              type="button"
              style="width:100%; margin-top:8px;"
              [disabled]="resending()"
              (click)="onResendVerification()">
              {{ resending() ? 'Sending...' : 'Resend verification email' }}
            </button>
            <button mat-raised-button color="primary" style="width:100%; margin-top:8px;" type="submit" [disabled]="loading()">
              {{ loading() ? 'Signing in...' : 'Sign in' }}
            </button>
            <p style="text-align:center; margin-top:16px; font-size:0.875rem;">
              No account yet? <a routerLink="/register">Sign up</a>
            </p>
          </form>

          <!-- 2FA form -->
          <form *ngIf="showTwoFactor()" [formGroup]="twoFactorForm" (ngSubmit)="onVerify2FA()">
            <p style="color:#555;">Enter the 6-digit code sent to your phone.</p>
            <mat-form-field appearance="outline" style="width:100%;">
              <mat-label>Verification code</mat-label>
              <input matInput formControlName="code" maxlength="6" inputmode="numeric" pattern="[0-9]*" />
            </mat-form-field>
            <p *ngIf="error()" style="color: red; font-size:0.85rem;">{{ error() }}</p>
            <button mat-raised-button color="primary" style="width:100%; margin-top:8px;" type="submit" [disabled]="loading()">
              {{ loading() ? 'Verifying...' : 'Verify' }}
            </button>
          </form>

        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class LoginComponent {
  loginForm: FormGroup;
  twoFactorForm: FormGroup;
  showTwoFactor = signal(false);
  loading = signal(false);
  resending = signal(false);
  needsVerification = signal(false);
  error = signal('');
  info = signal('');

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
    this.twoFactorForm = this.fb.group({
      code: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  onLogin(): void {
    if (this.loginForm.invalid) return;
    this.loading.set(true);
    this.error.set('');
    this.info.set('');
    this.needsVerification.set(false);
    const { email, password } = this.loginForm.value;

    this.auth.login({ email, password }).subscribe({
      next: res => {
        this.loading.set(false);
        if (res.requires2fa) {
          this.showTwoFactor.set(true);
        } else if (res.token) {
          this.auth.saveToken(res.token);
          this.router.navigate(['/dashboard']);
        } else {
          this.error.set('Unexpected response from server. Please try again.');
        }
      },
      error: err => {
        this.loading.set(false);
        if (err.status === 403) {
          this.needsVerification.set(true);
          this.error.set(err.error?.detail || 'Email address not verified.');
        } else {
          this.error.set(err.error?.detail || 'Invalid credentials');
        }
      }
    });
  }

  onResendVerification(): void {
    const email = this.loginForm.value.email;
    if (!email) return;
    this.resending.set(true);
    this.error.set('');
    this.info.set('');
    this.auth.resendVerification(email).subscribe({
      next: () => {
        this.resending.set(false);
        this.needsVerification.set(false);
        this.info.set('Verification email sent. Check your inbox.');
      },
      error: err => {
        this.resending.set(false);
        this.error.set(err.error?.detail || 'Could not resend verification email.');
      }
    });
  }

  onVerify2FA(): void {
    if (this.twoFactorForm.invalid) return;
    this.loading.set(true);
    this.error.set('');
    const email = this.loginForm.value.email;
    const code = this.twoFactorForm.value.code;

    this.auth.verifyTwoFactor({ email, code }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err.error?.detail || 'Invalid code');
      }
    });
  }
}
