import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InvoiceStatus } from '../../features/invoices/invoice.model';

export interface AccountingExportFilters {
  from?: string;
  to?: string;
  status?: InvoiceStatus;
}

@Injectable({ providedIn: 'root' })
export class ExportsService {
  private http = inject(HttpClient);

  downloadAccountingXlsx(filters: AccountingExportFilters): Observable<Blob> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value === undefined || value === null || value === '') continue;
      params = params.set(key, String(value));
    }
    return this.http.get(`${environment.apiUrl}/exports/accounting.xlsx`, {
      params,
      responseType: 'blob'
    });
  }
}
