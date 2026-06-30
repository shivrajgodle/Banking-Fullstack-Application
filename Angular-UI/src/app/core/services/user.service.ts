/**
 * ============================================================
 * user.service.ts — User Profile API Calls
 * ============================================================
 *
 * Handles all API endpoints for user profile management:
 * updating profile details and changing the password.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, UserResponse } from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class UserService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** PUT /users/profile — update profile details */
  updateProfile(data: Partial<UserResponse>): Observable<ApiResponse<UserResponse>> {
    return this.http.put<ApiResponse<UserResponse>>(`${this.base}/users/profile`, data);
  }

  /** POST /users/change-password */
  changePassword(
    currentPassword: string,
    newPassword: string,
    confirmPassword: string
  ): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(
      `${this.base}/users/change-password`,
      { currentPassword, newPassword, confirmPassword }
    );
  }
}
