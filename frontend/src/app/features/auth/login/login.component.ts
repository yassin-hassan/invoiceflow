import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../core/services/auth.service';
import { LanguageToggleComponent } from '../../../shared/components/language-toggle/language-toggle.component';
import { extractErrorDetail } from '../../../core/utils/http-errors';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule,
    TranslateModule, LanguageToggleComponent
  ],
  template: `
    <div style="display:flex; justify-content:center; align-items:center; height:100vh; background:#f5f5f5; position:relative;">
      <div style="position:absolute; top:16px; right:16px;">
        <app-language-toggle></app-language-toggle>
      </div>
      <mat-card style="width:440px; padding: 16px;">
        <mat-card-header>
          <mat-card-title style="font-size:1.5rem;">InvoiceFlow</mat-card-title>
          <mat-card-subtitle>{{ 'auth.login.subtitle' | translate }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content style="margin-top: 16px;">

          <!-- Login form -->
          <form *ngIf="!showTwoFactor()" [formGroup]="loginForm" (ngSubmit)="onLogin()">
            <mat-form-field appearance="outline" style="width:100%;">
              <mat-label>{{ 'auth.fields.email' | translate }}</mat-label>
              <input matInput formControlName="email" type="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" style="width:100%; margin-top:8px;">
              <mat-label>{{ 'auth.fields.password' | translate }}</mat-label>
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
              {{ (resending() ? 'auth.login.resending' : 'auth.login.resend') | translate }}
            </button>
            <button mat-raised-button color="primary" style="width:100%; margin-top:8px;" type="submit" [disabled]="loading()">
              {{ (loading() ? 'auth.login.submitting' : 'auth.login.submit') | translate }}
            </button>
            <p style="text-align:center; margin-top:16px; font-size:0.875rem;">
              {{ 'auth.login.noAccount' | translate }} <a routerLink="/register">{{ 'auth.login.signup' | translate }}</a>
            </p>
          </form>

          <!-- 2FA form -->
          <form *ngIf="showTwoFactor()" [formGroup]="twoFactorForm" (ngSubmit)="onVerify2FA()">
            <p style="color:#555;">{{ 'auth.login.twofaPrompt' | translate }}</p>
            <mat-form-field appearance="outline" style="width:100%;">
              <mat-label>{{ 'auth.fields.code' | translate }}</mat-label>
              <input matInput formControlName="code" maxlength="6" inputmode="numeric" pattern="[0-9]*" />
            </mat-form-field>
            <p *ngIf="error()" style="color: red; font-size:0.85rem;">{{ error() }}</p>
            <button mat-raised-button color="primary" style="width:100%; margin-top:8px;" type="submit" [disabled]="loading()">
              {{ (loading() ? 'auth.login.verifying' : 'auth.login.verify') | translate }}
            </button>
          </form>

        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class LoginComponent {
  private t = inject(TranslateService);

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
          this.error.set(this.t.instant('auth.errors.unexpected'));
        }
      },
      error: err => {
        this.loading.set(false);
        if (err.status === 403) {
          this.needsVerification.set(true);
          this.error.set(extractErrorDetail(err, this.t.instant('auth.errors.unverified')));
        } else {
          this.error.set(extractErrorDetail(err, this.t.instant('auth.errors.invalidCredentials')));
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
        this.info.set(this.t.instant('auth.login.resent'));
      },
      error: err => {
        this.resending.set(false);
        this.error.set(extractErrorDetail(err, this.t.instant('auth.errors.resendFailed')));
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
        this.error.set(extractErrorDetail(err, this.t.instant('auth.errors.invalidCode')));
      }
    });
  }
}
