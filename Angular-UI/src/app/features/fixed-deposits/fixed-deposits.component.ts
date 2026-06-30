import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { FixedDepositService } from '../../core/services/fixed-deposit.service';
import { AccountService } from '../../core/services/account.service';
import { ToastService } from '../../core/services/toast.service';
import { FixedDepositResponse, AccountResponse } from '../../shared/models';

@Component({
  selector: 'app-fixed-deposits',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './fixed-deposits.component.html',
  styleUrl: './fixed-deposits.component.scss'
})
export class FixedDepositsComponent implements OnInit {
  private fdService      = inject(FixedDepositService);
  private accountService = inject(AccountService);
  private toastService   = inject(ToastService);
  private fb             = inject(FormBuilder);

  fds        = signal<FixedDepositResponse[]>([]);
  accounts   = signal<AccountResponse[]>([]);
  loading    = signal(true);
  creating   = signal(false);
  showCreate = signal(false);
  maturity   = signal<any>(null);

  createForm = this.fb.group({
    accountNumber: ['', Validators.required],
    principalAmount: [null as number | null, [Validators.required, Validators.min(1000)]], // ✅ matches CreateFdRequest
    tenureMonths:  [null as number | null, [Validators.required, Validators.min(1), Validators.max(120)]],
    autoRenew:     [false]
  });

  ngOnInit(): void {
    this.loadFds();
    this.accountService.getAccounts().subscribe(r =>
      this.accounts.set(r.data.filter(a => a.status === 'ACTIVE'))
    );
  }

  loadFds(): void {
    this.loading.set(true);
    this.fdService.getFixedDeposits().subscribe({
      next: r => { this.fds.set(r.data.content); this.loading.set(false); },
      error: ()  => this.loading.set(false)
    });
  }

  calcMaturity(): void {
    const v = this.createForm.value;
    if (!v.principalAmount || !v.tenureMonths) return;
    this.fdService.calculateFdMaturity(v.principalAmount, 6.5, v.tenureMonths).subscribe({
      next: r => this.maturity.set(r.data),
      error: () => {}
    });
  }

  createFd(): void {
    if (this.createForm.invalid) { this.createForm.markAllAsTouched(); return; }
    this.creating.set(true);
    const v = this.createForm.value;
    this.fdService.createFd({
      accountNumber:   v.accountNumber!,
      principalAmount: v.principalAmount!, // ✅ correct field name for CreateFdRequest
      tenureMonths:    v.tenureMonths!,
      autoRenew:       v.autoRenew ?? false
    }).subscribe({
      next: r => {
        this.fds.update(list => [r.data, ...list]);
        this.showCreate.set(false);
        this.creating.set(false);
        this.maturity.set(null);
        this.toastService.success('FD Created', `FD ${r.data.fdNumber} opened successfully.`);
      },
      error: err => {
        this.creating.set(false);
        this.toastService.error('Failed', err?.error?.message || 'Could not create FD.');
      }
    });
  }

  confirmClose(fd: FixedDepositResponse): void {
    if (!confirm(`Close FD ${fd.fdNumber} prematurely? A penalty may apply.`)) return;
    this.fdService.closeFd(fd.id).subscribe({
      next: r => {
        this.fds.update(list => list.map(f => f.id === r.data.id ? r.data : f));
        this.toastService.success('FD Closed', `FD ${fd.fdNumber} has been closed.`);
      },
      error: err => this.toastService.error('Failed', err?.error?.message || 'Could not close FD.')
    });
  }

  isInvalid(field: string): boolean {
    const c = this.createForm.get(field);
    return !!(c?.invalid && c?.touched);
  }
}
