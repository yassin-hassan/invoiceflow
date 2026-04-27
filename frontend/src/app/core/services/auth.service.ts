import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

const API = 'http://localhost:8080/api';
const TOKEN_KEY = 'auth_token';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token?: string;
  requires2fa?: boolean;
}

export interface TwoFactorRequest {
  email: string;
  code: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  isLoggedIn = signal<boolean>(this.hasToken());

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API}/auth/login`, request);
  }

  verifyTwoFactor(request: TwoFactorRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API}/auth/2fa/verify`, request).pipe(
      tap(res => {
        if (res.token) this.saveToken(res.token);
      })
    );
  }

  register(data: any): Observable<unknown> {
    return this.http.post(`${API}/users`, data);
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.get<void>(`${API}/auth/verify`, { params: { token } });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${API}/auth/forgot-password`, { email });
  }

  resendVerification(email: string): Observable<void> {
    return this.http.post<void>(`${API}/auth/resend-verification`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API}/auth/reset-password`, { token, newPassword });
  }

  saveToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.isLoggedIn.set(true);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.isLoggedIn.set(false);
    this.router.navigate(['/login']);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }
}
