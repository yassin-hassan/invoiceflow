import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CreditNotesService } from './credit-notes.service';
import { CreditNote } from './credit-note.model';
import { CreditNoteStatusChipComponent } from './credit-note-status-chip.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { extractErrorDetail } from '../../core/utils/http-errors';

@Component({
  selector: 'app-credit-note-detail',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, RouterLink,
    MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatDialogModule, CreditNoteStatusChipComponent
  ],
  template: `
    <div style="padding:24px;">
      <a routerLink="/credit-notes" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        Back to credit notes
      </a>

      <ng-container *ngIf="loading()">
        <div style="display:flex; justify-content:center; padding:48px;">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      </ng-container>

      <ng-container *ngIf="!loading() && error()">
        <div style="padding:16px; background:#fdecea; color:#b71c1c; border-radius:4px;">
          {{ error() }}
        </div>
      </ng-container>

      <ng-container *ngIf="!loading() && creditNote() as cn">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap;">
          <div>
            <div style="display:flex; align-items:center; gap:12px;">
              <h1 style="margin:0;">{{ cn.number || 'Draft credit note' }}</h1>
              <app-credit-note-status-chip [status]="cn.status"></app-credit-note-status-chip>
            </div>
            <div style="color:#555; margin-top:8px;">
              <strong>Client:</strong> {{ cn.clientName }} &nbsp;·&nbsp;
              <strong>Issue:</strong> {{ cn.issueDate | date:'mediumDate' }}
            </div>
            <div style="margin-top:8px;">
              <a [routerLink]="['/invoices', cn.originalInvoiceId]"
                 style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; font-size:0.9rem;">
                <mat-icon style="font-size:16px; width:16px; height:16px;">receipt</mat-icon>
                Original invoice — {{ cn.originalInvoiceNumber || 'open' }}
              </a>
            </div>
            <div *ngIf="cn.issuedAt" style="color:#555; margin-top:4px;">
              <strong>Issued:</strong> {{ cn.issuedAt | date:'medium' }}
            </div>
            <div style="margin-top:12px;">
              <div style="color:#666; font-size:0.85rem;">Reason</div>
              <div style="white-space:pre-wrap;">{{ cn.reason }}</div>
            </div>
          </div>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <button mat-stroked-button (click)="downloadPdf()" [disabled]="acting()">
              <mat-icon>picture_as_pdf</mat-icon> PDF
            </button>
            <ng-container *ngIf="cn.status === 'DRAFT'">
              <button mat-raised-button color="primary" (click)="issue()" [disabled]="acting()">
                <mat-icon>verified</mat-icon> Issue
              </button>
              <button mat-stroked-button color="warn" (click)="remove()" [disabled]="acting()">
                <mat-icon>delete</mat-icon> Delete
              </button>
            </ng-container>
          </div>
        </div>

        <h3 style="margin:24px 0 8px;">Lines</h3>
        <table mat-table [dataSource]="cn.lines" style="width:100%; background:white;">
          <ng-container matColumnDef="description">
            <th mat-header-cell *matHeaderCellDef>Description</th>
            <td mat-cell *matCellDef="let l">{{ l.description }}</td>
          </ng-container>
          <ng-container matColumnDef="quantity">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">Qty</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.quantity | number:'1.0-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="unitPrice">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">Unit price</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.unitPrice | number:'1.2-2' }} €</td>
          </ng-container>
          <ng-container matColumnDef="vatRate">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">VAT</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.vatRate }}%</td>
          </ng-container>
          <ng-container matColumnDef="totalExclVat">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">Line total HT</th>
            <td mat-cell *matCellDef="let l" style="text-align:right; color:#b71c1c;">
              -{{ l.totalExclVat | number:'1.2-2' }} €
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
        </table>

        <div style="display:flex; justify-content:flex-end; margin-top:16px;">
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:320px;">
            <dt style="color:#666;">Subtotal HT</dt>
            <dd style="margin:0; text-align:right; color:#b71c1c;">-{{ cn.subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">VAT</dt>
            <dd style="margin:0; text-align:right; color:#b71c1c;">-{{ cn.totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600;">Total TTC</dt>
            <dd style="margin:0; text-align:right; font-weight:600; color:#b71c1c;">
              -{{ cn.totalInclVat | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>
      </ng-container>
    </div>
  `
})
export class CreditNoteDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private creditNotes = inject(CreditNotesService);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  creditNote = signal<CreditNote | null>(null);
  loading = signal(false);
  acting = signal(false);
  error = signal<string | null>(null);

  lineColumns = ['description', 'quantity', 'unitPrice', 'vatRate', 'totalExclVat'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Missing credit note id.');
      return;
    }
    this.loading.set(true);
    this.creditNotes.get(id).subscribe({
      next: cn => {
        this.creditNote.set(cn);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Credit note not found.'));
        this.loading.set(false);
      }
    });
  }

  downloadPdf(): void {
    const cn = this.creditNote();
    if (!cn) return;
    this.acting.set(true);
    this.creditNotes.downloadPdf(cn.id).subscribe({
      next: blob => {
        this.acting.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = (cn.number ?? `brouillon-av-${cn.id}`) + '.pdf';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.acting.set(false);
        this.snack.open('Could not download PDF.', 'Dismiss', { duration: 3000 });
      }
    });
  }

  issue(): void {
    const cn = this.creditNote();
    if (!cn) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Issue credit note',
        message: 'A sequential AV-YYYY-NNN number will be assigned now. The credit note becomes read-only afterwards.',
        confirmLabel: 'Issue',
        confirmColor: 'primary'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.creditNotes.issue(cn.id).subscribe({
        next: updated => {
          this.acting.set(false);
          this.creditNote.set(updated);
          this.snack.open(`Credit note ${updated.number} issued.`, 'Dismiss', { duration: 3000 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(extractErrorDetail(err, 'Could not issue credit note.'), 'Dismiss', { duration: 4000 });
        }
      });
    });
  }

  remove(): void {
    const cn = this.creditNote();
    if (!cn) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete credit note',
        message: 'Delete this draft credit note? This cannot be undone.',
        confirmLabel: 'Delete',
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.creditNotes.remove(cn.id).subscribe({
        next: () => {
          this.acting.set(false);
          this.snack.open('Credit note deleted.', 'Dismiss', { duration: 2500 });
          this.router.navigate(['/credit-notes']);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(extractErrorDetail(err, 'Could not delete credit note.'), 'Dismiss', { duration: 4000 });
        }
      });
    });
  }
}
