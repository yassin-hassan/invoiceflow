import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
    MatDialogModule, StatusChipComponent, TranslateModule
  ],
  template: `
    <div style="padding:24px;">
      <a routerLink="/quotes" style="display:inline-flex; align-items:center; gap:4px; color:#1976d2; text-decoration:none; margin-bottom:16px;">
        <mat-icon style="font-size:18px; width:18px; height:18px;">arrow_back</mat-icon>
        {{ 'quotes.backToList' | translate }}
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
              <strong>{{ 'quotes.detail.client' | translate }}:</strong> {{ q.clientName }} &nbsp;·&nbsp;
              <strong>{{ 'quotes.detail.issue' | translate }}:</strong> {{ q.issueDate | date:'mediumDate' }} &nbsp;·&nbsp;
              <strong>{{ 'quotes.detail.expiry' | translate }}:</strong> {{ q.expiryDate | date:'mediumDate' }}
            </div>
          </div>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <ng-container [ngSwitch]="q.status">
              <ng-container *ngSwitchCase="'DRAFT'">
                <button mat-raised-button color="primary" (click)="edit()" [disabled]="acting()">
                  <mat-icon>edit</mat-icon> {{ 'quotes.detail.edit' | translate }}
                </button>
                <button mat-raised-button color="accent" (click)="send()" [disabled]="acting()">
                  <mat-icon>send</mat-icon> {{ 'quotes.detail.send' | translate }}
                </button>
                <button mat-stroked-button color="warn" (click)="remove()" [disabled]="acting()">
                  <mat-icon>delete</mat-icon> {{ 'quotes.detail.delete' | translate }}
                </button>
              </ng-container>
              <ng-container *ngSwitchCase="'SENT'">
                <button mat-raised-button color="primary" (click)="markAccepted()" [disabled]="acting()">
                  <mat-icon>check_circle</mat-icon> {{ 'quotes.detail.markAccepted' | translate }}
                </button>
                <button mat-stroked-button color="warn" (click)="markRejected()" [disabled]="acting()">
                  <mat-icon>cancel</mat-icon> {{ 'quotes.detail.markRejected' | translate }}
                </button>
              </ng-container>
              <ng-container *ngSwitchCase="'ACCEPTED'">
                <button mat-raised-button color="primary" (click)="convert()" [disabled]="acting()">
                  <mat-icon>receipt_long</mat-icon> {{ 'quotes.detail.convertToInvoice' | translate }}
                </button>
              </ng-container>
              <ng-container *ngSwitchCase="'CONVERTED'">
                <span style="color:#666; font-style:italic;">
                  <ng-container *ngIf="convertedInvoice() as inv; else convertedFallback">
                    {{ 'quotes.detail.convertedTo' | translate:{ number: inv.number } }}
                  </ng-container>
                  <ng-template #convertedFallback>{{ 'quotes.detail.convertedFallback' | translate }}</ng-template>
                </span>
              </ng-container>
            </ng-container>
          </div>
        </div>

        <h3 style="margin:24px 0 8px;">{{ 'quotes.linesHeading' | translate }}</h3>
        <table mat-table [dataSource]="q.lines" style="width:100%; background:white;">
          <ng-container matColumnDef="description">
            <th mat-header-cell *matHeaderCellDef>{{ 'quotes.detail.lineDescription' | translate }}</th>
            <td mat-cell *matCellDef="let l">{{ l.description }}</td>
          </ng-container>
          <ng-container matColumnDef="quantity">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'quotes.detail.lineQty' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.quantity | number:'1.0-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="unitPrice">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'quotes.detail.lineUnitPrice' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.unitPrice | number:'1.2-2' }} €</td>
          </ng-container>
          <ng-container matColumnDef="vatRate">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'quotes.detail.lineVat' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.vatRate }}%</td>
          </ng-container>
          <ng-container matColumnDef="totalExclVat">
            <th mat-header-cell *matHeaderCellDef style="text-align:right;">{{ 'quotes.detail.lineTotalHt' | translate }}</th>
            <td mat-cell *matCellDef="let l" style="text-align:right;">{{ l.totalExclVat | number:'1.2-2' }} €</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="lineColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: lineColumns;"></tr>
        </table>

        <div style="display:flex; justify-content:flex-end; margin-top:16px;">
          <dl style="display:grid; grid-template-columns:auto auto; gap:4px 24px; margin:0; min-width:280px;">
            <dt style="color:#666;">{{ 'quotes.totals.subtotalHt' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ q.subtotalExclVat | number:'1.2-2' }} €</dd>
            <dt style="color:#666;">{{ 'quotes.totals.vat' | translate }}</dt>
            <dd style="margin:0; text-align:right;">{{ q.totalVat | number:'1.2-2' }} €</dd>
            <dt style="font-weight:600; font-size:1.05rem;">{{ 'quotes.totals.totalTtc' | translate }}</dt>
            <dd style="margin:0; text-align:right; font-weight:600; font-size:1.05rem;">
              {{ q.totalInclVat | number:'1.2-2' }} €
            </dd>
          </dl>
        </div>

        <ng-container *ngIf="q.notes">
          <h3 style="margin:24px 0 8px;">{{ 'quotes.notesHeading' | translate }}</h3>
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
  private t = inject(TranslateService);

  quote = signal<Quote | null>(null);
  loading = signal(false);
  acting = signal(false);
  error = signal<string | null>(null);
  convertedInvoice = signal<{ id: string; number: string } | null>(null);
  lineColumns = ['description', 'quantity', 'unitPrice', 'vatRate', 'totalExclVat'];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set(this.t.instant('quotes.detail.missingId'));
      return;
    }
    this.loading.set(true);
    this.quotes.get(id).subscribe({
      next: q => {
        this.quote.set(q);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, this.t.instant('quotes.detail.notFound')));
        this.loading.set(false);
      }
    });
  }

  edit(): void {
    const q = this.quote();
    if (q) this.router.navigate(['/quotes', q.id, 'edit']);
  }

  send(): void {
    const q = this.quote();
    if (!q) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant('quotes.send.title'),
        message: this.t.instant('quotes.send.message'),
        confirmLabel: this.t.instant('quotes.send.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'primary'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.quotes.send(q.id).subscribe({
        next: updated => {
          this.acting.set(false);
          this.quote.set(updated);
          this.snack.open(this.t.instant('quotes.send.success'), this.t.instant('common.dismiss'), { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('quotes.send.failed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }

  markAccepted(): void {
    this.confirmAndTransition({
      next: 'ACCEPTED',
      titleKey: 'quotes.accept.title',
      messageKey: 'quotes.accept.message',
      confirmKey: 'quotes.accept.confirm',
      confirmColor: 'primary',
      successKey: 'quotes.accept.success'
    });
  }

  markRejected(): void {
    this.confirmAndTransition({
      next: 'REJECTED',
      titleKey: 'quotes.reject.title',
      messageKey: 'quotes.reject.message',
      confirmKey: 'quotes.reject.confirm',
      confirmColor: 'warn',
      successKey: 'quotes.reject.success'
    });
  }

  convert(): void {
    const q = this.quote();
    if (!q) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.t.instant('quotes.convert.title'),
        message: this.t.instant('quotes.convert.message'),
        confirmLabel: this.t.instant('quotes.convert.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'primary'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.quotes.convert(q.id).subscribe({
        next: invoice => {
          this.convertedInvoice.set(invoice);
          this.acting.set(false);
          this.snack.open(
            this.t.instant('quotes.convert.successInvoice', { number: invoice.number }),
            this.t.instant('common.dismiss'),
            { duration: 3000 }
          );
          this.router.navigate(['/invoices', invoice.id]);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('quotes.convert.failed')),
            this.t.instant('common.dismiss'),
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
        title: this.t.instant('quotes.delete.title'),
        message: this.t.instant('quotes.delete.message', { number: q.number }),
        confirmLabel: this.t.instant('quotes.delete.confirm'),
        cancelLabel: this.t.instant('common.cancel'),
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe(ok => {
      if (!ok) return;
      this.acting.set(true);
      this.quotes.remove(q.id).subscribe({
        next: () => {
          this.acting.set(false);
          this.snack.open(
            this.t.instant('quotes.delete.success', { number: q.number }),
            this.t.instant('common.dismiss'),
            { duration: 2500 }
          );
          this.router.navigate(['/quotes']);
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('quotes.delete.failed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }

  private confirmAndTransition(opts: {
    next: QuoteStatus;
    titleKey: string;
    messageKey: string;
    confirmKey: string;
    confirmColor: 'primary' | 'accent' | 'warn';
    successKey: string;
  }): void {
    const q = this.quote();
    if (!q) return;
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
      this.quotes.setStatus(q.id, opts.next).subscribe({
        next: updated => {
          this.acting.set(false);
          this.quote.set(updated);
          this.snack.open(this.t.instant(opts.successKey), this.t.instant('common.dismiss'), { duration: 2500 });
        },
        error: err => {
          this.acting.set(false);
          this.snack.open(
            extractErrorDetail(err, this.t.instant('quotes.statusUpdateFailed')),
            this.t.instant('common.dismiss'),
            { duration: 4000 }
          );
        }
      });
    });
  }
}
