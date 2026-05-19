import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../core/services/auth.service';
import { extractErrorDetail } from '../../../core/utils/http-errors';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    MatCardModule, MatButtonModule, MatProgressSpinnerModule,
    TranslateModule
  ],
  template: `
    <div style="display:flex; justify-content:center; align-items:center; height:100vh; background:#f5f5f5;">
      <mat-card style="width:440px; padding:24px; text-align:center;">
        <mat-card-content>
          <ng-container *ngIf="loading()">
            <mat-spinner diameter="48" style="margin:0 auto 16px;"></mat-spinner>
            <p>{{ 'auth.verify.loading' | translate }}</p>
          </ng-container>
          <ng-container *ngIf="!loading() && success()">
            <p style="font-size:3rem;">✅</p>
            <h2>{{ 'auth.verify.successTitle' | translate }}</h2>
            <p style="color:#555;">{{ 'auth.verify.successBody' | translate }}</p>
            <button mat-raised-button color="primary" routerLink="/login" style="margin-top:16px;">
              {{ 'auth.verify.goToSignIn' | translate }}
            </button>
          </ng-container>
          <ng-container *ngIf="!loading() && !success()">
            <p style="font-size:3rem;">❌</p>
            <h2>{{ 'auth.verify.failedTitle' | translate }}</h2>
            <p style="color:#555;">{{ error() }}</p>
            <button mat-raised-button routerLink="/login" style="margin-top:16px;">
              {{ 'auth.verify.back' | translate }}
            </button>
          </ng-container>
        </mat-card-content>
      </mat-card>
    </div>
  `
})
export class VerifyComponent implements OnInit {
  private t = inject(TranslateService);

  loading = signal(true);
  success = signal(false);
  error = signal('');

  constructor(private route: ActivatedRoute, private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.error.set(this.t.instant('auth.verify.defaultError'));
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: err => {
        this.loading.set(false);
        this.error.set(extractErrorDetail(err, this.error()));
      }
    });
  }
}
