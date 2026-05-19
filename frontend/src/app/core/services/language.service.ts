import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export type AppLanguage = 'fr' | 'en';

const STORAGE_KEY = 'app_language';
const SUPPORTED: AppLanguage[] = ['fr', 'en'];
const DEFAULT: AppLanguage = 'fr';

export const LOCALE_BY_LANG: Record<AppLanguage, string> = {
  fr: 'fr-BE',
  en: 'en-GB'
};

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private translate = inject(TranslateService);
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  readonly current = signal<AppLanguage>(this.resolveInitial());

  init(): void {
    const lang = this.current();
    this.translate.addLangs(SUPPORTED);
    this.translate.setFallbackLang(DEFAULT);
    this.translate.use(lang);
  }

  set(lang: AppLanguage): void {
    if (lang === this.current()) return;
    localStorage.setItem(STORAGE_KEY, lang);
    this.current.set(lang);
    const reload = () => window.location.reload();
    if (this.auth.isLoggedIn()) {
      this.http.patch(`${environment.apiUrl}/users/me/language`, { language: lang.toUpperCase() })
        .subscribe({ next: reload, error: reload });
    } else {
      reload();
    }
  }

  locale(): string {
    return LOCALE_BY_LANG[this.current()];
  }

  private resolveInitial(): AppLanguage {
    const stored = localStorage.getItem(STORAGE_KEY) as AppLanguage | null;
    if (stored && SUPPORTED.includes(stored)) return stored;
    const browser = (navigator.language || '').toLowerCase();
    return browser.startsWith('fr') ? 'fr' : 'en';
  }
}
