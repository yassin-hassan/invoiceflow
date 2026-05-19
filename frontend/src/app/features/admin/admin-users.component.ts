import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AdminService, AdminUserListItem } from '../../core/services/admin.service';
import { AuthService } from '../../core/services/auth.service';
import { Role } from '../../models/user.model';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { extractErrorDetail } from '../../core/utils/http-errors';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe, FormsModule,
    MatTableModule, MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, MatInputModule, MatProgressSpinnerModule,
    MatChipsModule, MatDialogModule, MatSnackBarModule, MatTooltipModule,
    TranslateModule
  ],
  template: `
    <div>
      <h1 style="margin:0 0 16px;">{{ 'admin.users.title' | translate }}</h1>

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
        <mat-form-field appearance="outline" style="width:320px; margin-bottom:8px;">
          <mat-label>{{ 'common.search' | translate }}</mat-label>
          <input matInput [ngModel]="search()" (ngModelChange)="search.set($event)" />
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <table mat-table [dataSource]="visible()" style="width:100%; background:white;">
          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.email' | translate }}</th>
            <td mat-cell *matCellDef="let u">
              {{ u.email }}
              <span *ngIf="isSelf(u)" style="color:#666; font-size:0.8em;">({{ 'admin.users.you' | translate }})</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="role">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.role' | translate }}</th>
            <td mat-cell *matCellDef="let u">
              <mat-form-field appearance="outline" subscriptSizing="dynamic" style="width:120px;">
                <mat-select
                    [value]="u.role"
                    [disabled]="isSelf(u)"
                    [matTooltip]="isSelf(u) ? ('admin.users.cannotActOnSelf' | translate) : ''"
                    (selectionChange)="onRoleChange(u, $event.value)">
                  <mat-option value="USER">{{ 'admin.users.role.USER' | translate }}</mat-option>
                  <mat-option value="ADMIN">{{ 'admin.users.role.ADMIN' | translate }}</mat-option>
                </mat-select>
              </mat-form-field>
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.status' | translate }}</th>
            <td mat-cell *matCellDef="let u">
              <mat-chip [color]="u.active ? 'primary' : 'warn'" highlighted>
                {{ (u.active ? 'admin.users.status.active' : 'admin.users.status.inactive') | translate }}
              </mat-chip>
            </td>
          </ng-container>

          <ng-container matColumnDef="clientCount">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.clients' | translate }}</th>
            <td mat-cell *matCellDef="let u">{{ u.clientCount }}</td>
          </ng-container>

          <ng-container matColumnDef="invoiceCount">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.invoices' | translate }}</th>
            <td mat-cell *matCellDef="let u">{{ u.invoiceCount }}</td>
          </ng-container>

          <ng-container matColumnDef="totalRevenue">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.revenue' | translate }}</th>
            <td mat-cell *matCellDef="let u">{{ u.totalRevenue | number:'1.2-2' }}</td>
          </ng-container>

          <ng-container matColumnDef="lastLoginAt">
            <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.columns.lastLogin' | translate }}</th>
            <td mat-cell *matCellDef="let u">{{ u.lastLoginAt ? (u.lastLoginAt | date:'short') : '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef style="width:160px;"></th>
            <td mat-cell *matCellDef="let u">
              <button mat-icon-button
                      (click)="toggleStatus(u)"
                      [disabled]="isSelf(u)"
                      [matTooltip]="isSelf(u) ? ('admin.users.cannotActOnSelf' | translate) : ((u.active ? 'admin.users.actions.disable' : 'admin.users.actions.enable') | translate)">
                <mat-icon>{{ u.active ? 'block' : 'check_circle' }}</mat-icon>
              </button>
              <button mat-icon-button
                      (click)="sendPasswordReset(u)"
                      [matTooltip]="'admin.users.actions.sendPasswordReset' | translate">
                <mat-icon>lock_reset</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;"></tr>
        </table>
      </ng-container>
    </div>
  `
})
export class AdminUsersComponent implements OnInit {
  private adminService = inject(AdminService);
  private authService = inject(AuthService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private t = inject(TranslateService);

  users = signal<AdminUserListItem[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  search = signal('');

  visible = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) return this.users();
    return this.users().filter(u =>
      u.email.toLowerCase().includes(term) ||
      (u.firstName ?? '').toLowerCase().includes(term) ||
      (u.lastName ?? '').toLowerCase().includes(term) ||
      (u.companyName ?? '').toLowerCase().includes(term)
    );
  });

  columns = ['email', 'role', 'status', 'clientCount', 'invoiceCount', 'totalRevenue', 'lastLoginAt', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  isSelf(user: AdminUserListItem): boolean {
    return this.authService.currentUser()?.id === user.id;
  }

  toggleStatus(user: AdminUserListItem): void {
    const nextActive = !user.active;
    const data: ConfirmDialogData = {
      title: this.t.instant(nextActive ? 'admin.users.confirm.enableTitle' : 'admin.users.confirm.disableTitle'),
      message: this.t.instant(nextActive ? 'admin.users.confirm.enable' : 'admin.users.confirm.disable', { email: user.email }),
      confirmColor: nextActive ? 'primary' : 'warn'
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.adminService.updateStatus(user.id, nextActive).subscribe({
        next: updated => {
          this.users.update(list => list.map(u => u.id === user.id ? { ...u, active: updated.active } : u));
          this.snackBar.open(this.t.instant('admin.users.feedback.statusChanged'), this.t.instant('common.ok'), { duration: 3000 });
        },
        error: err => this.snackBar.open(extractErrorDetail(err, this.t.instant('common.errorGeneric')), this.t.instant('common.ok'), { duration: 4000 })
      });
    });
  }

  onRoleChange(user: AdminUserListItem, role: Role): void {
    if (role === user.role) return;
    const data: ConfirmDialogData = {
      title: this.t.instant('admin.users.confirm.roleTitle'),
      message: this.t.instant('admin.users.confirm.role', { email: user.email, role: this.t.instant('admin.users.role.' + role) }),
      confirmColor: 'primary'
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed().subscribe(confirmed => {
      if (!confirmed) {
        this.users.update(list => list.map(u => u.id === user.id ? { ...u } : u));
        return;
      }
      this.adminService.updateRole(user.id, role).subscribe({
        next: updated => {
          this.users.update(list => list.map(u => u.id === user.id ? { ...u, role: updated.role } : u));
          this.snackBar.open(this.t.instant('admin.users.feedback.roleChanged'), this.t.instant('common.ok'), { duration: 3000 });
        },
        error: err => {
          this.users.update(list => list.map(u => u.id === user.id ? { ...u } : u));
          this.snackBar.open(extractErrorDetail(err, this.t.instant('common.errorGeneric')), this.t.instant('common.ok'), { duration: 4000 });
        }
      });
    });
  }

  sendPasswordReset(user: AdminUserListItem): void {
    const data: ConfirmDialogData = {
      title: this.t.instant('admin.users.confirm.resetTitle'),
      message: this.t.instant('admin.users.confirm.reset', { email: user.email }),
      confirmColor: 'primary'
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.adminService.triggerPasswordReset(user.id).subscribe({
        next: () => this.snackBar.open(this.t.instant('admin.users.feedback.resetSent'), this.t.instant('common.ok'), { duration: 3000 }),
        error: err => this.snackBar.open(extractErrorDetail(err, this.t.instant('common.errorGeneric')), this.t.instant('common.ok'), { duration: 4000 })
      });
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminService.listUsers().subscribe({
      next: list => {
        this.users.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, this.t.instant('common.errorGeneric')));
        this.loading.set(false);
      }
    });
  }
}
