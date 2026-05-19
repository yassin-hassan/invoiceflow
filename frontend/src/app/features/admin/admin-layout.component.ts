import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive, RouterOutlet,
    MatTabsModule, MatIconModule, TranslateModule
  ],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="adminTabPanel" style="margin-bottom:24px;">
      <a mat-tab-link routerLink="/admin/users" routerLinkActive #usersLink="routerLinkActive" [active]="usersLink.isActive">
        <mat-icon style="margin-right:6px;">people</mat-icon>
        {{ 'admin.nav.users' | translate }}
      </a>
      <a mat-tab-link routerLink="/admin/audit-logs" routerLinkActive #auditLink="routerLinkActive" [active]="auditLink.isActive">
        <mat-icon style="margin-right:6px;">fact_check</mat-icon>
        {{ 'admin.nav.auditLogs' | translate }}
      </a>
    </nav>
    <mat-tab-nav-panel #adminTabPanel>
      <router-outlet />
    </mat-tab-nav-panel>
  `
})
export class AdminLayoutComponent {}
