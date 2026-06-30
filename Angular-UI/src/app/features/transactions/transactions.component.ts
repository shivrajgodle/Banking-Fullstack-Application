/**
 * TransactionsComponent — Fund Transfers, Withdrawals, Transaction History
 *
 * OTP UX Pattern:
 *  Industry-standard inline panel approach — no floating popup dialog.
 *  The panel slides in alongside the transaction table, keeping users
 *  contextually grounded (matching patterns used by HDFC NetBanking,
 *  ICICI iMobile, etc.). OTP entry uses individual digit boxes for
 *  clearer input feedback and auto-advance behavior.
 */
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AccountService } from '../../core/services/account.service';
import { TransactionService } from '../../core/services/transaction.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { AccountResponse, TransactionResponse } from '../../shared/models';

type PanelType = 'transfer' | 'withdraw' | null;

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, TitleCasePipe],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.scss'
})
export class TransactionsComponent implements OnInit, OnDestroy {

  private accountService     = inject(AccountService);
  private transactionService = inject(TransactionService);
  private authService        = inject(AuthService);
  private toastService       = inject(ToastService);
  private fb                 = inject(FormBuilder);

  accounts        = signal<AccountResponse[]>([]);
  selectedAccount = signal<AccountResponse | null>(null);
  transactions    = signal<TransactionResponse[]>([]);
  loadingTxns     = signal(false);
  activePanel     = signal<PanelType>(null);
  panelStep       = signal<1 | 2>(1);
  otpRequesting   = signal(false);
  submitting      = signal(false);
  currentPage     = signal(0);
  totalPages      = signal(0);
  otpError        = signal('');
  otpCountdown    = signal(0);

  readonly otpDigits = [0, 1, 2, 3, 4, 5];
  otpValues: string[] = ['', '', '', '', '', ''];

  private countdownTimer?: ReturnType<typeof setInterval>;

  transactionForm = this.fb.group({
    fromAccount: ['', Validators.required],
    toAccount:   [''],
    amount:      [null as number | null, [Validators.required, Validators.min(1)]],
    description: ['']
  });

  ngOnInit(): void {
    this.accountService.getAccounts().subscribe(r => {
      const active = r.data.filter(a => a.status === 'ACTIVE');
      this.accounts.set(active);
      if (active.length > 0) this.selectAccount(active[0]);
    });
  }

  ngOnDestroy(): void {
    this.clearCountdown();
  }

  selectAccount(acc: AccountResponse): void {
    this.selectedAccount.set(acc);
    this.currentPage.set(0);
    this.loadTransactions();
  }

  loadTransactions(): void {
    const acc = this.selectedAccount();
    if (!acc) return;
    this.loadingTxns.set(true);
    this.transactionService.getTransactions(acc.accountNumber, this.currentPage(), 15).subscribe({
      next: r => {
        this.transactions.set(r.data.content);
        this.totalPages.set(r.data.totalPages);
        this.loadingTxns.set(false);
      },
      error: () => this.loadingTxns.set(false)
    });
  }

  changePage(page: number): void {
    this.currentPage.set(page);
    this.loadTransactions();
  }

  openPanel(type: PanelType): void {
    this.activePanel.set(type);
    this.panelStep.set(1);
    this.resetOtpBoxes();
    this.otpError.set('');

    const fromAcc = this.selectedAccount()?.accountNumber
      ?? this.accounts()[0]?.accountNumber ?? '';

    this.transactionForm.reset({ fromAccount: fromAcc });

    if (type === 'withdraw') {
      this.transactionForm.get('toAccount')?.clearValidators();
    } else {
      this.transactionForm.get('toAccount')?.setValidators(Validators.required);
    }
    this.transactionForm.get('toAccount')?.updateValueAndValidity();
  }

  closePanel(): void {
    this.activePanel.set(null);
    this.panelStep.set(1);
    this.clearCountdown();
    this.resetOtpBoxes();
  }

  goBackToDetails(): void {
    this.panelStep.set(1);
    this.resetOtpBoxes();
    this.otpError.set('');
  }

