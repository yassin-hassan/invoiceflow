import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { extractErrorDetail } from '../../core/utils/http-errors';
import {
  CreditNote, CreateCreditNoteRequest, UpdateCreditNoteRequest
} from './credit-note.model';

const API = `${environment.apiUrl}/credit-notes`;

@Injectable({ providedIn: 'root' })
export class CreditNotesService {
  private http = inject(HttpClient);

  creditNotes = signal<CreditNote[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  loadAll(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<CreditNote[]>(API).subscribe({
      next: list => {
        this.creditNotes.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Failed to load credit notes.'));
        this.loading.set(false);
      }
    });
  }

  get(id: string) {
    return this.http.get<CreditNote>(`${API}/${id}`);
  }

  createForInvoice(invoiceId: string, req: CreateCreditNoteRequest) {
    return this.http.post<CreditNote>(
      `${environment.apiUrl}/invoices/${invoiceId}/credit-notes`, req);
  }

  update(id: string, req: UpdateCreditNoteRequest) {
    return this.http.put<CreditNote>(`${API}/${id}`, req);
  }

  issue(id: string) {
    return this.http.post<CreditNote>(`${API}/${id}/issue`, {});
  }

  remove(id: string) {
    return this.http.delete<void>(`${API}/${id}`);
  }

  downloadPdf(id: string) {
    return this.http.get(`${API}/${id}/pdf`, { responseType: 'blob' });
  }
}
