import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AccountDeletionService {
  private http = inject(HttpClient);

  deleteAccount(currentPassword: string): Observable<void> {
    return this.http.request<void>('delete', `${environment.apiUrl}/users/me`, {
      body: { currentPassword }
    });
  }
}
