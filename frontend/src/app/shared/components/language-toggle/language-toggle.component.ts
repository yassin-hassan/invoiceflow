import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { AppLanguage, LanguageService } from '../../../core/services/language.service';

@Component({
  selector: 'app-language-toggle',
  standalone: true,
  imports: [MatButtonModule, MatMenuModule, MatIconModule, TranslateModule],
  template: `
    <button mat-button [matMenuTriggerFor]="menu" type="button">
      <mat-icon>language</mat-icon>
      {{ label() }}
    </button>
    <mat-menu #menu="matMenu">
      <button mat-menu-item (click)="select('fr')" [disabled]="lang.current() === 'fr'">
        🇧🇪 {{ 'language.fr' | translate }}
      </button>
      <button mat-menu-item (click)="select('en')" [disabled]="lang.current() === 'en'">
        🇬🇧 {{ 'language.en' | translate }}
      </button>
    </mat-menu>
  `
})
export class LanguageToggleComponent {
  lang = inject(LanguageService);

  label(): string {
    return this.lang.current().toUpperCase();
  }

  select(target: AppLanguage): void {
    this.lang.set(target);
  }
}
