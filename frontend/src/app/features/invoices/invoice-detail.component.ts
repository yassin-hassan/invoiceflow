import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { InvoicesService } from './invoices.service';
import { Invoice, InvoiceStatus } from './invoice.model';
import { InvoiceStatusChipComponent } from './invoice-status-chip.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { PaymentDialogComponent } from './payment-dialog.component';
import { extractErrorDetail } from '../../core/utils/http-errors';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, RouterLink,
    MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule,
    MatDialogModule, InvoiceStatusChipComponent
  ],
  template: `
    <div style="padding:24px;">
      <a routerLink="/invoices" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        Back to invoices
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

      <ng-container *ngIf="!loading() && invoice() as inv">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap;">
          <div>
            <div style="display:flex; align-items:center; gap:12px;">
              <h1 style="margin:0;">{{ inv.number || 'Draft invoice' }}</h1>
              <app-invoice-status-chip [status]="inv.status"></app-invoice-status-chip>
            </div>
            <div style="color:#555; margin-top:8px;">
              <strong>Client:</strong> {{ inv.clientName }} &nbsp;·&nbsp;
              <strong>Issue:</strong> {{ inv.issueDate | date:'mediumDate' }} &nbsp;·&nbsp;
              <strong>Due:</strong> {{ inv.dueDate | date:'mediumDate' }}
            </div>
            <div *ngIf="inv.sentAt" style="color:#555; margin-top:4px;">
              <strong>Sent:</strong> {{ inv.sentAt | date:'medium' }} &nbsp;·&nbsp;
              <span style="color:#666;">to {{ inv.clientEmail }}</span>
            </div>
            <div *ngIf="inv.paymentTerms" style="color:#666; margin-top:4px; font-style:italic;">
              {{ inv.paymentTerms }}
            </div>
            <div *ngIf="inv.quoteId" style="margin-top:8px;">
              <a [routerLink]="['/quotes', inv.quoteId]"
                 style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; font-size:0.9rem;">
                <mat-icon style="font-size:16px; width:16px; height:16px;">request_quote</mat-icon>
                Created from quote — open
              </a>
            </div>
          </div>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <button mat-stroked-button (click)="downloadPdf()" [disabled]="acting()">
              <mat-icon>picture_as_pdf</mat-icon> PDF
            </button>
            <ng-container [ngSwitch]="inv.status">
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
                <ng-container *ngTemplateOutlet="activeActions; context: { inv }"></ng-container>
              </ng-container>
              <ng-container *ngSwitchCase="'PARTIALLY_PAID'">
                <ng-container *ngTemplateOutlet="activeActions; context: { inv }"></ng-container>
              </ng-container>
              <ng-container *ngSwitchCase="'OVERDUE'">
                <button mat-raised-button color="primary" (click)="recordPayment()" [disabled]="acting()">
                  <mat-icon>payments</mat-icon> Record payment
                </button>
                <button mat-stroked-button color="warn" (click)="cancelInvoice()" [disabled]="acting()">
                  <mat-icon>cancel</mat-icon> Cancel
                </button>
              </ng-container>

              <ng-container *ngSwitchCase="'PAID'">
                <span style="color:#1b5e20; font-weight:500; align-self:center;">
                  <mat-icon style="vertical-align:middle;">check_circle</mat-icon> Fully paid
                </span>
              </ng-container>
              <ng-container *ngSwitchCase="'CANCELLED'">
                <span style="color:#666; font-style:italic; align-self:center;">Cancelled — no actions available.</span>
              </ng-container>
            </ng-container>
          </div>
        </div>

        <ng-template #activeActions let-inv="inv">
          <button mat-raised-button color="primary" (click)="recordPayment()" [disabled]="acting()">
            <mat-icon>payments</mat-icon> Record payment
          </button>
          <button *ngIf="canMarkOverdue(inv)" mat-stroked-button (click)="markOverdue()" [disabled]="acting()">
            <mat-icon>schedule</mat-icon> Mark overdue
          </button>
          <button mat-stroked-button color="warn" (click)="cancelInvoice()" [disabled]="acting()">
            <mat-icon>cancel</mat-icon> Cancel
          </button>
        </ng-template>

        <h3 style="margin:24px 0 8px;">Lines</h3>
        <table mat-table [dataSource]="inv.lines" style="width:100%; background:white;">
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
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:320px;">
            <dt style="color:#666;">Subtotal HT</dt>
            <dd style="margin:0; text-align:right;">{{ inv.subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">VAT</dt>
            <dd style="margin:0; text-align:right;">{{ inv.totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600;">Total TTC</dt>
            <dd style="margin:0; text-align:right; font-weight:600;">{{ inv.totalInclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#1b5e20;">Paid</dt>
            <dd style="margin:0; text-align:right; color:#1b5e20;">{{ inv.amountPaid | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600; font-size:1.05rem;" [style.color]="inv.amountDue > 0 ? '#b71c1c' : '#1b5e20'">
              Amount due
            </dt>
            <dd style="margin:0; text-align:right; font-weight:600; font-size:1.05rem;"
                [style.color]="inv.amountDue > 0 ? '#b71c1c' : '#1b5e20'">
              {{ inv.amountDue | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>

        <h3 style="margin:24px 0 8px;">Payments</h3>
        <ng-container *ngIf="inv.payments.length === 0">
          <div style="padding:16px; color:#777; background:#fafafa; border-radius:4px;">
            No payments recorded yet.
          </div>
        </ng-container>
        <table *ngIf="inv.payments.length > 0" mat-table [dataSource]="inv.payments" style="width:100%; background:white;">
          <ng-container matColumnDef="paidAt">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let p">{{ p.paidAt | date:'mediumDate' }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">Amount</th>
            <td mat-cell *matCellDef="let p" style="text-align:right;">{{ p.amount | number:'1.2-2' }} €</td>
          </ng-container>
          <ng-container matColumnDef="method">
            <th mat-header-cell *matHeaderCellDef>Method</th>
            <td mat-cell *matCellDef="let p">{{ p.method }}</td>
          </ng-container>
          <ng-container matColumnDef="notes">
            <th mat-header-cell *matHeaderCellDef>Notes</th>
            <td mat-cell *matCellDef="let p" style="color:#666;">{{ p.notes }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="paymentColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: paymentColumns;"></tr>
        </table>
      </ng-container>
    </div>
  `
})
export class InvoiceDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private invoices = inject(InvoicesService);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  invoice = signal<Invoice | null>(null);
  loading = signal(false);
  acting = signal(false);
  error = signal<string | null>(null);

  lineColumns = ['description', 'quantity', 'unitPrice', 'vatRate', 'totalExclVat'];
  paymentColumns = ['paidAt', 'amount', 'method', 'notes'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Missing invoice id.');
      return;
    }
    this.loading.set(true);
    this.invoices.get(id).subscribe({
      next: inv => {
        this.invoice.set(inv);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Invoice not found.'));
        this.loading.set(false);
      }
    });
  }

  edit(): void {
    const inv = this.invoice();
    if (inv) this.router.navigate(['/invoices', inv.id, 'edit']);
  }

  canMarkOverdue(inv: Invoice): boolean {
    return inv.dueDate < todayIso();
  }

  send(): void {
    const inv = this.invoice();
    if (!inv) return;
    if (!inv.clientEmail || !inv.clientEmail.trim()) {
      this.snack.open('This client has no email address.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Send invoice',
        message: `Send this invoice by email to ${inv.clientEmail}? A sequential invoice number will be assigned now, and the invoice becomes read-only afterwards.`,
        confirmLabel: 'Send',
        confirmColor: 'primary'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.invoices.send(inv.id).subscribe({
        next: updated => {
          this.acting.set(false);
          this.invoice.set(updated);
          this.snack.open(`Invoice ${updated.number} sent to ${updated.clientEmail}.`, 'Dismiss', { duration: 3500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(extractErrorDetail(err, 'Could not send invoice.'), 'Dismiss', { duration: 4000 });
        }
      });
    });
  }

  markOverdue(): void {
    this.confirmAndTransition({
      next: 'OVERDUE',
      title: 'Mark invoice overdue',
      message: 'The due date has passed and the invoice is unpaid. Marking it overdue is informational — you can still record payments or cancel it afterwards.',
      confirmLabel: 'Mark overdue',
      confirmColor: 'primary',
      successMessage: 'Invoice marked as overdue.'
    });
  }

  downloadPdf(): void {
    const inv = this.invoice();
    if (!inv) return;
    this.acting.set(true);
    this.invoices.downloadPdf(inv.id).subscribe({
      next: blob => {
        this.acting.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = (inv.number ?? `brouillon-${inv.id}`) + '.pdf';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.acting.set(false);
        this.snack.open('Could not download PDF.', 'Dismiss', { duration: 3000 });
      }
    });
  }

  recordPayment(): void {
    const inv = this.invoice();
    if (!inv) return;
    if (inv.amountDue <= 0) {
      this.snack.open('Nothing left to pay on this invoice.', 'Dismiss', { duration: 2500 });
      return;
    }
    this.dialog.open(PaymentDialogComponent, {
      data: { invoiceNumber: inv.number ?? '', amountDue: inv.amountDue }
    }).afterClosed().subscribe(req => {
      if (!req) return;
      this.acting.set(true);
      this.invoices.recordPayment(inv.id, req).subscribe({
        next: updated => {
          this.acting.set(false);
          this.invoice.set(updated);
          this.snack.open('Payment recorded.', 'Dismiss', { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, 'Could not record payment.'),
            'Dismiss',
            { duration: 4000 }
          );
        }
      });
    });
  }

  remove(): void {
    const inv = this.invoice();
    if (!inv) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete invoice',
        message: `Delete ${inv.number ? 'invoice ' + inv.number : 'this draft invoice'}? This cannot be undone.`,
        confirmLabel: 'Delete',
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.invoices.remove(inv.id).subscribe({
        next: () => {
          this.acting.set(false);
          this.snack.open(`${inv.number ? 'Invoice ' + inv.number : 'Draft invoice'} deleted.`, 'Dismiss', { duration: 2500 });
          this.router.navigate(['/invoices']);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, 'Could not delete invoice.'),
            'Dismiss',
            { duration: 4000 }
          );
        }
      });
    });
  }

  cancelInvoice(): void {
    this.confirmAndTransition({
      next: 'CANCELLED',
      title: 'Cancel invoice',
      message: 'Cancelling is irreversible. Use a credit note (coming later) if you need to refund a paid invoice.',
      confirmLabel: 'Cancel invoice',
      confirmColor: 'warn',
      successMessage: 'Invoice cancelled.'
    });
  }

  todo(label: string): void {
    this.snack.open(`${label} — coming in next step`, 'Dismiss', { duration: 2000 });
  }

  private confirmAndTransition(opts: {
    next: InvoiceStatus;
    title: string;
    message: string;
    confirmLabel: string;
    confirmColor: 'primary' | 'accent' | 'warn';
    successMessage: string;
  }): void {
    const inv = this.invoice();
    if (!inv) return;
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
      this.invoices.setStatus(inv.id, opts.next).subscribe({
        next: updated => {
          this.acting.set(false);
          this.invoice.set(updated);
          this.snack.open(opts.successMessage, 'Dismiss', { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, 'Could not update invoice status.'),
            'Dismiss',
            { duration: 4000 }
          );
        }
      });
    });
  }
}