  proceedToOtp(): void {
    if (this.transactionForm.invalid) {
      this.transactionForm.markAllAsTouched();
      return;
    }
    this.otpRequesting.set(true);
    this.authService.sendOtp('TRANSACTION').subscribe({
      next: () => {
        this.otpRequesting.set(false);
        this.panelStep.set(2);
        this.startOtpCountdown();
        this.toastService.info('OTP Sent', 'Check your registered email and mobile number.');
        setTimeout(() => {
          (document.getElementById('otp-box-0') as HTMLInputElement)?.focus();
        }, 100);
      },
      error: () => {
        this.otpRequesting.set(false);
        this.toastService.error('Error', 'Could not send OTP. Please try again.');
      }
    });
  }

  resendOtp(): void {
    this.resetOtpBoxes();
    this.proceedToOtp();
  }

  private startOtpCountdown(): void {
    this.clearCountdown();
    this.otpCountdown.set(30);
    this.countdownTimer = setInterval(() => {
      const c = this.otpCountdown();
      if (c <= 1) { this.clearCountdown(); this.otpCountdown.set(0); }
      else         { this.otpCountdown.set(c - 1); }
    }, 1000);
  }

  private clearCountdown(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = undefined;
    }
  }

  onOtpInput(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const val = input.value.replace(/\D/g, '').slice(-1);
    this.otpValues[index] = val;
    this.otpError.set('');
    if (val && index < 5) {
      setTimeout(() => (document.getElementById(`otp-box-${index + 1}`) as HTMLInputElement)?.focus(), 0);
    }
  }

  onOtpKeydown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace' && !this.otpValues[index] && index > 0) {
      this.otpValues[index - 1] = '';
      setTimeout(() => (document.getElementById(`otp-box-${index - 1}`) as HTMLInputElement)?.focus(), 0);
    }
  }

  onOtpPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text') ?? '';
    const digits = text.replace(/\D/g, '').slice(0, 6).split('');
    digits.forEach((d, i) => { this.otpValues[i] = d; });
    setTimeout(() => (document.getElementById(`otp-box-${Math.min(digits.length, 5)}`) as HTMLInputElement)?.focus(), 0);
  }

  getFullOtp(): string {
    return this.otpValues.join('');
  }

  private resetOtpBoxes(): void {
    this.otpValues = ['', '', '', '', '', ''];
  }

  submitTransaction(): void {
    const otp = this.getFullOtp();
    if (otp.length < 6) {
      this.otpError.set('Please enter the complete 6-digit OTP.');
      return;
    }

    this.submitting.set(true);
    const v = this.transactionForm.value;

    const obs$ = this.activePanel() === 'transfer'
      ? this.transactionService.transfer({
          fromAccountNumber: v.fromAccount!,
          toAccountNumber:   v.toAccount!,
          amount:            v.amount!,
          otp,
          description:       v.description || undefined,
          paymentMode:       'IMPS'
        })
      : this.transactionService.withdraw({
          accountNumber: v.fromAccount!,
          amount:        v.amount!,
          otp,
          description:   v.description || undefined
        });

    obs$.subscribe({
      next: () => {
        this.submitting.set(false);
        const type = this.activePanel() === 'transfer' ? 'Transfer' : 'Withdrawal';
        this.closePanel();
        this.toastService.success('Success!', `${type} of ₹${v.amount} completed.`);
        this.loadTransactions();
        this.accountService.getAccounts().subscribe(r =>
          this.accounts.set(r.data.filter(a => a.status === 'ACTIVE'))
        );
      },
      error: err => {
        this.submitting.set(false);
        const msg = err?.error?.message || 'Transaction failed.';
        if (msg.toLowerCase().includes('otp')) {
          this.otpError.set(msg);
          this.resetOtpBoxes();
        } else {
          this.toastService.error('Transaction Failed', msg);
        }
      }
    });
  }

  isInvalidField(field: string): boolean {
    const c = this.transactionForm.get(field);
    return !!(c?.invalid && c?.touched);
  }

  isCredit(type: string): boolean {
    return ['DEPOSIT', 'TRANSFER_IN', 'LOAN_DISBURSEMENT', 'INTEREST_CREDIT', 'FD_MATURITY', 'REFUND'].includes(type);
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = {
      COMPLETED: 'badge-success', FAILED: 'badge-error',
      PENDING: 'badge-warning', REVERSED: 'badge-muted', PROCESSING: 'badge-info'
    };
    return map[status] ?? 'badge-muted';
  }
}
