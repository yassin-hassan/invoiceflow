import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AdminService, AuditAction, AuditLog, AuditLogFilters, Page } from '../../core/services/admin.service';
import { extractErrorDetail } from '../../core/utils/http-errors';

const AUDIT_ACTIONS: AuditAction[] = [
  'LOGIN_SUCCESS', 'LOGIN_FAILED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_CHANGED',
  'TWO_FA_ENABLED', 'TWO_FA_DISABLED',
  'INVOICE_SENT', 'INVOICE_CANCELLED', 'INVOICE_PAYMENT_RECORDED',
  'CREDIT_NOTE_ISSUED', 'STRIPE_PAYMENT_CONFIRMED',
  'ADMIN_USER_STATUS_CHANGED', 'ADMIN_USER_ROLE_CHANGED', 'ADMIN_USER_PASSWORD_RESET_SENT',
  'ACCOUNT_DELETED'
];

@Component({
  selector: 'app-admin-audit-logs',
  standalone: true,
  imports: [
    CommonModule, DatePipe, FormsModule,
    MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatNativeDateModule,
    MatProgressSpinnerModule, MatChipsModule, MatSnackBarModule,
    TranslateModule
  ],
  template: `
    <div>
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <h1 style="margin:0;">{{ 'admin.audit.title' | translate }}</h1>
        <button mat-raised-button color="primary" (click)="exportCsv()" [disabled]="exporting()">
          <mat-icon>download</mat-icon> {{ 'admin.audit.export' | translate }}
        </button>
      </div>

      <div style="display:flex; gap:12px; flex-wrap:wrap; margin-bottom:16px;">
        <mat-form-field appearance="outline" subscriptSizing="dynamic" style="width:240px;">
          <mat-label>{{ 'admin.audit.filters.actor' | translate }}</mat-label>
          <input matInput [ngModel]="actor()" (ngModelChange)="actor.set($event)" (keyup.enter)="applyFilters()" />
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic" style="width:240px;">
          <mat-label>{{ 'admin.audit.filters.action' | translate }}</mat-label>
          <mat-select [ngModel]="action()" (ngModelChange)="action.set($event)">
            <mat-option [value]="''">{{ 'admin.audit.filters.actionAll' | translate }}</mat-option>
            <mat-option *ngFor="let a of actions" [value]="a">{{ 'admin.audit.action.' + a | translate }}</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic" style="width:180px;">
          <mat-label>{{ 'admin.audit.filters.from' | translate }}</mat-label>
          <input matInput [matDatepicker]="fromPicker" [ngModel]="from()" (ngModelChange)="from.set($event)" />
          <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>
          <mat-datepicker #fromPicker></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic" style="width:180px;">
          <mat-label>{{ 'admin.audit.filters.to' | translate }}</mat-label>
          <input matInput [matDatepicker]="toPicker" [ngModel]="to()" (ngModelChange)="to.set($event)" />
          <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>
          <mat-datepicker #toPicker></mat-datepicker>
        </mat-form-field>

        <button mat-raised-button color="primary" (click)="applyFilters()">
          <mat-icon>filter_alt</mat-icon> {{ 'admin.audit.filters.apply' | translate }}
        </button>
        <button mat-button (click)="resetFilters()">{{ 'admin.audit.filters.reset' | translate }}</button>
      </div>

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

      <ng-container *ngIf="!loading() && !error()">
        <table mat-table [dataSource]="page()?.content ?? []" style="width:100%; background:white;">
          <ng-container matColumnDef="occurredAt">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.columns.time' | translate }}</th>
            <td mat-cell *matCellDef="let r">{{ r.occurredAt | date:'short' }}</td>
          </ng-container>

          <ng-container matColumnDef="actor">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.columns.actor' | translate }}</th>
            <td mat-cell *matCellDef="let r">{{ r.actorEmail || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="action">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.columns.action' | translate }}</th>
            <td mat-cell *matCellDef="let r">
              <mat-chip>{{ 'admin.audit.action.' + r.action | translate }}</mat-chip>
            </td>
          </ng-container>

          <ng-container matColumnDef="resource">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.columns.resource' | translate }}</th>
            <td mat-cell *matCellDef="let r">
              <span *ngIf="r.resourceType">{{ r.resourceType }}</span>
              <span *ngIf="r.resourceId" style="color:#666; font-size:0.85em;"> {{ r.resourceId }}</span>
              <span *ngIf="!r.resourceType && !r.resourceId">—</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="ip">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.columns.ip' | translate }}</th>
            <td mat-cell *matCellDef="let r">{{ r.ipAddress || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="details">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.columns.details' | translate }}</th>
            <td mat-cell *matCellDef="let r">
              <code *ngIf="r.details" style="font-size:0.8em; white-space:pre-wrap; word-break:break-all;">{{ formatDetails(r.details) }}</code>
              <span *ngIf="!r.details">—</span>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>

        <mat-paginator
            [length]="page()?.totalElements ?? 0"
            [pageSize]="size()"
            [pageIndex]="pageIndex()"
            [pageSizeOptions]="[25, 50, 100]"
            (page)="onPage($event)">
        </mat-paginator>
      </ng-container>
    </div>
  `
})
export class AdminAuditLogsComponent implements OnInit {
  private adminService = inject(AdminService);
  private snackBar = inject(MatSnackBar);
  private t = inject(TranslateService);

  page = signal<Page<AuditLog> | null>(null);
  loading = signal(false);
  exporting = signal(false);
  error = signal<string | null>(null);

  actor = signal('');
  action = signal<AuditAction | ''>('');
  from = signal<Date | null>(null);
  to = signal<Date | null>(null);
  pageIndex = signal(0);
  size = signal(50);

  actions = AUDIT_ACTIONS;
  columns = ['occurredAt', 'actor', 'action', 'resource', 'ip', 'details'];

  ngOnInit(): void {
    this.load();
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.load();
  }

  resetFilters(): void {
    this.actor.set('');
    this.action.set('');
    this.from.set(null);
    this.to.set(null);
    this.pageIndex.set(0);
    this.load();
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.size.set(event.pageSize);
    this.load();
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.adminService.downloadAuditLogsCsv(this.buildFilters()).subscribe({
      next: blob => {
        this.exporting.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
        a.href = url;
        a.download = `audit-logs-${stamp}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: err => {
        this.exporting.set(false);
        this.snackBar.open(extractErrorDetail(err, this.t.instant('common.errorGeneric')), this.t.instant('common.ok'), { duration: 4000 });
      }
    });
  }

  formatDetails(details: Record<string, unknown>): string {
    try {
      return JSON.stringify(details);
    } catch {
      return '';
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const filters: AuditLogFilters = {
      ...this.buildFilters(),
      page: this.pageIndex(),
      size: this.size()
    };
    this.adminService.listAuditLogs(filters).subscribe({
      next: page => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, this.t.instant('common.errorGeneric')));
        this.loading.set(false);
      }
    });
  }

  private buildFilters(): AuditLogFilters {
    return {
      actor: this.actor() || undefined,
      action: this.action() || undefined,
      from: this.from() ? this.from()!.toISOString() : undefined,
      to: this.to() ? this.toEndOfDay(this.to()!) : undefined
    };
  }

  private toEndOfDay(date: Date): string {
    const end = new Date(date);
    end.setHours(23, 59, 59, 999);
    return end.toISOString();
  }
}
