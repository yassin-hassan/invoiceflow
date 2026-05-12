import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { DashboardService } from './dashboard.service';
import { DashboardResponse, InvoiceSummary } from './dashboard.model';
import { InvoiceStatusChipComponent } from '../invoices/invoice-status-chip.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe,
    MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule, MatTableModule,
    InvoiceStatusChipComponent
  ],
  template: `
    <div style="padding:24px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">Dashboard</h1>
        <button mat-stroked-button (click)="service.load()" [disabled]="service.loading()">
          <mat-icon>refresh</mat-icon> Refresh
        </button>
      </div>

      <ng-container *ngIf="service.loading()">
        <div style="display:flex; justify-content:center; padding:48px;">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      </ng-container>

      <ng-container *ngIf="!service.loading() && service.error()">
        <div style="padding:16px; background:#fdecea; color:#b71c1c; border-radius:4px;">
          {{ service.error() }}
        </div>
      </ng-container>

      <ng-container *ngIf="!service.loading() && !service.error() && service.data() as d">
        <ng-container *ngIf="isEmptyAccount(d); else dashboardContent">
          <mat-card style="margin-top:24px;">
            <mat-card-content style="text-align:center; padding:48px 24px;">
              <mat-icon style="font-size:64px; width:64px; height:64px; color:#1976d2;">rocket_launch</mat-icon>
              <h2 style="margin:16px 0 8px;">Welcome to InvoiceFlow</h2>
              <p style="color:#666; max-width:480px; margin:0 auto 24px;">
                You don't have any data yet. Start by adding a client, then create your first quote or invoice.
              </p>
              <div style="display:flex; gap:12px; justify-content:center; flex-wrap:wrap;">
                <button mat-raised-button color="primary" (click)="newClient()">
                  <mat-icon>person_add</mat-icon> Add a client
                </button>
                <button mat-stroked-button (click)="newQuote()">
                  <mat-icon>request_quote</mat-icon> New quote
                </button>
                <button mat-stroked-button (click)="newInvoice()">
                  <mat-icon>receipt_long</mat-icon> New invoice
                </button>
              </div>
            </mat-card-content>
          </mat-card>
        </ng-container>

        <ng-template #dashboardContent>
        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:16px;">
          <mat-card style="background:#e8f5e9;">
            <mat-card-content>
              <div style="display:flex; align-items:center; gap:8px; color:#1b5e20;">
                <mat-icon>payments</mat-icon>
                <span style="font-size:0.9rem; text-transform:uppercase; letter-spacing:0.5px;">Revenue</span>
              </div>
              <div style="font-size:1.8rem; font-weight:600; margin-top:8px;">
                {{ d.totals.revenue | number:'1.2-2' }} €
              </div>
              <div style="color:#555; font-size:0.85rem; margin-top:4px;">
                {{ d.counts.paidInvoices }} paid invoice(s)
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card style="background:#e3f2fd;">
            <mat-card-content>
              <div style="display:flex; align-items:center; gap:8px; color:#0d47a1;">
                <mat-icon>hourglass_top</mat-icon>
                <span style="font-size:0.9rem; text-transform:uppercase; letter-spacing:0.5px;">Outstanding</span>
              </div>
              <div style="font-size:1.8rem; font-weight:600; margin-top:8px;">
                {{ d.totals.outstanding | number:'1.2-2' }} €
              </div>
              <div style="color:#555; font-size:0.85rem; margin-top:4px;">
                {{ d.counts.sentInvoices + d.counts.overdueInvoices }} open invoice(s)
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card [style.background]="d.totals.overdue > 0 ? '#fdecea' : '#fafafa'">
            <mat-card-content>
              <div style="display:flex; align-items:center; gap:8px;"
                   [style.color]="d.totals.overdue > 0 ? '#b71c1c' : '#666'">
                <mat-icon>warning</mat-icon>
                <span style="font-size:0.9rem; text-transform:uppercase; letter-spacing:0.5px;">Overdue</span>
              </div>
              <div style="font-size:1.8rem; font-weight:600; margin-top:8px;"
                   [style.color]="d.totals.overdue > 0 ? '#b71c1c' : '#222'">
                {{ d.totals.overdue | number:'1.2-2' }} €
              </div>
              <div style="color:#555; font-size:0.85rem; margin-top:4px;">
                {{ d.counts.overdueInvoices }} overdue invoice(s)
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card style="background:#f3e5f5;">
            <mat-card-content>
              <div style="display:flex; align-items:center; gap:8px; color:#4a148c;">
                <mat-icon>group</mat-icon>
                <span style="font-size:0.9rem; text-transform:uppercase; letter-spacing:0.5px;">Clients</span>
              </div>
              <div style="font-size:1.8rem; font-weight:600; margin-top:8px;">
                {{ d.counts.clients }}
              </div>
              <div style="color:#555; font-size:0.85rem; margin-top:4px;">
                {{ d.counts.openQuotes }} open quote(s)
              </div>
            </mat-card-content>
          </mat-card>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(360px, 1fr)); gap:16px; margin-top:24px;">
          <mat-card>
            <mat-card-content>
              <h3 style="margin:0 0 12px;">Recent invoices</h3>
              <ng-container *ngIf="d.recentInvoices.length === 0; else recentInvoicesTable">
                <div style="padding:16px; color:#777; background:#fafafa; border-radius:4px;">
                  No invoices yet.
                </div>
              </ng-container>
              <ng-template #recentInvoicesTable>
                <table mat-table [dataSource]="d.recentInvoices" style="width:100%;">
                  <ng-container matColumnDef="number">
                    <th mat-header-cell *matHeaderCellDef>Number</th>
                    <td mat-cell *matCellDef="let inv">
                      <span *ngIf="inv.number; else recDraft">{{ inv.number }}</span>
                      <ng-template #recDraft><span style="color:#999; font-style:italic;">— Draft —</span></ng-template>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="client">
                    <th mat-header-cell *matHeaderCellDef>Client</th>
                    <td mat-cell *matCellDef="let inv">{{ inv.clientName }}</td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let inv">
                      <app-invoice-status-chip [status]="inv.status"></app-invoice-status-chip>
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="amountDue">
                    <th mat-header-cell *matHeaderCellDef style="text-align:right;">Amount due</th>
                    <td mat-cell *matCellDef="let inv" style="text-align:right;"
                        [style.color]="inv.amountDue > 0 ? '#b71c1c' : '#1b5e20'">
                      {{ inv.amountDue | number:'1.2-2' }} €
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="recentInvoiceColumns"></tr>
                  <tr mat-row
                      *matRowDef="let row; columns: recentInvoiceColumns;"
                      (click)="openInvoice(row)"
                      [style.cursor]="'pointer'"></tr>
                </table>
              </ng-template>
            </mat-card-content>
          </mat-card>

          <mat-card>
            <mat-card-content>
              <h3 style="margin:0 0 12px;">Recent payments</h3>
              <ng-container *ngIf="d.recentPayments.length === 0; else recentPaymentsTable">
                <div style="padding:16px; color:#777; background:#fafafa; border-radius:4px;">
                  No payments recorded yet.
                </div>
              </ng-container>
              <ng-template #recentPaymentsTable>
                <table mat-table [dataSource]="d.recentPayments" style="width:100%;">
                  <ng-container matColumnDef="paidAt">
                    <th mat-header-cell *matHeaderCellDef>Date</th>
                    <td mat-cell *matCellDef="let p">{{ p.paidAt | date:'mediumDate' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="invoice">
                    <th mat-header-cell *matHeaderCellDef>Invoice</th>
                    <td mat-cell *matCellDef="let p">{{ p.invoiceNumber }}</td>
                  </ng-container>
                  <ng-container matColumnDef="client">
                    <th mat-header-cell *matHeaderCellDef>Client</th>
                    <td mat-cell *matCellDef="let p">{{ p.clientName }}</td>
                  </ng-container>
                  <ng-container matColumnDef="amount">
                    <th mat-header-cell *matHeaderCellDef style="text-align:right;">Amount</th>
                    <td mat-cell *matCellDef="let p" style="text-align:right; color:#1b5e20;">
                      {{ p.amount | number:'1.2-2' }} €
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="recentPaymentColumns"></tr>
                  <tr mat-row
                      *matRowDef="let row; columns: recentPaymentColumns;"
                      (click)="openInvoiceById(row.invoiceId)"
                      [style.cursor]="'pointer'"></tr>
                </table>
              </ng-template>
            </mat-card-content>
          </mat-card>
        </div>

        <mat-card *ngIf="d.topOverdue.length > 0" style="margin-top:24px; border-left:4px solid #b71c1c;">
          <mat-card-content>
            <h3 style="margin:0 0 12px; color:#b71c1c; display:flex; align-items:center; gap:8px;">
              <mat-icon>warning</mat-icon> Top overdue invoices
            </h3>
            <table mat-table [dataSource]="d.topOverdue" style="width:100%;">
              <ng-container matColumnDef="number">
                <th mat-header-cell *matHeaderCellDef>Number</th>
                <td mat-cell *matCellDef="let inv">{{ inv.number }}</td>
              </ng-container>
              <ng-container matColumnDef="client">
                <th mat-header-cell *matHeaderCellDef>Client</th>
                <td mat-cell *matCellDef="let inv">{{ inv.clientName }}</td>
              </ng-container>
              <ng-container matColumnDef="dueDate">
                <th mat-header-cell *matHeaderCellDef>Due date</th>
                <td mat-cell *matCellDef="let inv">{{ inv.dueDate | date:'mediumDate' }}</td>
              </ng-container>
              <ng-container matColumnDef="daysOverdue">
                <th mat-header-cell *matHeaderCellDef style="text-align:right;">Days late</th>
                <td mat-cell *matCellDef="let inv" style="text-align:right; color:#b71c1c; font-weight:500;">
                  {{ daysOverdue(inv.dueDate) }}
                </td>
              </ng-container>
              <ng-container matColumnDef="amountDue">
                <th mat-header-cell *matHeaderCellDef style="text-align:right;">Amount due</th>
                <td mat-cell *matCellDef="let inv" style="text-align:right; color:#b71c1c; font-weight:500;">
                  {{ inv.amountDue | number:'1.2-2' }} €
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="overdueColumns"></tr>
              <tr mat-row
                  *matRowDef="let row; columns: overdueColumns;"
                  (click)="openInvoice(row)"
                  [style.cursor]="'pointer'"></tr>
            </table>
          </mat-card-content>
        </mat-card>

        <div style="display:flex; gap:12px; flex-wrap:wrap; margin-top:24px;">
          <button mat-raised-button color="primary" (click)="newInvoice()">
            <mat-icon>add</mat-icon> New invoice
          </button>
          <button mat-stroked-button (click)="newQuote()">
            <mat-icon>request_quote</mat-icon> New quote
          </button>
        </div>
        </ng-template>
      </ng-container>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  service = inject(DashboardService);
  private router = inject(Router);

  recentInvoiceColumns = ['number', 'client', 'status', 'amountDue'];
  recentPaymentColumns = ['paidAt', 'invoice', 'client', 'amount'];
  overdueColumns = ['number', 'client', 'dueDate', 'daysOverdue', 'amountDue'];

  ngOnInit(): void {
    this.service.load();
  }

  openInvoice(inv: InvoiceSummary): void {
    this.router.navigate(['/invoices', inv.id]);
  }

  openInvoiceById(id: string): void {
    this.router.navigate(['/invoices', id]);
  }

  newInvoice(): void {
    this.router.navigate(['/invoices/new']);
  }

  newQuote(): void {
    this.router.navigate(['/quotes/new']);
  }

  newClient(): void {
    this.router.navigate(['/clients']);
  }

  isEmptyAccount(d: DashboardResponse): boolean {
    const c = d.counts;
    return c.clients === 0
      && c.draftInvoices === 0
      && c.sentInvoices === 0
      && c.overdueInvoices === 0
      && c.paidInvoices === 0
      && c.openQuotes === 0;
  }

  daysOverdue(dueDate: string): number {
    const due = new Date(dueDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    due.setHours(0, 0, 0, 0);
    return Math.max(0, Math.round((today.getTime() - due.getTime()) / 86_400_000));
  }
}
