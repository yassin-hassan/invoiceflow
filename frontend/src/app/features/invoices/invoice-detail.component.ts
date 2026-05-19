import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { InvoicesService } from './invoices.service';
import { Invoice, InvoiceStatus } from './invoice.model';
import { InvoiceStatusChipComponent } from './invoice-status-chip.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { PaymentDialogComponent } from './payment-dialog.component';
import { CreditNoteFormDialogComponent } from '../credit-notes/credit-note-form-dialog.component';
import { CreditNotesService } from '../credit-notes/credit-notes.service';
import { CreateCreditNoteRequest } from '../credit-notes/credit-note.model';
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
    MatDialogModule, MatTooltipModule, InvoiceStatusChipComponent, TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <a routerLink="/invoices" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        {{ 'invoices.backToList' | translate }}
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
              <h1 style="margin:0;">{{ inv.number || ('invoices.draftInvoice' | translate) }}</h1>
              <app-invoice-status-chip [status]="inv.status"></app-invoice-status-chip>
            </div>
            <div style="color:#555; margin-top:8px;">
              <strong>{{ 'invoices.detail.client' | translate }}:</strong> {{ inv.clientName }} &nbsp;·&nbsp;
              <strong>{{ 'invoices.detail.issue' | translate }}:</strong> {{ inv.issueDate | date:'mediumDate' }} &nbsp;·&nbsp;
              <strong>{{ 'invoices.detail.due' | translate }}:</strong> {{ inv.dueDate | date:'mediumDate' }}
            </div>
            <div *ngIf="inv.sentAt" style="color:#555; margin-top:4px;">
              <strong>{{ 'invoices.detail.sent' | translate }}:</strong> {{ inv.sentAt | date:'medium' }} &nbsp;·&nbsp;
              <span style="color:#666;">{{ 'invoices.detail.sentTo' | translate:{ email: inv.clientEmail } }}</span>
            </div>
            <div *ngIf="inv.paymentTerms" style="color:#666; margin-top:4px; font-style:italic;">
              {{ inv.paymentTerms }}
            </div>
            <div *ngIf="inv.quoteId" style="margin-top:8px;">
              <a [routerLink]="['/quotes', inv.quoteId]"
                 style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; font-size:0.9rem;">
                <mat-icon style="font-size:16px; width:16px; height:16px;">request_quote</mat-icon>
                {{ 'invoices.detail.createdFromQuote' | translate }}
              </a>
            </div>
            <div *ngFor="let cn of inv.creditNotes" style="margin-top:8px;">
              <a [routerLink]="['/credit-notes', cn.id]"
                 style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; font-size:0.9rem;">
                <mat-icon style="font-size:16px; width:16px; height:16px;">undo</mat-icon>
                {{ 'invoices.detail.creditNoteLink' | translate:{ label: cn.number || ('invoices.detail.creditNoteDraft' | translate) } }}
              </a>
            </div>
            <div *ngIf="showActivePaymentLinkBadge(inv)"
                 style="margin-top:12px; display:inline-flex; align-items:center; gap:8px; padding:6px 10px; background:#e8f5e9; border:1px solid #a5d6a7; border-radius:16px; font-size:0.9rem;">
              <mat-icon style="font-size:18px; width:18px; height:18px; color:#2e7d32;">link</mat-icon>
              <a [href]="inv.stripePaymentLinkUrl" target="_blank" rel="noopener"
                 style="color:#2e7d32; text-decoration:none; font-weight:500;">
                {{ 'invoices.detail.activeStripeLink' | translate }}
              </a>
              <button mat-icon-button
                      (click)="copyPaymentLink(inv.stripePaymentLinkUrl!)"
                      [matTooltip]="'invoices.detail.copyLink' | translate"
                      style="width:24px; height:24px; line-height:24px;">
                <mat-icon style="font-size:16px; width:16px; height:16px;">content_copy</mat-icon>
              </button>
            </div>
          </div>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <button mat-stroked-button (click)="downloadPdf()" [disabled]="acting()">
              <mat-icon>picture_as_pdf</mat-icon> {{ 'invoices.detail.pdf' | translate }}
            </button>
            <ng-container [ngSwitch]="inv.status">
              <ng-container *ngSwitchCase="'DRAFT'">
                <button mat-raised-button color="primary" (click)="edit()" [disabled]="acting()">
                  <mat-icon>edit</mat-icon> {{ 'invoices.detail.edit' | translate }}
                </button>
                <button mat-raised-button color="accent" (click)="send()" [disabled]="acting()">
                  <mat-icon>send</mat-icon> {{ 'invoices.detail.send' | translate }}
                </button>
                <button mat-stroked-button color="warn" (click)="remove()" [disabled]="acting()">
                  <mat-icon>delete</mat-icon> {{ 'invoices.detail.delete' | translate }}
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
                  <mat-icon>payments</mat-icon> {{ 'invoices.detail.recordPayment' | translate }}
                </button>
              </ng-container>

              <ng-container *ngSwitchCase="'PAID'">
                <span style="color:#1b5e20; font-weight:500; align-self:center;">
                  <mat-icon style="vertical-align:middle;">check_circle</mat-icon> {{ 'invoices.detail.fullyPaid' | translate }}
                </span>
              </ng-container>
              <ng-container *ngSwitchCase="'CANCELLED'">
                <span style="color:#666; font-style:italic; align-self:center;">{{ 'invoices.detail.cancelled' | translate }}</span>
              </ng-container>
            </ng-container>
            <button *ngIf="canCreateCreditNote(inv)" mat-stroked-button (click)="createCreditNote()" [disabled]="acting()">
              <mat-icon>undo</mat-icon> {{ 'invoices.detail.creditNote' | translate }}
            </button>
          </div>
        </div>

        <ng-template #activeActions let-inv="inv">
          <button mat-raised-button color="primary" (click)="recordPayment()" [disabled]="acting()">
            <mat-icon>payments</mat-icon> {{ 'invoices.detail.recordPayment' | translate }}
          </button>
          <button *ngIf="canMarkOverdue(inv)" mat-stroked-button (click)="markOverdue()" [disabled]="acting()">
            <mat-icon>schedule</mat-icon> {{ 'invoices.detail.markOverdue' | translate }}
          </button>
        </ng-template>

        <div *ngIf="showStalePaymentLinkBanner(inv)"
             style="margin-top:16px; padding:12px 16px; background:#fff3e0; border-left:4px solid #fb8c00; border-radius:4px; display:flex; align-items:flex-start; gap:8px;">
          <mat-icon style="color:#fb8c00;">info</mat-icon>
          <span style="color:#5d4037;">
            {{ 'invoices.detail.stalePaymentLink' | translate }}
          </span>
        </div>

        <h3 style="margin:24px 0 8px;">{{ 'invoices.linesHeading' | translate }}</h3>
        <table mat-table [dataSource]="inv.lines" style="width:100%; background:white;">
          <ng-container matColumnDef="description">
            <th mat-header-cell *matHeaderCellDef>{{ 'invoices.detail.lineDescription' | translate }}</th>
            <td mat-cell *matCellDef="let l">{{ l.description }}</td>
          </ng-container>
          <ng-container matColumnDef="quantity">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'invoices.detail.lineQty' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.quantity | number:'1.0-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="unitPrice">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'invoices.detail.lineUnitPrice' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.unitPrice | number:'1.2-2' }} €</td>
          </ng-container>
          <ng-container matColumnDef="vatRate">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'invoices.detail.lineVat' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.vatRate }}%</td>
          </ng-container>
          <ng-container matColumnDef="totalExclVat">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'invoices.detail.lineTotalHt' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.totalExclVat | number:'1.2-2' }} €</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
        </table>

        <div style="display:flex; justify-content:flex-end; margin-top:16px;">
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:320px;">
            <dt style="color:#666;">{{ 'invoices.totals.subtotalHt' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ inv.subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">{{ 'invoices.totals.vat' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ inv.totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600;">{{ 'invoices.totals.totalTtc' | translate }}</dt>
            <dd style="margin:0; text-align:right; font-weight:600;">{{ inv.totalInclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#1b5e20;">{{ 'invoices.totals.paid' | translate }}</dt>
            <dd style="margin:0; text-align:right; color:#1b5e20;">{{ inv.amountPaid | number:'1.2-2' }} €</dd>
            <ng-container *ngIf="inv.creditNoteTotalInclVat">
              <dt style="color:#b71c1c;">{{ 'invoices.totals.creditNote' | translate }}</dt>
              <dd style="margin:0; text-align:right; color:#b71c1c;">
                -{{ inv.creditNoteTotalInclVat | number:'1.2-2' }} €
              </dd>
            </ng-container>
            <dt style="font-weight:600; font-size:1.05rem;" [style.color]="netAmountDue(inv) > 0 ? '#b71c1c' : '#1b5e20'">
              {{ (inv.creditNoteTotalInclVat ? 'invoices.totals.netAmountDue' : 'invoices.totals.amountDue') | translate }}
            </dt>
            <dd style="margin:0; text-align:right; font-weight:600; font-size:1.05rem;"
                [style.color]="netAmountDue(inv) > 0 ? '#b71c1c' : '#1b5e20'">
              {{ netAmountDue(inv) | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>

        <h3 style="margin:24px 0 8px;">{{ 'invoices.paymentsHeading' | translate }}</h3>
        <ng-container *ngIf="inv.payments.length === 0">
          <div style="padding:16px; color:#777; background:#fafafa; border-radius:4px;">
            {{ 'invoices.noPayments' | translate }}
          </div>
        </ng-container>
        <table *ngIf="inv.payments.length > 0" mat-table [dataSource]="inv.payments" style="width:100%; background:white;">
          <ng-container matColumnDef="paidAt">
            <th mat-header-cell *matHeaderCellDef>{{ 'invoices.detail.paymentDate' | translate }}</th>
            <td mat-cell *matCellDef="let p">{{ p.paidAt | date:'mediumDate' }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'invoices.detail.paymentAmount' | translate }}</th>
            <td mat-cell *matCellDef="let p" style="text-align:right;">{{ p.amount | number:'1.2-2' }} €</td>
          </ng-container>
          <ng-container matColumnDef="method">
            <th mat-header-cell *matHeaderCellDef>{{ 'invoices.detail.paymentMethod' | translate }}</th>
            <td mat-cell *matCellDef="let p">{{ ('invoices.payment.methods.' + p.method) | translate }}</td>
          </ng-container>
          <ng-container matColumnDef="notes">
            <th mat-header-cell *matHeaderCellDef>{{ 'invoices.detail.paymentNotes' | translate }}</th>
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
  private creditNotes = inject(CreditNotesService);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);
  private t = inject(TranslateService);

  invoice = signal<Invoice | null>(null);
  loading = signal(false);
  acting = signal(false);
  error = signal<string | null>(null);

  lineColumns = ['description', 'quantity', 'unitPrice', 'vatRate', 'totalExclVat'];
  paymentColumns = ['paidAt', 'amount', 'method', 'notes'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set(this.t.instant('invoices.detail.missingId'));
      return;
    }
    this.loading.set(true);
    this.invoices.get(id).subscribe({
      next: inv => {
        this.invoice.set(inv);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, this.t.instant('invoices.detail.notFound')));
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

  copyPaymentLink(url: string): void {
    navigator.clipboard.writeText(url).then(
      () => this.snack.open(this.t.instant('invoices.detail.linkCopied'), this.t.instant('common.ok'), { duration: 2000 }),
      () => this.snack.open(this.t.instant('invoices.detail.copyFailed'), this.t.instant('common.ok'), { duration: 2500 })
    );
  }

  showActivePaymentLinkBadge(inv: Invoice): boolean {
    if (!inv.stripePaymentLinkUrl) return false;
    return inv.status === 'SENT' || inv.status === 'PARTIALLY_PAID' || inv.status === 'OVERDUE';
  }

  showStalePaymentLinkBanner(inv: Invoice): boolean {
    const expectsPayment = inv.status === 'SENT' || inv.status === 'PARTIALLY_PAID' || inv.status === 'OVERDUE';
    if (!expectsPayment) return false;
    if (inv.stripePaymentLinkUrl) return false;
    return inv.creditNotes.some(cn => cn.status === 'ISSUED');
  }

  netAmountDue(inv: Invoice): number {
    const cn = inv.creditNoteTotalInclVat ?? 0;
    return Math.max(0, Math.round((inv.amountDue - cn) * 100) / 100);
  }

  canCreateCreditNote(inv: Invoice): boolean {
    const eligible = inv.status === 'SENT' || inv.status === 'PARTIALLY_PAID'
        || inv.status === 'PAID' || inv.status === 'OVERDUE';
    if (!eligible) return false;
    return this.hasRemainingHeadroom(inv);
  }

  private hasRemainingHeadroom(inv: Invoice): boolean {
    const issuedByLine = new Map<string, number>();
    for (const cn of inv.creditNotes) {
      if (cn.status !== 'ISSUED') continue;
      for (const cnLine of cn.lines) {
        issuedByLine.set(cnLine.invoiceLineId,
          (issuedByLine.get(cnLine.invoiceLineId) ?? 0) + cnLine.quantity);
      }
    }
    return inv.lines.some(l => l.quantity - (issuedByLine.get(l.id) ?? 0) > 0.0001);
  }

  createCreditNote(): void {
    const inv = this.invoice();
    if (!inv) return;
    this.dialog.open(CreditNoteFormDialogComponent, {
      data: { invoice: inv }
    }).afterClosed().subscribe((req: CreateCreditNoteRequest | undefined) => {
      if (!req) return;
      this.acting.set(true);
      this.creditNotes.createForInvoice(inv.id, req).subscribe({
        next: created => {
          this.acting.set(false);
          this.snack.open(this.t.instant('invoices.creditNoteShortcut.created'), this.t.instant('common.dismiss'), { duration: 2500 });
          this.router.navigate(['/credit-notes', created.id]);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(extractErrorDetail(err, this.t.instant('invoices.creditNoteShortcut.createFailed')), this.t.instant('common.dismiss'), { duration: 4000 });
        }
      });
    });
  }

  send(): void {
    const inv = this.invoice();
    if (!inv) return;
    if (!inv.clientEmail || !inv.clientEmail.trim()) {
      this.snack.open(this.t.instant('invoices.send.noEmail'), this.t.instant('common.dismiss'), { duration: 4000 });
      return;
    }
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant('invoices.send.title'),
        message: this.t.instant('invoices.send.message', { email: inv.clientEmail }),
        confirmLabel: this.t.instant('invoices.send.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'primary'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.invoices.send(inv.id).subscribe({
        next: updated => {
          this.acting.set(false);
          this.invoice.set(updated);
          this.snack.open(
            this.t.instant('invoices.send.success', { number: updated.number, email: updated.clientEmail }),
            this.t.instant('common.dismiss'),
            { duration: 3500 }
          );
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(extractErrorDetail(err, this.t.instant('invoices.send.failed')), this.t.instant('common.dismiss'), { duration: 4000 });
        }
      });
    });
  }

  markOverdue(): void {
    this.confirmAndTransition({
      next: 'OVERDUE',
      titleKey: 'invoices.markOverdue.title',
      messageKey: 'invoices.markOverdue.message',
      confirmKey: 'invoices.markOverdue.confirm',
      confirmColor: 'primary',
      successKey: 'invoices.markOverdue.success'
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
        a.download = (inv.number ?? `${this.t.instant('invoices.pdf.draftFilenamePrefix')}-${inv.id}`) + '.pdf';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.acting.set(false);
        this.snack.open(this.t.instant('invoices.pdf.failed'), this.t.instant('common.dismiss'), { duration: 3000 });
      }
    });
  }

  recordPayment(): void {
    const inv = this.invoice();
    if (!inv) return;
    const net = this.netAmountDue(inv);
    if (net <= 0) {
      this.snack.open(this.t.instant('invoices.recordPayment.nothingDue'), this.t.instant('common.dismiss'), { duration: 2500 });
      return;
    }
    this.dialog.open(PaymentDialogComponent, {
      data: { invoiceNumber: inv.number ?? '', amountDue: net }
    }).afterClosed().subscribe(req => {
      if (!req) return;
      this.acting.set(true);
      this.invoices.recordPayment(inv.id, req).subscribe({
        next: updated => {
          this.acting.set(false);
          this.invoice.set(updated);
          this.snack.open(this.t.instant('invoices.recordPayment.success'), this.t.instant('common.dismiss'), { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('invoices.recordPayment.failed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }

  remove(): void {
    const inv = this.invoice();
    if (!inv) return;
    const messageKey = inv.number ? 'invoices.delete.messageWithNumber' : 'invoices.delete.messageDraft';
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant('invoices.delete.title'),
        message: this.t.instant(messageKey, { number: inv.number }),
        confirmLabel: this.t.instant('invoices.delete.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.invoices.remove(inv.id).subscribe({
        next: () => {
          this.acting.set(false);
          const successKey = inv.number ? 'invoices.delete.successWithNumber' : 'invoices.delete.successDraft';
          this.snack.open(
            this.t.instant(successKey, { number: inv.number }),
            this.t.instant('common.dismiss'),
            { duration: 2500 }
          );
          this.router.navigate(['/invoices']);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('invoices.delete.failed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }

  private confirmAndTransition(opts: {
    next: InvoiceStatus;
    titleKey: string;
    messageKey: string;
    confirmKey: string;
    confirmColor: 'primary' | 'accent' | 'warn';
    successKey: string;
  }): void {
    const inv = this.invoice();
    if (!inv) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant(opts.titleKey),
        message: this.t.instant(opts.messageKey),
        confirmLabel: this.t.instant(opts.confirmKey),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: opts.confirmColor
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.invoices.setStatus(inv.id, opts.next).subscribe({
        next: updated => {
          this.acting.set(false);
          this.invoice.set(updated);
          this.snack.open(this.t.instant(opts.successKey), this.t.instant('common.dismiss'), { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('invoices.statusUpdateFailed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }
}
