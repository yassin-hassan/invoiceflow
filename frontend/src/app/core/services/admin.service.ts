import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Role } from '../../models/user.model';

const API = `${environment.apiUrl}/admin`;

export interface AdminUserListItem {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  companyName?: string;
  role: Role;
  active: boolean;
  emailVerified: boolean;
  twoFaEnabled: boolean;
  createdAt: string;
  lastLoginAt?: string;
  clientCount: number;
  invoiceCount: number;
  totalRevenue: number;
}

export interface AdminUserDetail extends AdminUserListItem {
  phone?: string;
  vatNumber?: string;
  preferredLanguage?: string;
}

export type AuditAction =
  | 'LOGIN_SUCCESS' | 'LOGIN_FAILED' | 'PASSWORD_RESET_REQUESTED' | 'PASSWORD_CHANGED'
  | 'TWO_FA_ENABLED' | 'TWO_FA_DISABLED'
  | 'INVOICE_SENT' | 'INVOICE_CANCELLED' | 'INVOICE_PAYMENT_RECORDED'
  | 'CREDIT_NOTE_ISSUED'
  | 'STRIPE_PAYMENT_CONFIRMED'
  | 'ADMIN_USER_STATUS_CHANGED' | 'ADMIN_USER_ROLE_CHANGED' | 'ADMIN_USER_PASSWORD_RESET_SENT'
  | 'ACCOUNT_DELETED';

export interface AuditLog {
  id: string;
  occurredAt: string;
  actorEmail?: string;
  action: AuditAction;
  resourceType?: string;
  resourceId?: string;
  ipAddress?: string;
  userAgent?: string;
  details?: Record<string, unknown>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditLogFilters {
  actor?: string;
  action?: AuditAction | '';
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);

  listUsers(): Observable<AdminUserListItem[]> {
    return this.http.get<AdminUserListItem[]>(`${API}/users`);
  }

  getUser(id: string): Observable<AdminUserDetail> {
    return this.http.get<AdminUserDetail>(`${API}/users/${id}`);
  }

  updateStatus(id: string, active: boolean): Observable<AdminUserDetail> {
    return this.http.patch<AdminUserDetail>(`${API}/users/${id}/status`, { active });
  }

  updateRole(id: string, role: Role): Observable<AdminUserDetail> {
    return this.http.patch<AdminUserDetail>(`${API}/users/${id}/role`, { role });
  }

  triggerPasswordReset(id: string): Observable<void> {
    return this.http.post<void>(`${API}/users/${id}/password-reset`, {});
  }

  listAuditLogs(filters: AuditLogFilters): Observable<Page<AuditLog>> {
    return this.http.get<Page<AuditLog>>(`${API}/audit-logs`, { params: toParams(filters) });
  }

  downloadAuditLogsCsv(filters: AuditLogFilters): Observable<Blob> {
    const { page, size, ...rest } = filters;
    return this.http.get(`${API}/audit-logs.csv`, {
      params: toParams(rest),
      responseType: 'blob'
    });
  }
}

function toParams(filters: AuditLogFilters): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value === undefined || value === null || value === '') continue;
    params = params.set(key, String(value));
  }
  return params;
}
