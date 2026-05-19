import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { UserDataExportService } from '../../core/services/user-data-export.service';
import { extractErrorDetail } from '../../core/utils/http-errors';

@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule,
    TranslateModule
  ],
  template: `
    <div style="max-width:760px;">
      <h1 style="margin-top:0;">{{ 'account.title' | translate }}</h1>

      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ 'account.privacy.heading' | translate }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p style="color:#555; line-height:1.5; white-space:pre-line;">
            {{ 'account.privacy.explanation' | translate }}
          </p>
        </mat-card-content>
        <mat-card-actions align="end">
          <button mat-raised-button color="primary" (click)="download()" [disabled]="downloading()">
            <mat-spinner *ngIf="downloading()" diameter="16" style="display:inline-block; margin-right:8px;"></mat-spinner>
            <mat-icon *ngIf="!downloading()">download</mat-icon>
            <span style="margin-left:4px;">
              {{ (downloading() ? 'account.privacy.downloading' : 'account.privacy.download') | translate }}
            </span>
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `
})
export class AccountSettingsComponent {
  private exportService = inject(UserDataExportService);
  private snackBar = inject(MatSnackBar);
  private t = inject(TranslateService);

  downloading = signal(false);

  download(): void {
    if (this.downloading()) return;
    this.downloading.set(true);
    this.exportService.downloadDataExport().subscribe({
      next: blob => {
        this.downloading.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
        a.href = url;
        a.download = `invoiceflow-data-export-${stamp}.zip`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: err => {
        this.downloading.set(false);
        this.snackBar.open(
          extractErrorDetail(err, this.t.instant('account.privacy.failed')),
          this.t.instant('common.ok'),
          { duration: 4000 }
        );
      }
    });
  }
}
