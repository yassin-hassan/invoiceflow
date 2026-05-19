import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'verify',
    loadComponent: () => import('./features/auth/verify/verify.component').then(m => m.VerifyComponent)
  },
  {
    path: '',
    loadComponent: () => import('./shared/components/layout/layout.component').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'clients',
        loadComponent: () => import('./features/clients/clients.component').then(m => m.ClientsComponent)
      },
      {
        path: 'products',
        loadComponent: () => import('./features/products/products.component').then(m => m.ProductsComponent)
      },
      {
        path: 'quotes',
        loadComponent: () => import('./features/quotes/quotes-list.component').then(m => m.QuotesListComponent)
      },
      {
        path: 'quotes/new',
        loadComponent: () => import('./features/quotes/quote-form.component').then(m => m.QuoteFormComponent)
      },
      {
        path: 'quotes/:id',
        loadComponent: () => import('./features/quotes/quote-detail.component').then(m => m.QuoteDetailComponent)
      },
      {
        path: 'quotes/:id/edit',
        loadComponent: () => import('./features/quotes/quote-form.component').then(m => m.QuoteFormComponent)
      },
      {
        path: 'invoices',
        loadComponent: () => import('./features/invoices/invoices-list.component').then(m => m.InvoicesListComponent)
      },
      {
        path: 'invoices/new',
        loadComponent: () => import('./features/invoices/invoice-form.component').then(m => m.InvoiceFormComponent)
      },
      {
        path: 'invoices/:id',
        loadComponent: () => import('./features/invoices/invoice-detail.component').then(m => m.InvoiceDetailComponent)
      },
      {
        path: 'invoices/:id/edit',
        loadComponent: () => import('./features/invoices/invoice-form.component').then(m => m.InvoiceFormComponent)
      },
      {
        path: 'credit-notes',
        loadComponent: () => import('./features/credit-notes/credit-notes-list.component').then(m => m.CreditNotesListComponent)
      },
      {
        path: 'credit-notes/:id',
        loadComponent: () => import('./features/credit-notes/credit-note-detail.component').then(m => m.CreditNoteDetailComponent)
      },
      {
        path: 'account',
        loadComponent: () => import('./features/account/account-settings.component').then(m => m.AccountSettingsComponent)
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/admin-layout.component').then(m => m.AdminLayoutComponent),
        children: [
          { path: '', redirectTo: 'users', pathMatch: 'full' },
          {
            path: 'users',
            loadComponent: () => import('./features/admin/admin-users.component').then(m => m.AdminUsersComponent)
          },
          {
            path: 'audit-logs',
            loadComponent: () => import('./features/admin/admin-audit-logs.component').then(m => m.AdminAuditLogsComponent)
          }
        ]
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
