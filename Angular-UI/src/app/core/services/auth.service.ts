/**
 * ============================================================
 * AuthService — Authentication & Token Management
 * ============================================================
 *
 * Central service for:
 *   - Login / Register / Logout API calls
 *   - JWT token storage and retrieval (localStorage)
 *   - Token refresh lifecycle
 *   - Current user state (BehaviorSubject — reactive)
 *   - Route guards (isLoggedIn, currentUser$)
 *
 * Token Storage Strategy:
 *   Access token  → localStorage['access_token']
 *   Refresh token → localStorage['refresh_token']
 *   User profile  → localStorage['current_user'] (JSON)
 *
 * Reactive State:
 *   currentUser$ is a BehaviorSubject — any component can subscribe
 *   and it replays the last value to new subscribers.
 *   This allows the navbar, sidebar, and dashboard to always show
 *   the latest user data without manual passing of props.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse, LoginRequest, RegisterRequest,
  UserResponse, ApiResponse
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class AuthService {

  // Inject dependencies using Angular 17's new inject() function
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** Base URL from environment (e.g., '/api/v1' in dev, 'https://...' in prod) */
  private readonly apiUrl = `${environment.apiUrl}/auth`;

  /**
   * BehaviorSubject holds the current authenticated user.
   * null = not logged in, UserResponse = logged in user.
   *
   * BehaviorSubject vs Subject:
   *   - Subject: only emits to current subscribers (no replay)
   *   - BehaviorSubject: stores the last value, new subscribers get it immediately
   *     This is perfect for "current user" state.
   */
  private currentUserSubject = new BehaviorSubject<UserResponse | null>(
    this.getStoredUser() // Initialize from localStorage on app start
  );

  /** Public Observable — components subscribe to this, never to the subject directly */
  public currentUser$ = this.currentUserSubject.asObservable();

  // ── Storage Keys ──────────────────────────────────────────────────────────
  private readonly TOKEN_KEY = 'access_token';
  private readonly REFRESH_KEY = 'refresh_token';
  private readonly USER_KEY = 'current_user';

  // ── Authentication Methods ────────────────────────────────────────────────

  /**
   * Logs in a user and stores the tokens.
   *
   * @param credentials Email and password
   * @returns Observable of AuthResponse — subscribe to get tokens and user data
   *
   * tap() is an RxJS operator that runs a side effect (storing tokens)
   * without modifying the stream value.
   */
  login(credentials: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(
      `${this.apiUrl}/login`, credentials
    ).pipe(
      tap(response => {
        if (response.success) {
          this.storeTokens(response.data);
          this.currentUserSubject.next(response.data.user);
        }
      }),
      catchError(this.handleError)
    );
  }

  /**
   * Registers a new user. Does NOT log in automatically.
   * After registration, the user is redirected to login.
   */
  register(request: RegisterRequest): Observable<ApiResponse<UserResponse>> {
    return this.http.post<ApiResponse<UserResponse>>(
      `${this.apiUrl}/register`, request
    ).pipe(catchError(this.handleError));
  }

  /**
   * Uses the refresh token to get a new access token.
   * Called automatically by the JWT interceptor when a 401 is received.
   */
  refreshAccessToken(): Observable<ApiResponse<AuthResponse>> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<ApiResponse<AuthResponse>>(
      `${this.apiUrl}/refresh`, { refreshToken }
    ).pipe(
      tap(response => {
        if (response.success) {
          this.storeTokens(response.data);
          this.currentUserSubject.next(response.data.user);
        }
      }),
      catchError(err => {
        // Refresh failed — force logout
        this.logout();
        return throwError(() => err);
      })
    );
  }

  /**
   * Logs out: clears local storage, resets user state, navigates to login.
   * Also calls the backend to revoke refresh tokens.
   */
  logout(): void {
    // Attempt to notify backend (fire and forget — don't wait for response)
    const token = this.getAccessToken();
    if (token) {
      this.http.post(`${this.apiUrl}/logout`, {}).subscribe({
        error: () => {} // Ignore errors — we're logging out anyway
      });
    }
    this.clearStorage();
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  /**
   * Sends an OTP to the user's registered email.
   * @param otpType The purpose (TRANSACTION, BENEFICIARY_ADD, etc.)
   */
  sendOtp(otpType: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(
      `${this.apiUrl}/otp/send`, { otpType }
    );
  }

  /** Gets the current logged-in user's profile from the backend */
  getCurrentUser(): Observable<ApiResponse<UserResponse>> {
    return this.http.get<ApiResponse<UserResponse>>(`${this.apiUrl}/me`).pipe(
      tap(response => {
        if (response.success) {
          this.currentUserSubject.next(response.data);
          localStorage.setItem(this.USER_KEY, JSON.stringify(response.data));
        }
      })
    );
  }

  // ── Token Helpers ─────────────────────────────────────────────────────────

  /** Stores access token, refresh token, and user data to localStorage */
  private storeTokens(authResponse: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, authResponse.accessToken);
    localStorage.setItem(this.REFRESH_KEY, authResponse.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(authResponse.user));
  }

  /** Returns the stored JWT access token */
  getAccessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /** Returns the stored refresh token */
  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_KEY);
  }

  /** Returns the stored user object from localStorage */
  private getStoredUser(): UserResponse | null {
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }

  /** Clears all auth data from localStorage */
  private clearStorage(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  /** Synchronous check — true if a token exists */
  isLoggedIn(): boolean {
    return !!this.getAccessToken();
  }

  /** Returns the current user value synchronously */
  get currentUser(): UserResponse | null {
    return this.currentUserSubject.value;
  }

  /** Error handler — re-throws the error as an Observable */
  private handleError(error: any): Observable<never> {
    return throwError(() => error);
  }
}
