/**
 * ============================================================
 * JwtInterceptor — Adds JWT to Every API Request
 * ============================================================
 *
 * This HTTP interceptor automatically:
 *   1. Reads the JWT access token from localStorage
 *   2. Adds "Authorization: Bearer <token>" header to every request
 *   3. If the response is 401 (token expired), automatically:
 *      a. Calls POST /auth/refresh to get a new token
 *      b. Retries the original request with the new token
 *      c. If refresh fails → forces logout
 *
 * This means components NEVER need to manually add Authorization headers.
 * The interceptor handles it transparently.
 *
 * Flow:
 *   Component makes request
 *     → Interceptor adds Authorization header
 *       → Spring Boot validates JWT
 *         → If 401: interceptor refreshes token and retries
 *           → Success: component gets its data
 *
 * Angular 17 uses functional interceptors (not class-based) — simpler and
 * works with the standalone component architecture.
 */
import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Functional HTTP interceptor — Angular 17 style.
 * Exported as a function and registered in app.config.ts.
 */
export const jwtInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  // Clone the request and add the Authorization header if a token exists.
  // HTTP requests are immutable — we must clone to modify them.
  const authReq = token
    ? req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      })
    : req;

  // Pass the (possibly modified) request to the next handler
  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle 401 Unauthorized — token might be expired
      if (error.status === 401 && authService.getRefreshToken()) {
        // Try to refresh the access token
        return authService.refreshAccessToken().pipe(
          switchMap(response => {
            // Retry the original request with the NEW access token
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${response.data.accessToken}` }
            });
            return next(retryReq);
          }),
          catchError(refreshError => {
            // Refresh also failed — force logout
            authService.logout();
            return throwError(() => refreshError);
          })
        );
      }
      // For other errors, just re-throw
      return throwError(() => error);
    })
  );
};
