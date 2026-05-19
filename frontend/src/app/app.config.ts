import {
  ApplicationConfig,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
  inject
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFrBe from '@angular/common/locales/fr-BE';
import localeEnGb from '@angular/common/locales/en-GB';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { LanguageService } from './core/services/language.service';
import { routes } from './app.routes';

registerLocaleData(localeFrBe);
registerLocaleData(localeEnGb);

function readInitialLocale(): string {
  const stored = (typeof localStorage !== 'undefined' && localStorage.getItem('app_language')) || '';
  if (stored === 'fr') return 'fr-BE';
  if (stored === 'en') return 'en-GB';
  const browser = (typeof navigator !== 'undefined' ? navigator.language : '').toLowerCase();
  return browser.startsWith('fr') ? 'fr-BE' : 'en-GB';
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideTranslateService({ fallbackLang: 'fr' }),
    provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
    { provide: LOCALE_ID, useFactory: readInitialLocale },
    provideAppInitializer(() => inject(LanguageService).init())
  ]
};
