import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { extractErrorDetail } from '../../core/utils/http-errors';
import { Client, CreateClientRequest, UpdateClientRequest } from './client.model';

const API = `${environment.apiUrl}/clients`;

@Injectable({ providedIn: 'root' })
export class ClientsService {
  private http = inject(HttpClient);

  clients = signal<Client[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  loadAll(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Client[]>(API).subscribe({
      next: list => {
        this.clients.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Failed to load clients.'));
        this.loading.set(false);
      }
    });
  }

  create(req: CreateClientRequest) {
    return this.http.post<Client>(API, req);
  }

  update(id: string, req: UpdateClientRequest) {
    return this.http.put<Client>(`${API}/${id}`, req);
  }

  archive(id: string) {
    return this.http.delete<{ mode: 'DELETED' | 'ARCHIVED' }>(`${API}/${id}`);
  }
}
