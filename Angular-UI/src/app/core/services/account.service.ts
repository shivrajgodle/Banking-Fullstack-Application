/**
 * ============================================================
 * account.service.ts — Account & Dashboard API Calls
 * ============================================================
 *
 * Handles all API endpoints related to bank accounts and the
 * main dashboard summary. Separated from api.service.ts to
 * follow the Single Responsibility Principle.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  AccountResponse,
  CreateAccountRequest,
  DashboardResponse
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class AccountService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // ── Dashboard ─────────────────────────────────────────────────────────────

  /** GET /accounts/dashboard — financial summary for the home screen */
  getDashboard(): Observable<ApiResponse<DashboardResponse>> {
    return this.http.get<ApiResponse<DashboardResponse>>(`${this.base}/accounts/dashboard`);
  }

  // ── Accounts ──────────────────────────────────────────────────────────────

  /** GET /accounts — list all accounts belonging to the current user */
  getAccounts(): Observable<ApiResponse<AccountResponse[]>> {
    return this.http.get<ApiResponse<AccountResponse[]>>(`${this.base}/accounts`);
  }

  /** GET /accounts/:number — single account details */
  getAccount(accountNumber: string): Observable<ApiResponse<AccountResponse>> {
    return this.http.get<ApiResponse<AccountResponse>>(`${this.base}/accounts/${accountNumber}`);
  }

  /** POST /accounts — open a new bank account */
  createAccount(request: CreateAccountRequest): Observable<ApiResponse<AccountResponse>> {
    return this.http.post<ApiResponse<AccountResponse>>(`${this.base}/accounts`, request);
  }

  /** POST /accounts/:number/close — close a zero-balance account */
  closeAccount(accountNumber: string): Observable<ApiResponse<AccountResponse>> {
    return this.http.post<ApiResponse<AccountResponse>>(
      `${this.base}/accounts/${accountNumber}/close`, {}
    );
  }
}
