import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { extractErrorDetail } from '../../core/utils/http-errors';
import {
  Invoice, InvoiceStatus,
  CreateInvoiceRequest, UpdateInvoiceRequest, RecordPaymentRequest
} from './invoice.model';

const API = `${environment.apiUrl}/invoices`;

@Injectable({ providedIn: 'root' })
export class InvoicesService {
  private http = inject(HttpClient);

  invoices = signal<Invoice[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  loadAll(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Invoice[]>(API).subscribe({
      next: list => {
        this.invoices.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Failed to load invoices.'));
        this.loading.set(false);
      }
    });
  }

  get(id: string) {
    return this.http.get<Invoice>(`${API}/${id}`);
  }

  create(req: CreateInvoiceRequest) {
    return this.http.post<Invoice>(API, req);
  }

  update(id: string, req: UpdateInvoiceRequest) {
    return this.http.put<Invoice>(`${API}/${id}`, req);
  }

  setStatus(id: string, status: InvoiceStatus) {
    return this.http.patch<Invoice>(`${API}/${id}/status`, { status });
  }

  remove(id: string) {
    return this.http.delete<void>(`${API}/${id}`);
  }

  recordPayment(id: string, req: RecordPaymentRequest) {
    return this.http.post<Invoice>(`${API}/${id}/payments`, req);
  }
}
