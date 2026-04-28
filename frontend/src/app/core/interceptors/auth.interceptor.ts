import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

const PUBLIC_PATHS = [
  { method: 'POST', path: '/api/auth/login' },
  { method: 'POST', path: '/api/auth/2fa/verify' },
  { method: 'GET',  path: '/api/auth/verify' },
  { method: 'POST', path: '/api/auth/resend-verification' },
  { method: 'POST', path: '/api/auth/forgot-password' },
  { method: 'POST', path: '/api/auth/reset-password' },
  { method: 'POST', path: '/api/users' }
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const isPublic = PUBLIC_PATHS.some(p =>
    req.method === p.method && new URL(req.url, window.location.origin).pathname === p.path
  );
  if (isPublic) return next(req);

  const token = inject(AuthService).getToken();
  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }
  return next(req);
};
