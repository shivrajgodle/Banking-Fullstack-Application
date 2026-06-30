import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { NotificationService } from '../../core/services/notification.service';
import { ToastService } from '../../core/services/toast.service';
import { NotificationResponse } from '../../shared/models';

// HTML uses:
//   loading(), notifications(), unreadCount(), markingRead(),
//   markAllRead(), changePage(), getIcon(type)

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss'
})
export class NotificationsComponent implements OnInit {
  private notificationService = inject(NotificationService);
  private toastService        = inject(ToastService);

  notifications = signal<NotificationResponse[]>([]);
  loading       = signal(true);
  unreadCount   = signal(0);     // ✅ HTML calls unreadCount()
  currentPage   = signal(0);
  totalPages    = signal(0);
  markingRead   = signal(false);

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.loading.set(true);
    this.notificationService.getNotifications(this.currentPage(), 20).subscribe({
      next: r => {
        this.notifications.set(r.data.content);
        this.totalPages.set(r.data.totalPages);
        // Compute unread count from the loaded page
        this.unreadCount.set(r.data.content.filter(n => !n.read).length);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    // Also fetch the true server-side unread count
    this.notificationService.getUnreadCount().subscribe({
      next: r => this.unreadCount.set(r.data?.unreadCount ?? 0),
      error: () => {}
    });
  }

  markAllRead(): void {
    this.markingRead.set(true);
    this.notificationService.markAllNotificationsRead().subscribe({
      next: () => {
        this.markingRead.set(false);
        this.unreadCount.set(0);
        this.notifications.update(list => list.map(n => ({ ...n, read: true })));
        this.toastService.success('Done', 'All notifications marked as read.');
      },
      error: () => this.markingRead.set(false)
    });
  }

  changePage(page: number): void {
    this.currentPage.set(page);
    this.loadNotifications();
  }

  // ✅ HTML calls getIcon(n.type)
  getIcon(type: string): string {
    const icons: Record<string, string> = {
      TRANSACTION: '💸',
      LOAN:        '🏛️',
      ALERT:       '⚠️',
      SYSTEM:      '🔔',
      FD:          '🔒',
      KYC:         '📋'
    };
    return icons[type] ?? '🔔';
  }

  getTypeClass(type: string): string {
    const m: Record<string, string> = {
      TRANSACTION: 'notif-transaction',
      LOAN:        'notif-loan',
      ALERT:       'notif-alert',
      SYSTEM:      'notif-system'
    };
    return m[type] ?? 'notif-system';
  }
}
