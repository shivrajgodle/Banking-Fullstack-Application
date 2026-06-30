/**
 * ============================================================
 * app.config.ts — Angular 17 Application Configuration
 * ============================================================
 *
 * Angular 17 uses a standalone, module-free architecture.
 * Instead of AppModule, we configure the app here.
 *
 * Providers:
 *   - provideRouter: Sets up app routing with lazy loading
 *   - provideHttpClient: HTTP client with our JWT interceptor
 *   - provideAnimations: Enables Angular animations
 */
import { ApplicationConfig } from '@angular/core';
import { provideRouter, withPreloading, PreloadAllModules } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    // Router with preloading — lazy-loaded modules load in background after initial load
    provideRouter(routes, withPreloading(PreloadAllModules)),

    // HTTP client with JWT interceptor applied globally
    provideHttpClient(withInterceptors([jwtInterceptor])),

    // Angular animations (used for transitions, toasts, etc.)
    provideAnimations(),
  ]
};
