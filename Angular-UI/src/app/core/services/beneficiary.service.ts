/**
 * ============================================================
 * beneficiary.service.ts — Beneficiary API Calls
 * ============================================================
 *
 * Handles all API endpoints for beneficiary/payee management:
 * listing, adding (OTP-verified), and removing beneficiaries.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  BeneficiaryResponse,
  AddBeneficiaryRequest
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class BeneficiaryService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** GET /beneficiaries — all saved beneficiaries */
  getBeneficiaries(): Observable<ApiResponse<BeneficiaryResponse[]>> {
    return this.http.get<ApiResponse<BeneficiaryResponse[]>>(`${this.base}/beneficiaries`);
  }

  /** POST /beneficiaries — add a new beneficiary (OTP required) */
  addBeneficiary(request: AddBeneficiaryRequest): Observable<ApiResponse<BeneficiaryResponse>> {
    return this.http.post<ApiResponse<BeneficiaryResponse>>(
      `${this.base}/beneficiaries`, request
    );
  }

  /** POST /beneficiaries/otp — request OTP to add a beneficiary */
  requestBeneficiaryOtp(): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.base}/beneficiaries/otp`, {});
  }

  /** DELETE /beneficiaries/:id — remove a beneficiary */
  deleteBeneficiary(id: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.base}/beneficiaries/${id}`);
  }
}
