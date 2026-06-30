/**
 * ============================================================
 * transaction.service.ts — Transaction API Calls
 * ============================================================
 *
 * Handles all API endpoints for financial transactions:
 * withdrawals, transfers, deposits, and transaction history.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  PageResponse,
  TransactionResponse,
  WithdrawalRequest,
  TransferRequest,
  DepositRequest
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class TransactionService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * GET /transactions/account/:number — paginated transaction history
   * @param page 0-based page number
   * @param size records per page
   */
  getTransactions(
    accountNumber: string,
    page = 0,
    size = 20
  ): Observable<ApiResponse<PageResponse<TransactionResponse>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<TransactionResponse>>>(
      `${this.base}/transactions/account/${accountNumber}`, { params }
    );
  }

  /** POST /transactions/withdraw — OTP-secured withdrawal */
  withdraw(request: WithdrawalRequest): Observable<ApiResponse<TransactionResponse>> {
    return this.http.post<ApiResponse<TransactionResponse>>(
      `${this.base}/transactions/withdraw`, request
    );
  }

  /** POST /transactions/transfer — OTP-secured fund transfer */
  transfer(request: TransferRequest): Observable<ApiResponse<TransactionResponse>> {
    return this.http.post<ApiResponse<TransactionResponse>>(
      `${this.base}/transactions/transfer`, request
    );
  }

  /** POST /transactions/deposit — teller/admin cash deposit */
  deposit(request: DepositRequest): Observable<ApiResponse<TransactionResponse>> {
    return this.http.post<ApiResponse<TransactionResponse>>(
      `${this.base}/transactions/deposit`, request
    );
  }
}
