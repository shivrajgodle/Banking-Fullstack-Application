/**
 * ============================================================
 * loan.service.ts — Loan API Calls
 * ============================================================
 *
 * Handles all API endpoints for loan management:
 * fetching loans, applying, EMI calculation, and repayment.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  PageResponse,
  LoanResponse,
  LoanApplicationRequest,
  EmiCalculatorResult
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class LoanService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** GET /loans — all loans for current user */
  getLoans(page = 0, size = 10): Observable<ApiResponse<PageResponse<LoanResponse>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<LoanResponse>>>(
      `${this.base}/loans`, { params }
    );
  }

  /** GET /loans/:id — single loan details */
  getLoan(loanId: string): Observable<ApiResponse<LoanResponse>> {
    return this.http.get<ApiResponse<LoanResponse>>(`${this.base}/loans/${loanId}`);
  }

  /** POST /loans/apply — submit a new loan application */
  applyLoan(request: LoanApplicationRequest): Observable<ApiResponse<LoanResponse>> {
    return this.http.post<ApiResponse<LoanResponse>>(`${this.base}/loans/apply`, request);
  }

  /**
   * GET /loans/emi-calculator — calculate EMI without authentication
   * @returns monthlyEmi, totalInterest, totalPayable
   */
  calculateEmi(
    principal: number,
    annualRate: number,
    tenureMonths: number
  ): Observable<ApiResponse<EmiCalculatorResult>> {
    const params = new HttpParams()
      .set('principal', principal)
      .set('annualRate', annualRate)
      .set('tenureMonths', tenureMonths);
    return this.http.get<ApiResponse<EmiCalculatorResult>>(
      `${this.base}/loans/emi-calculator`, { params }
    );
  }

  /** POST /loans/repay — make an EMI payment */
  repayLoan(
    loanNumber: string,
    amount: number,
    accountNumber: string,
    otp: string
  ): Observable<ApiResponse<LoanResponse>> {
    return this.http.post<ApiResponse<LoanResponse>>(`${this.base}/loans/repay`, {
      loanNumber, amount, accountNumber, otp
    });
  }
}
