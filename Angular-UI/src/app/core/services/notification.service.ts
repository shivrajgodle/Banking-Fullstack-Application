/**
 * ============================================================
 * notification.service.ts — Notification API Calls
 * ============================================================
 *
 * Handles all API endpoints for the notification centre:
 * fetching, unread count, and marking notifications as read.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  PageResponse,
  NotificationResponse
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** GET /notifications — paginated notifications */
  getNotifications(page = 0, size = 20): Observable<ApiResponse<PageResponse<NotificationResponse>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<NotificationResponse>>>(
      `${this.base}/notifications`, { params }
    );
  }

  /** GET /notifications/unread-count */
  getUnreadCount(): Observable<ApiResponse<{ unreadCount: number }>> {
    return this.http.get<ApiResponse<{ unreadCount: number }>>(
      `${this.base}/notifications/unread-count`
    );
  }

  /** POST /notifications/mark-all-read */
  markAllNotificationsRead(): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(
      `${this.base}/notifications/mark-all-read`, {}
    );
  }
}
