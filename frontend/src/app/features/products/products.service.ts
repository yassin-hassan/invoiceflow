import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { extractErrorDetail } from '../../core/utils/http-errors';
import { Product, CreateProductRequest, UpdateProductRequest } from './product.model';

const API = `${environment.apiUrl}/products`;

@Injectable({ providedIn: 'root' })
export class ProductsService {
  private http = inject(HttpClient);

  products = signal<Product[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  loadAll(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Product[]>(API).subscribe({
      next: list => {
        this.products.set(list);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(extractErrorDetail(err, 'Failed to load products.'));
        this.loading.set(false);
      }
    });
  }

  create(req: CreateProductRequest) {
    return this.http.post<Product>(API, req);
  }

  update(id: string, req: UpdateProductRequest) {
    return this.http.put<Product>(`${API}/${id}`, req);
  }

  remove(id: string) {
    return this.http.delete<{ mode: 'DELETED' | 'ARCHIVED' }>(`${API}/${id}`);
  }
}
