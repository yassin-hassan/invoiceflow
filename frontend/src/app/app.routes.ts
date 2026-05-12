import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

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
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
