import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div style="display:flex; justify-content:center; align-items:center; height:100vh; background:#f5f5f5;">
      <mat-card style="width:440px; padding:24px; text-align:center;">
        <mat-card-content>
          <ng-container *ngIf="loading()">
            <mat-spinner diameter="48" style="margin:0 auto 16px;"></mat-spinner>
            <p>Verifying your email...</p>
          </ng-container>
          <ng-container *ngIf="!loading() && success()">
            <p style="font-size:3rem;">✅</p>
            <h2>Email verified!</h2>
            <p style="color:#555;">Your account is now active. You can sign in.</p>
            <button mat-raised-button color="primary" routerLink="/login" style="margin-top:16px;">
              Go to Sign in
            </button>
          </ng-container>
          <ng-container *ngIf="!loading() && !success()">
            <p style="font-size:3rem;">❌</p>
            <h2>Verification failed</h2>
            <p style="color:#555;">{{ error() }}</p>
            <button mat-raised-button routerLink="/login" style="margin-top:16px;">
              Back to Sign in
            </button>
          </ng-container>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class VerifyComponent implements OnInit {
  loading = signal(true);
  success = signal(false);
  error = signal('The link is invalid or has expired.');

  constructor(private route: ActivatedRoute, private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: err => {
        this.loading.set(false);
        const detail = typeof err.error === 'string' ? this.tryParseDetail(err.error) : err.error?.detail;
        if (detail) this.error.set(detail);
      }
    });
  }

  private tryParseDetail(body: string): string | undefined {
    try { return JSON.parse(body)?.detail; } catch { return undefined; }
  }
}
