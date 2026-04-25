import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatIconModule, MatButtonModule
  ],
  template: `
    <mat-sidenav-container style="height: 100vh;">
      <mat-sidenav mode="side" opened style="width: 240px;">
        <div style="padding: 16px; font-size: 1.2rem; font-weight: 600; border-bottom: 1px solid #eee;">
          InvoiceFlow
        </div>
        <mat-nav-list>
          <a mat-list-item routerLink="/dashboard" routerLinkActive="active-link">
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Dashboard</span>
          </a>
          <a mat-list-item routerLink="/clients" routerLinkActive="active-link">
            <mat-icon matListItemIcon>people</mat-icon>
            <span matListItemTitle>Clients</span>
          </a>
          <a mat-list-item routerLink="/products" routerLinkActive="active-link">
            <mat-icon matListItemIcon>inventory_2</mat-icon>
            <span matListItemTitle>Products</span>
          </a>
          <a mat-list-item routerLink="/quotes" routerLinkActive="active-link">
            <mat-icon matListItemIcon>description</mat-icon>
            <span matListItemTitle>Quotes</span>
          </a>
          <a mat-list-item routerLink="/invoices" routerLinkActive="active-link">
            <mat-icon matListItemIcon>receipt</mat-icon>
            <span matListItemTitle>Invoices</span>
          </a>
        </mat-nav-list>
        <div style="position: absolute; bottom: 0; width: 100%; padding: 8px;">
          <button mat-button style="width: 100%;" (click)="auth.logout()">
            <mat-icon>logout</mat-icon> Logout
          </button>
        </div>
      </mat-sidenav>
      <mat-sidenav-content>
        <mat-toolbar color="primary">
          <span style="flex: 1;"></span>
        </mat-toolbar>
        <div style="padding: 24px;">
          <router-outlet />
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .active-link { background: rgba(0,0,0,0.08); }
  `]
})
export class LayoutComponent {
  constructor(public auth: AuthService) {}
}
