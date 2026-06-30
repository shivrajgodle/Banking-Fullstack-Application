/**
 * ============================================================
 * AuthGuard — Protects Routes Requiring Authentication
 * ============================================================
 *
 * Applied to routes that require a logged-in user.
 * If not authenticated, redirects to /login with a returnUrl
 * so the user is sent back to their intended page after login.
 *
 * Usage in routes:
 *   { path: 'dashboard', canActivate: [authGuard], component: ... }
 *
 * Angular 17 uses functional guards — simpler than class-based CanActivate.
 */
import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true; // Allow navigation
  }

  // Redirect to login, preserving the intended URL
  // e.g., if user tries to visit /dashboard, redirect to /login?returnUrl=/dashboard
  router.navigate(['/login'], {
    queryParams: { returnUrl: state.url }
  });
  return false;
};

/**
 * Guard for public-only routes (login/register).
 * If already logged in, redirects to dashboard.
 */
export const publicGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return true; // Allow access to login/register
  }

  // Already logged in — go to dashboard
  router.navigate(['/dashboard']);
  return false;
};
