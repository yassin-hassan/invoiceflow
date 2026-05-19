import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UserDataExportService {
  private http = inject(HttpClient);

  downloadDataExport(): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/users/me/data-export`, {
      responseType: 'blob'
    });
  }
}
