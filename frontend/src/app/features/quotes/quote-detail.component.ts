import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { QuotesService } from './quotes.service';
import { Quote, QuoteStatus } from './quote.model';
import { StatusChipComponent } from './status-chip.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { extractErrorDetail } from '../../core/utils/http-errors';

@Component({
  selector: 'app-quote-detail',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, RouterLink,
    MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule,
    MatDialogModule, StatusChipComponent
  ],
  template: `
    <div style="padding:24px;">
      <a routerLink="/quotes" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        Back to quotes
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

      <ng-container *ngIf="!loading() && quote() as q">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap;">
          <div>
            <div style="display:flex; align-items:center; gap:12px;">
              <h1 style="margin:0;">{{ q.number }}</h1>
              <app-status-chip [status]="q.status"></app-status-chip>
            </div>
            <div style="color:#555; margin-top:8px;">
              <strong>Client:</strong> {{ q.clientName }} &nbsp;·&nbsp;
              <strong>Issue:</strong> {{ q.issueDate | date:'mediumDate' }} &nbsp;·&nbsp;
              <strong>Expiry:</strong> {{ q.expiryDate | date:'mediumDate' }}
            </div>
          </div>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <ng-container [ngSwitch]="q.status">
              <ng-container *ngSwitchCase="'DRAFT'">
                <button mat-raised-button color="primary" (click)="edit()" [disabled]="acting()">
                  <mat-icon>edit</mat-icon> Edit
                </button>
                <button mat-raised-button color="accent" (click)="send()" [disabled]="acting()">
                  <mat-icon>send</mat-icon> Send
                </button>
                <button mat-stroked-button color="warn" (click)="remove()" [disabled]="acting()">
                  <mat-icon>delete</mat-icon> Delete
                </button>
              </ng-container>
              <ng-container *ngSwitchCase="'SENT'">
                <button mat-raised-button color="primary" (click)="markAccepted()" [disabled]="acting()">
                  <mat-icon>check_circle</mat-icon> Mark accepted
                </button>
                <button mat-stroked-button color="warn" (click)="markRejected()" [disabled]="acting()">
                  <mat-icon>cancel</mat-icon> Mark rejected
                </button>
              </ng-container>
              <ng-container *ngSwitchCase="'ACCEPTED'">
                <button mat-raised-button color="primary" (click)="convert()" [disabled]="acting()">
                  <mat-icon>receipt_long</mat-icon> Convert to invoice
                </button>
              </ng-container>
              <ng-container *ngSwitchCase="'CONVERTED'">
                <span style="color:#666; font-style:italic;">
                  <ng-container *ngIf="convertedInvoice() as inv; else convertedFallback">
                    Converted to invoice {{ inv.number }}.
                  </ng-container>
                  <ng-template #convertedFallback>Converted to invoice.</ng-template>
                </span>
              </ng-container>
            </ng-container>
          </div>
        </div>

        <h3 style="margin:24px 0 8px;">Lines</h3>
        <table mat-table [dataSource]="q.lines" style="width:100%; background:white;">
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
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.totalExclVat | number:'1.2-2' }} €</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
        </table>

        <div style="display:flex; justify-content:flex-end; margin-top:16px;">
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:280px;">
            <dt style="color:#666;">Subtotal HT</dt>
            <dd style="margin:0; text-align:right;">{{ q.subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">VAT</dt>
            <dd style="margin:0; text-align:right;">{{ q.totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600; font-size:1.05rem;">Total TTC</dt>
            <dd style="margin:0; text-align:right; font-weight:600; font-size:1.05rem;">
              {{ q.totalInclVat | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>

        <ng-container *ngIf="q.notes">
          <h3 style="margin:24px 0 8px;">Notes</h3>
          <div style="white-space:pre-wrap; padding:12px; background:#fafafa; border-radius:4px;">{{ q.notes }}</div>
        </ng-container>
      </ng-container>
    </div>
  `
})
export class QuoteDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private quotes = inject(QuotesService);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  quote = signal<Quote | null>(null);
  loading = signal(false);
  acting = signal(false);
  error = signal<string | null>(null);
  convertedInvoice = signal<{ id: string; number: string } | null>(null);
  lineColumns = ['description', 'quantity', 'unitPrice', 'vatRate', 'totalExclVat'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Missing quote id.');
      return;
    }
    this.loading.set(true);
    this.quotes.get(id).subscribe({
      next: q => {
        this.quote.set(q);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Quote not found.'));
        this.loading.set(false);
      }
    });
  }

  edit(): void {
    const q = this.quote();
    if (q) this.router.navigate(['/quotes', q.id, 'edit']);
  }

  send(): void {
    this.confirmAndTransition({
      next: 'SENT',
      title: 'Send quote',
      message: 'Once sent, the quote becomes read-only and the status changes to SENT.',
      confirmLabel: 'Send',
      confirmColor: 'primary',
      successMessage: 'Quote marked as sent.'
    });
  }

  markAccepted(): void {
    this.confirmAndTransition({
      next: 'ACCEPTED',
      title: 'Mark quote accepted',
      message: 'The client has accepted this quote. You will then be able to convert it into an invoice.',
      confirmLabel: 'Mark accepted',
      confirmColor: 'primary',
      successMessage: 'Quote marked as accepted.'
    });
  }

  markRejected(): void {
    this.confirmAndTransition({
      next: 'REJECTED',
      title: 'Mark quote rejected',
      message: 'The client has rejected this quote. This action is final — the quote cannot be reopened or edited.',
      confirmLabel: 'Mark rejected',
      confirmColor: 'warn',
      successMessage: 'Quote marked as rejected.'
    });
  }

  convert(): void {
    const q = this.quote();
    if (!q) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Convert to invoice',
        message: 'A new DRAFT invoice will be created from this quote, and the quote will be marked CONVERTED. This cannot be undone.',
        confirmLabel: 'Convert',
        confirmColor: 'primary'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.quotes.convert(q.id).subscribe({
        next: invoice => {
          this.convertedInvoice.set(invoice);
          this.acting.set(false);
          this.snack.open(`Invoice ${invoice.number} created.`, 'Dismiss', { duration: 3000 });
          this.router.navigate(['/invoices', invoice.id]);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, 'Could not convert quote.'),
            'Dismiss',
            { duration: 4000 }
          );
        }
      });
    });
  }

  remove(): void {
    const q = this.quote();
    if (!q) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete quote',
        message: `Delete quote ${q.number}? This cannot be undone.`,
        confirmLabel: 'Delete',
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.quotes.remove(q.id).subscribe({
        next: () => {
          this.acting.set(false);
          this.snack.open(`Quote ${q.number} deleted.`, 'Dismiss', { duration: 2500 });
          this.router.navigate(['/quotes']);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, 'Could not delete quote.'),
            'Dismiss',
            { duration: 4000 }
          );
        }
      });
    });
  }

  todo(label: string): void {
    this.snack.open(`${label} — coming in next step`, 'Dismiss', { duration: 2000 });
  }

  private confirmAndTransition(opts: {
    next: QuoteStatus;
    title: string;
    message: string;
    confirmLabel: string;
    confirmColor: 'primary' | 'accent' | 'warn';
    successMessage: string;
  }): void {
    const q = this.quote();
    if (!q) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: opts.title,
        message: opts.message,
        confirmLabel: opts.confirmLabel,
        confirmColor: opts.confirmColor
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.quotes.setStatus(q.id, opts.next).subscribe({
        next: updated => {
          this.acting.set(false);
          this.quote.set(updated);
          this.snack.open(opts.successMessage, 'Dismiss', { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, 'Could not update quote status.'),
            'Dismiss',
            { duration: 4000 }
          );
        }
      });
    });
  }
}
