import { HttpErrorResponse } from '@angular/common/http';

export function extractErrorDetail(err: HttpErrorResponse, fallback: string): string {
  const body = err?.error;
  if (typeof body === 'string') {
    try { return JSON.parse(body)?.detail ?? fallback; } catch { return fallback; }
  }
  return body?.detail ?? fallback;
}

export function extractFieldErrors(err: HttpErrorResponse): Record<string, string> | null {
  const body = err?.error;
  return err?.status === 400 && body && typeof body === 'object' && body.errors ? body.errors : null;
}
