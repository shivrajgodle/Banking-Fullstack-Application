/**
 * ============================================================
 * api.service.ts — DEPRECATED: Use split services instead
 * ============================================================
 *
 * This file has been refactored. All API methods are now split
 * into focused, single-responsibility services following the
 * Separation of Concerns principle:
 *
 *   AccountService     → account.service.ts     (accounts + dashboard)
 *   TransactionService → transaction.service.ts (withdraw, transfer, deposit, history)
 *   LoanService        → loan.service.ts         (loans, EMI calculator, repayment)
 *   FixedDepositService→ fixed-deposit.service.ts(FDs, calculator)
 *   BeneficiaryService → beneficiary.service.ts  (beneficiaries, OTP)
 *   NotificationService→ notification.service.ts (notifications, unread count)
 *   UserService        → user.service.ts          (profile, password)
 *
 * This file is kept for reference only and should not be injected.
 */
export * from './account.service';
export * from './transaction.service';
export * from './loan.service';
export * from './fixed-deposit.service';
export * from './beneficiary.service';
export * from './notification.service';
export * from './user.service';
