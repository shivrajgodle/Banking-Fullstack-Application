/**
 * ============================================================
 * app.routes.ts — Application Routing Configuration
 * ============================================================
 *
 * Lazy Loading Strategy:
 *   Each feature route uses loadComponent() to lazy-load the component.
 *   This means:
 *   - Initial bundle is small (only login/register loaded upfront)
 *   - Dashboard, accounts, etc. are downloaded only when first visited
 *   - Faster initial page load
 *
 * Route Guards:
 *   - authGuard:   Requires login (redirect to /login if not authenticated)
 *   - publicGuard: Redirects logged-in users away from login/register
 */
import { Routes } from '@angular/router';
import { authGuard, publicGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  // ── Public Routes (no auth required) ─────────────────────────────────────
  {
    path: 'login',
    canActivate: [publicGuard],   // Redirect if already logged in
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    canActivate: [publicGuard],
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },

  // ── Protected Routes (requires login) ────────────────────────────────────
  {
    path: '',
    canActivate: [authGuard],
    // Shell layout component wraps all authenticated pages (sidebar + navbar)
    loadComponent: () =>
      import('./shared/components/shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'accounts',
        loadComponent: () =>
          import('./features/accounts/accounts.component').then(m => m.AccountsComponent)
      },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./features/transactions/transactions.component').then(m => m.TransactionsComponent)
      },
      {
        path: 'loans',
        loadComponent: () =>
          import('./features/loans/loans.component').then(m => m.LoansComponent)
      },
      {
        path: 'fixed-deposits',
        loadComponent: () =>
          import('./features/fixed-deposits/fixed-deposits.component').then(m => m.FixedDepositsComponent)
      },
      {
        path: 'beneficiaries',
        loadComponent: () =>
          import('./features/beneficiaries/beneficiaries.component').then(m => m.BeneficiariesComponent)
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications.component').then(m => m.NotificationsComponent)
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/profile/profile.component').then(m => m.ProfileComponent)
      },
    ]
  },

  // Catch-all redirect
  { path: '**', redirectTo: 'dashboard' }
];
