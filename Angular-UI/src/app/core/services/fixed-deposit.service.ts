/**
 * ============================================================
 * fixed-deposit.service.ts — Fixed Deposit API Calls
 * ============================================================
 *
 * Handles all API endpoints for fixed deposit management:
 * listing, creating, closing, and maturity calculation.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  PageResponse,
  FixedDepositResponse,
  CreateFdRequest
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class FixedDepositService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** GET /fixed-deposits — all FDs for current user */
  getFixedDeposits(page = 0, size = 10): Observable<ApiResponse<PageResponse<FixedDepositResponse>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<FixedDepositResponse>>>(
      `${this.base}/fixed-deposits`, { params }
    );
  }

  /** POST /fixed-deposits — create a new FD */
  createFd(request: CreateFdRequest): Observable<ApiResponse<FixedDepositResponse>> {
    return this.http.post<ApiResponse<FixedDepositResponse>>(
      `${this.base}/fixed-deposits`, request
    );
  }

  /** POST /fixed-deposits/:id/close — premature closure */
  closeFd(fdId: string): Observable<ApiResponse<FixedDepositResponse>> {
    return this.http.post<ApiResponse<FixedDepositResponse>>(
      `${this.base}/fixed-deposits/${fdId}/close`, {}
    );
  }

  /** GET /fixed-deposits/calculator — compute maturity amount */
  calculateFdMaturity(
    principal: number,
    annualRate: number,
    tenureMonths: number
  ): Observable<ApiResponse<any>> {
    const params = new HttpParams()
      .set('principal', principal)
      .set('annualRate', annualRate)
      .set('tenureMonths', tenureMonths);
    return this.http.get<ApiResponse<any>>(
      `${this.base}/fixed-deposits/calculator`, { params }
    );
  }
}
