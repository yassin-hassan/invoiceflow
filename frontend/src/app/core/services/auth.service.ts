import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { User } from '../../models/user.model';

const API = environment.apiUrl;
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

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  isLoggedIn = signal<boolean>(this.hasToken());
  currentUser = signal<User | null>(null);
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  constructor(private http: HttpClient, private router: Router) {
    if (this.isLoggedIn()) this.refreshCurrentUser().subscribe();
  }

  refreshCurrentUser(): Observable<User | null> {
    return this.http.get<User>(`${API}/users/me`).pipe(
      tap(user => this.currentUser.set(user)),
      catchError(() => {
        this.currentUser.set(null);
        return of(null);
      })
    );
  }

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

  register(data: RegisterRequest): Observable<unknown> {
    return this.http.post(`${API}/users`, data);
  }

  verifyEmail(token: string): Observable<unknown> {
    return this.http.get(`${API}/auth/verify`, { params: { token }, responseType: 'text' });
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
    this.refreshCurrentUser().subscribe();
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.isLoggedIn.set(false);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }
}
