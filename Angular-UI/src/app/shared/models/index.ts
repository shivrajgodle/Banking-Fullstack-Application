// Auth
export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  password: string;
  dateOfBirth: string;
  nationalId: string;
  address?: string;
  city?: string;
  state?: string;
  zipCode?: string;
  country?: string;
}
export interface LoginRequest {
  email: string;
  password: string;
}
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}

// User
export interface UserResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  dateOfBirth: string;
  nationalId: string;
  address?: string;
  city?: string;
  state?: string;
  zipCode?: string;
  country?: string;
  role: UserRole;
  status: UserStatus;
  kycStatus: KycStatus;
  emailVerified: boolean;
  phoneVerified: boolean;
  lastLogin?: string;
  profileImage?: string;
  createdAt: string;
}
export type UserRole = "CUSTOMER" | "TELLER" | "MANAGER" | "ADMIN";
export type UserStatus =
  | "ACTIVE"
  | "INACTIVE"
  | "SUSPENDED"
  | "LOCKED"
  | "CLOSED";
export type KycStatus = "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED";

// Account
export interface AccountResponse {
  id: string;
  accountNumber: string;
  ownerName: string;
  accountType: AccountType;
  status: AccountStatus;
  balance: number;
  availableBalance: number;
  currency: string;
  interestRate: number;
  dailyLimit: number;
  nomineeName?: string;
  nomineeRelation?: string;
  branchCode: string;
  ifscCode: string;
  openedDate: string;
  lastTransactionAt?: string;
  createdAt: string;
}
export type AccountType =
  | "SAVINGS"
  | "CURRENT"
  | "SALARY"
  | "NRI"
  | "FIXED_DEPOSIT";
export type AccountStatus =
  | "ACTIVE"
  | "INACTIVE"
  | "FROZEN"
  | "CLOSED"
  | "DORMANT";
export interface CreateAccountRequest {
  accountType: AccountType;
  nomineeName?: string;
  nomineeRelation?: string;
  currency?: string;
}

// Transaction
export interface TransactionResponse {
  id: string;
  transactionRef: string;
  accountNumber: string;
  counterpartyAccountNumber?: string;
  transactionType: TransactionType;
  amount: number;
  currency: string;
  balanceBefore: number;
  balanceAfter: number;
  status: TransactionStatus;
  description?: string;
  referenceNumber?: string;
  paymentMode?: string;
  channel?: string;
  failureReason?: string;
  completedAt?: string;
  createdAt: string;
}
export type TransactionType =
  | "DEPOSIT"
  | "WITHDRAWAL"
  | "TRANSFER_IN"
  | "TRANSFER_OUT"
  | "LOAN_DISBURSEMENT"
  | "LOAN_REPAYMENT"
  | "INTEREST_CREDIT"
  | "FD_CREATION"
  | "FD_MATURITY"
  | "REVERSAL"
  | "CHARGE"
  | "REFUND";
export type TransactionStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "REVERSED"
  | "CANCELLED";
export interface WithdrawalRequest {
  accountNumber: string;
  amount: number;
  otp: string;
  description?: string;
  paymentMode?: string;
}
export interface TransferRequest {
  fromAccountNumber: string;
  toAccountNumber: string;
  amount: number;
  otp: string;
  description?: string;
  paymentMode?: string;
}
export interface DepositRequest {
  accountNumber: string;
  amount: number;
  description?: string;
  paymentMode?: string;
  referenceNumber?: string;
}

// Loan
export interface LoanResponse {
  id: string;
  loanNumber: string;
  borrowerName: string;
  loanType: LoanType;
  principalAmount: number;
  outstandingAmount: number;
  interestRate: number;
  tenureMonths: number;
  emiAmount: number;
  emiDay: number;
  totalInterest: number;
  totalPayable: number;
  paidAmount: number;
  status: LoanStatus;
  purpose?: string;
  disbursedDate?: string;
  maturityDate?: string;
  nextEmiDate?: string;
  overdueAmount: number;
  overdueDays: number;
  createdAt: string;
}
export type LoanType =
  | "PERSONAL"
  | "HOME"
  | "AUTO"
  | "EDUCATION"
  | "BUSINESS"
  | "GOLD";
export type LoanStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "ACTIVE"
  | "CLOSED"
  | "OVERDUE"
  | "WRITTEN_OFF";
export interface LoanApplicationRequest {
  loanType: LoanType;
  principalAmount: number;
  tenureMonths: number;
  accountNumber: string;
  purpose?: string;
  collateralType?: string;
  collateralValue?: number;
}
export interface EmiCalculatorResult {
  principal: number;
  annualInterestRate: number;
  tenureMonths: number;
  monthlyEmi: number;
  totalInterest: number;
  totalPayable: number;
}

// Fixed Deposit
export interface FixedDepositResponse {
  id: string;
  fdNumber: string;
  accountNumber: string;
  principalAmount: number;
  interestRate: number;
  tenureMonths: number;
  maturityAmount: number;
  maturityDate: string;
  interestPayout: string;
  autoRenew: boolean;
  status: string;
  openedDate: string;
  createdAt: string;
}
export interface CreateFdRequest {
  accountNumber: string;
  principalAmount: number;
  tenureMonths: number;
  autoRenew?: boolean;
  interestPayout?: string;
}

// Beneficiary
export interface BeneficiaryResponse {
  id: string;
  beneficiaryName: string;
  accountNumber: string;
  ifscCode: string;
  bankName?: string;
  nickname?: string;
  status: string;
  createdAt: string;
}
export interface AddBeneficiaryRequest {
  beneficiaryName: string;
  accountNumber: string;
  ifscCode: string;
  bankName?: string;
  nickname?: string;
  otp: string;
}

// Notification
export interface NotificationResponse {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  read: boolean;
  referenceId?: string;
  createdAt: string;
}
export type NotificationType =
  | "TRANSACTION"
  | "LOAN"
  | "ACCOUNT"
  | "SECURITY"
  | "PROMOTIONAL"
  | "SYSTEM";

// Dashboard
export interface DashboardResponse {
  totalBalance: number;
  totalAccounts: number;
  activeLoans: number;
  activeFds: number;
  totalLoanOutstanding: number;
  totalFdAmount: number;
  recentTransactions: TransactionResponse[];
  accounts: AccountResponse[];
}

// Generic wrappers
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type OtpType =
  | "LOGIN"
  | "TRANSACTION"
  | "RESET_PASSWORD"
  | "EMAIL_VERIFY"
  | "PHONE_VERIFY"
  | "BENEFICIARY_ADD";
