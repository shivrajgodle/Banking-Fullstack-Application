import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { LoanService } from '../../core/services/loan.service';
import { AccountService } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { LoanResponse, AccountResponse, LoanType, EmiCalculatorResult } from '../../shared/models';

// HTML uses:
//   loading(), loans(), accounts(), showCalc (signal), showApply (signal),
//   repayLoan (signal with .set(null)), repayStep(), otpRequesting(), applying(),
//   repaying(), emiResult(), calcForm (fields: principal, rate, tenure),
//   applyForm (fields: loanType, principalAmount, tenureMonths, accountNumber, purpose),
//   repayForm (fields: accountNumber, amount, otp),
//   loanRates, calculateEmi(), applyLoan(), openRepay(loan),
//   requestRepayOtp(), submitRepay(), getProgress(loan), getLoanStatusClass(status)

@Component({
  selector: 'app-loans',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, TitleCasePipe],
  templateUrl: './loans.component.html',
  styleUrl: './loans.component.scss'
})
export class LoansComponent implements OnInit {
  private loanService    = inject(LoanService);
  private accountService = inject(AccountService);
  private authService    = inject(AuthService);
  private toastService   = inject(ToastService);
  private fb             = inject(FormBuilder);

  loans         = signal<LoanResponse[]>([]);
  accounts      = signal<AccountResponse[]>([]);
  loading       = signal(true);
  showApply     = signal(false);
  showCalc      = signal(false);
  repayLoan     = signal<LoanResponse | null>(null);  // ✅ HTML: repayLoan() and repayLoan.set(null)
  repayStep     = signal(1);                           // ✅ HTML: repayStep() === 1
  otpRequesting = signal(false);                       // ✅ HTML: otpRequesting()
  applying      = signal(false);
  repaying      = signal(false);
  emiResult     = signal<EmiCalculatorResult | null>(null); // ✅ HTML: emiResult()

  readonly loanRates = [
    { type: 'PERSONAL', rate: '10.5% p.a.' },
    { type: 'HOME',     rate: '8.5% p.a.'  },
    { type: 'CAR',      rate: '9.0% p.a.'  },
    { type: 'EDUCATION', rate: '9.5% p.a.' },
    { type: 'BUSINESS', rate: '11.0% p.a.' },
  ];

  // ✅ HTML uses calcForm fields: principal, rate, tenure
  calcForm = this.fb.group({
    principal: [null as number | null, [Validators.required, Validators.min(1000)]],
    rate:      [null as number | null, [Validators.required, Validators.min(1), Validators.max(30)]],
    tenure:    [null as number | null, [Validators.required, Validators.min(1), Validators.max(360)]]
  });

  // ✅ HTML uses applyForm fields: loanType, principalAmount, tenureMonths, accountNumber, purpose
  applyForm = this.fb.group({
    loanType:       ['PERSONAL', Validators.required],
    principalAmount: [null as number | null, [Validators.required, Validators.min(10000)]], // ✅ matches LoanApplicationRequest
    tenureMonths:   [null as number | null, [Validators.required, Validators.min(6), Validators.max(360)]],
    accountNumber:  ['', Validators.required],
    purpose:        ['']
  });

  // ✅ HTML uses repayForm fields: accountNumber, amount, otp
  repayForm = this.fb.group({
    accountNumber: ['', Validators.required],
    amount:        [null as number | null, Validators.required],
    otp:           ['', [Validators.required, Validators.minLength(6)]]
  });

  ngOnInit(): void {
    this.loadLoans();
    this.accountService.getAccounts().subscribe(r =>
      this.accounts.set(r.data.filter(a => a.status === 'ACTIVE'))
    );
  }

  loadLoans(): void {
    this.loading.set(true);
    this.loanService.getLoans().subscribe({
      next: r => { this.loans.set(r.data.content); this.loading.set(false); },
      error: ()  => this.loading.set(false)
    });
  }

  calculateEmi(): void {
    if (this.calcForm.invalid) { this.calcForm.markAllAsTouched(); return; }
    const v = this.calcForm.value;
    // ✅ API expects annualRate — HTML form field is named 'rate'
    this.loanService.calculateEmi(v.principal!, v.rate!, v.tenure!).subscribe({
      next: r => this.emiResult.set(r.data),
      error: () => this.toastService.error('Error', 'Could not calculate EMI.')
    });
  }

  applyLoan(): void {
    if (this.applyForm.invalid) { this.applyForm.markAllAsTouched(); return; }
    this.applying.set(true);
    const v = this.applyForm.value;
    this.loanService.applyLoan({
      loanType:       v.loanType as LoanType,
      principalAmount: v.principalAmount!, // ✅ correct field name for LoanApplicationRequest
      tenureMonths:   v.tenureMonths!,
      accountNumber:  v.accountNumber!,
      purpose:        v.purpose || undefined
    }).subscribe({
      next: r => {
        this.loans.update(l => [r.data, ...l]);
        this.showApply.set(false);
        this.applying.set(false);
        this.toastService.success('Submitted', `Loan ${r.data.loanNumber} is under review.`);
      },
      error: err => {
        this.applying.set(false);
        this.toastService.error('Failed', err?.error?.message || 'Could not apply.');
      }
    });
  }

  openRepay(loan: LoanResponse): void {
    this.repayLoan.set(loan);
    this.repayStep.set(1);
    this.repayForm.reset();
    if (this.accounts().length > 0) {
      this.repayForm.patchValue({
        accountNumber: this.accounts()[0].accountNumber,
        amount: loan.emiAmount
      });
    }
  }

  requestRepayOtp(): void {  // ✅ HTML calls requestRepayOtp()
    this.otpRequesting.set(true);
    this.authService.sendOtp('TRANSACTION').subscribe({
      next: () => {
        this.otpRequesting.set(false);
        this.repayStep.set(2);
        this.toastService.info('OTP Sent', 'Check your registered email.');
      },
      error: () => {
        this.otpRequesting.set(false);
        this.toastService.error('Error', 'Could not send OTP.');
      }
    });
  }

  submitRepay(): void {
    if (this.repayForm.invalid) { this.repayForm.markAllAsTouched(); return; }
    const loan = this.repayLoan();
    if (!loan) return;
    this.repaying.set(true);
    const v = this.repayForm.value;
    this.loanService.repayLoan(loan.loanNumber, v.amount!, v.accountNumber!, v.otp!).subscribe({
      next: r => {
        this.loans.update(list => list.map(l => l.id === r.data.id ? r.data : l));
        this.repayLoan.set(null);
        this.repaying.set(false);
        this.toastService.success('EMI Paid', `₹${v.amount} paid successfully.`);
      },
      error: err => {
        this.repaying.set(false);
        this.toastService.error('Failed', err?.error?.message || 'Payment failed.');
      }
    });
  }

  getProgress(loan: LoanResponse): number {
    if (!loan.principalAmount) return 0;
    return (loan.paidAmount / loan.principalAmount) * 100;
  }

  getLoanStatusClass(status: string): string {
    const m: Record<string, string> = {
      ACTIVE: 'badge-success', CLOSED: 'badge-muted',
      OVERDUE: 'badge-error',  PENDING: 'badge-warning'
    };
    return m[status] ?? 'badge-muted';
  }
}
