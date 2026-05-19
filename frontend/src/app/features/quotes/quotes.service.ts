import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { extractErrorDetail } from '../../core/utils/http-errors';
import {
  Quote, QuoteStatus,
  CreateQuoteRequest, UpdateQuoteRequest
} from './quote.model';

const API = `${environment.apiUrl}/quotes`;

@Injectable({ providedIn: 'root' })
export class QuotesService {
  private http = inject(HttpClient);

  quotes = signal<Quote[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  loadAll(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Quote[]>(API).subscribe({
      next: list => {
        this.quotes.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Failed to load quotes.'));
        this.loading.set(false);
      }
    });
  }

  get(id: string) {
    return this.http.get<Quote>(`${API}/${id}`);
  }

  create(req: CreateQuoteRequest) {
    return this.http.post<Quote>(API, req);
  }

  update(id: string, req: UpdateQuoteRequest) {
    return this.http.put<Quote>(`${API}/${id}`, req);
  }

  setStatus(id: string, status: QuoteStatus) {
    return this.http.patch<Quote>(`${API}/${id}/status`, { status });
  }

  send(id: string) {
    return this.http.post<Quote>(`${API}/${id}/send`, {});
  }

  remove(id: string) {
    return this.http.delete<void>(`${API}/${id}`);
  }

  convert(id: string) {
    return this.http.post<{ id: string; number: string }>(`${API}/${id}/convert`, {});
  }
}
