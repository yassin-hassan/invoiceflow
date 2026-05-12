import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { extractErrorDetail } from '../../core/utils/http-errors';
import { DashboardResponse } from './dashboard.model';

const API = `${environment.apiUrl}/dashboard`;

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);

  data = signal<DashboardResponse | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<DashboardResponse>(API).subscribe({
      next: res => {
        this.data.set(res);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Failed to load dashboard.'));
        this.loading.set(false);
      }
    });
  }
}
