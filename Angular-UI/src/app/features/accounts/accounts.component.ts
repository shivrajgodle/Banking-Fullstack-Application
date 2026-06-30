import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AccountService } from '../../core/services/account.service';
import { ToastService } from '../../core/services/toast.service';
import { AccountResponse, AccountType } from '../../shared/models';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, TitleCasePipe],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss'
})
export class AccountsComponent implements OnInit {
  private accountService = inject(AccountService);
  private toastService   = inject(ToastService);
  private fb             = inject(FormBuilder);

  accounts        = signal<AccountResponse[]>([]);
  loading         = signal(true);
  creating        = signal(false);
  showCreateModal = signal(false);
  selectedAccount = signal<AccountResponse | null>(null);

  createForm = this.fb.group({
    accountType:     ['SAVINGS', Validators.required],
    nomineeName:     [''],
    nomineeRelation: [''],
    currency:        ['INR']
  });

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.loading.set(true);
    this.accountService.getAccounts().subscribe({
      next: r => { this.accounts.set(r.data); this.loading.set(false); },
      error: ()  => this.loading.set(false)
    });
  }

  createAccount(): void {
    this.creating.set(true);
    const v = this.createForm.value;
    this.accountService.createAccount({
      accountType:     v.accountType as AccountType,
      nomineeName:     v.nomineeName     || undefined,
      nomineeRelation: v.nomineeRelation || undefined,
      currency:        v.currency        || 'INR'
    }).subscribe({
      next: r => {
        this.accounts.update(list => [...list, r.data]);
        this.showCreateModal.set(false);
        this.creating.set(false);
        this.createForm.reset({ accountType: 'SAVINGS', currency: 'INR' });
        this.toastService.success('Account Opened!', `Account ${r.data.accountNumber} created.`);
      },
      error: err => {
        this.creating.set(false);
        this.toastService.error('Failed', err?.error?.message || 'Could not create account.');
      }
    });
  }

  selectAccount(acc: AccountResponse): void {
    this.selectedAccount.set(acc);
  }

  formatAccountNumber(num: string): string {
    return num.replace(/(.{4})/g, '$1 ').trim();
  }

  getStatusClass(status: string): string {
    const m: Record<string, string> = {
      ACTIVE: 'badge-success', FROZEN: 'badge-error',
      CLOSED: 'badge-muted',  DORMANT: 'badge-warning'
    };
    return m[status] ?? 'badge-muted';
  }
}
