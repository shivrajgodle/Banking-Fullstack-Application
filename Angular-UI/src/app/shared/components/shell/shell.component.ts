/**
 * ShellComponent — Authenticated Layout Wrapper
 *
 * Fix: ngOnInit now calls getCurrentUser() to re-fetch the logged-in user
 * from the backend on every app load. Without this, the BehaviorSubject
 * is seeded only from localStorage. If localStorage is cleared or the token
 * is refreshed, currentUser$ emits null and the topbar shows '?' / blank name.
 */
import { Component, signal, inject, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule, TitleCasePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UserResponse } from '../../models';

interface NavItem {
  icon: string;
  label: string;
  route: string;
  badge?: number;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule, TitleCasePipe],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent implements OnInit {
  private authService         = inject(AuthService);
  private notificationService = inject(NotificationService);

  sidebarCollapsed = signal(false);
  user             = signal<UserResponse | null>(null);
  unreadCount      = signal(0);

  navItems: NavItem[] = [
    {
      route: '/dashboard', label: 'Dashboard',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
               <rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
             </svg>`
    },
    {
      route: '/accounts', label: 'Accounts',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>
             </svg>`
    },
    {
      route: '/transactions', label: 'Transactions',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <path d="M8 6L4 10l4 4M16 6l4 4-4 4M14 4l-4 16"/>
             </svg>`
    },
    {
      route: '/loans', label: 'Loans',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
             </svg>`
    },
    {
      route: '/fixed-deposits', label: 'Fixed Deposits',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
             </svg>`
    },
    {
      route: '/beneficiaries', label: 'Beneficiaries',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
               <circle cx="9" cy="7" r="4"/>
               <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
             </svg>`
    },
    {
      route: '/notifications', label: 'Notifications',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 01-3.46 0"/>
             </svg>`
    },
    {
      route: '/profile', label: 'Profile',
      icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
               <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
               <circle cx="12" cy="7" r="4"/>
             </svg>`
    },
  ];

  ngOnInit(): void {
    // Subscribe to the user stream first so we display whatever is in localStorage immediately
    this.authService.currentUser$.subscribe(u => this.user.set(u));

    // Then fetch fresh data from the backend.
    // This ensures the topbar/greeting never shows stale or missing user data
    // even after a hard refresh, token renewal, or if localStorage was cleared.
    this.authService.getCurrentUser().subscribe({
      error: () => {
        // If /auth/me fails (e.g. expired token), the interceptor handles logout.
        // We silently ignore here — the user stream already shows the cached value.
      }
    });

    this.notificationService.getUnreadCount().subscribe({
      next: r => this.unreadCount.set(r.data?.unreadCount ?? 0),
      error: () => {}
    });
  }

  toggleSidebar(): void {
    this.sidebarCollapsed.update(v => !v);
  }

  logout(): void {
    this.authService.logout();
  }

  getInitials(): string {
    const u = this.user();
    if (!u) return '?';
    return `${u.firstName[0]}${u.lastName[0]}`.toUpperCase();
  }

  timeOfDay(): string {
    const h = new Date().getHours();
    if (h < 12) return 'morning';
    if (h < 17) return 'afternoon';
    return 'evening';
  }
}
