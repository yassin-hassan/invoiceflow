import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../../core/services/auth.service';

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
          <mat-card-subtitle>Create your account</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content style="margin-top:16px;">
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <div style="display:flex; gap:8px;">
              <mat-form-field appearance="outline" style="flex:1;">
                <mat-label>First name</mat-label>
                <input matInput formControlName="firstName" />
              </mat-form-field>
              <mat-form-field appearance="outline" style="flex:1;">
                <mat-label>Last name</mat-label>
                <input matInput formControlName="lastName" />
              </mat-form-field>
            </div>
            <mat-form-field appearance="outline" style="width:100%;">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" type="email" />
              <mat-error *ngIf="form.get('email')?.hasError('email')">Invalid email address</mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline" style="width:100%; margin-top:8px;">
              <mat-label>Password</mat-label>
              <input matInput formControlName="password" type="password" />
              <mat-error *ngIf="form.get('password')?.hasError('pattern')">
                Min 8 characters, one uppercase, one lowercase, one digit
              </mat-error>
            </mat-form-field>
            <p *ngIf="error" style="color:red; font-size:0.85rem;">{{ error }}</p>
            <p *ngIf="success" style="color:green; font-size:0.85rem;">
              Account created! Check your email to verify your account.
            </p>
            <button mat-raised-button color="primary" style="width:100%; margin-top:8px;" type="submit" [disabled]="loading || success">
              {{ loading ? 'Creating account...' : 'Create account' }}
            </button>
            <p style="text-align:center; margin-top:16px; font-size:0.875rem;">
              Already have an account? <a routerLink="/login">Sign in</a>
            </p>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class RegisterComponent {
  form: FormGroup;
  loading = false;
  error = '';
  success = false;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/)]]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    this.auth.register(this.form.value).subscribe({
      next: () => {
        this.loading = false;
        this.success = true;
      },
      error: err => {
        this.loading = false;
        this.error = err.error?.detail || 'Registration failed. Please try again.';
      }
    });
  }
}
