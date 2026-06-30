import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AccountService } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { DashboardResponse } from '../../shared/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, CurrencyPipe, DatePipe, TitleCasePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private accountService = inject(AccountService);
  private authService = inject(AuthService);

  dash = signal<DashboardResponse | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    this.accountService.getDashboard().subscribe({
      next: r => { this.dash.set(r.data); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  getAccountStatusClass(status: string): string {
    const map: Record<string, string> = {
      ACTIVE: 'badge-success', FROZEN: 'badge-error',
      CLOSED: 'badge-muted', DORMANT: 'badge-warning'
    };
    return map[status] ?? 'badge-muted';
  }

  isCredit(type: string): boolean {
    return ['DEPOSIT', 'TRANSFER_IN', 'LOAN_DISBURSEMENT', 'INTEREST_CREDIT', 'FD_MATURITY', 'REFUND'].includes(type);
  }

  getTxnIcon(type: string): string {
    if (this.isCredit(type)) return '↓';
    return '↑';
  }

  getTxnIconClass(type: string): string {
    return this.isCredit(type) ? 'credit' : 'debit';
  }
}