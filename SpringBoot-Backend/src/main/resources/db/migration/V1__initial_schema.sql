-- =============================================
-- V1__initial_schema.sql
-- Banking Database Schema
-- =============================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================
-- USERS TABLE
-- =============================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone_number    VARCHAR(20) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    date_of_birth   DATE NOT NULL,
    national_id     VARCHAR(50) NOT NULL UNIQUE,
    address         TEXT,
    city            VARCHAR(100),
    state           VARCHAR(100),
    zip_code        VARCHAR(20),
    country         VARCHAR(100) DEFAULT 'India',
    role            VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    kyc_status      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    email_verified  BOOLEAN DEFAULT FALSE,
    phone_verified  BOOLEAN DEFAULT FALSE,
    failed_login_attempts INT DEFAULT 0,
    locked_until    TIMESTAMP,
    last_login      TIMESTAMP,
    profile_image   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- ACCOUNTS TABLE
-- =============================================
CREATE TABLE accounts (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_number      VARCHAR(20) NOT NULL UNIQUE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_type        VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    balance             DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance   DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    interest_rate       DECIMAL(5,4) DEFAULT 0.0000,
    daily_limit         DECIMAL(19,4) DEFAULT 200000.0000,
    nominee_name        VARCHAR(200),
    nominee_relation    VARCHAR(100),
    branch_code         VARCHAR(20) DEFAULT 'MAIN001',
    ifsc_code           VARCHAR(20) DEFAULT 'BANK0001',
    opened_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    closed_date         DATE,
    last_transaction_at TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- TRANSACTIONS TABLE
-- =============================================
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_ref     VARCHAR(50) NOT NULL UNIQUE,
    account_id          UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    counterparty_account_id UUID REFERENCES accounts(id),
    transaction_type    VARCHAR(50) NOT NULL,
    amount              DECIMAL(19,4) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance_before      DECIMAL(19,4) NOT NULL,
    balance_after       DECIMAL(19,4) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    description         TEXT,
    reference_number    VARCHAR(100),
    payment_mode        VARCHAR(50),
    channel             VARCHAR(50) DEFAULT 'ONLINE',
    initiated_by        UUID REFERENCES users(id),
    approved_by         UUID REFERENCES users(id),
    ip_address          VARCHAR(50),
    device_info         TEXT,
    failure_reason      TEXT,
    reversal_of         UUID REFERENCES transactions(id),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP
);

-- =============================================
-- LOANS TABLE
-- =============================================
CREATE TABLE loans (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_number         VARCHAR(30) NOT NULL UNIQUE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_id          UUID REFERENCES accounts(id),
    loan_type           VARCHAR(50) NOT NULL,
    principal_amount    DECIMAL(19,4) NOT NULL,
    outstanding_amount  DECIMAL(19,4) NOT NULL,
    interest_rate       DECIMAL(5,4) NOT NULL,
    tenure_months       INT NOT NULL,
    emi_amount          DECIMAL(19,4) NOT NULL,
    emi_day             INT NOT NULL DEFAULT 5,
    disbursed_amount    DECIMAL(19,4) DEFAULT 0,
    total_interest      DECIMAL(19,4) NOT NULL,
    total_payable       DECIMAL(19,4) NOT NULL,
    paid_amount         DECIMAL(19,4) DEFAULT 0,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    purpose             TEXT,
    disbursed_date      DATE,
    maturity_date       DATE,
    next_emi_date       DATE,
    last_payment_date   DATE,
    overdue_amount      DECIMAL(19,4) DEFAULT 0,
    overdue_days        INT DEFAULT 0,
    penalty_rate        DECIMAL(5,4) DEFAULT 0.02,
    collateral_type     VARCHAR(100),
    collateral_value    DECIMAL(19,4),
    approved_by         UUID REFERENCES users(id),
    approved_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- FIXED DEPOSITS TABLE
-- =============================================
CREATE TABLE fixed_deposits (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    fd_number           VARCHAR(30) NOT NULL UNIQUE,
    account_id          UUID NOT NULL REFERENCES accounts(id),
    user_id             UUID NOT NULL REFERENCES users(id),
    principal_amount    DECIMAL(19,4) NOT NULL,
    interest_rate       DECIMAL(5,4) NOT NULL,
    tenure_months       INT NOT NULL,
    maturity_amount     DECIMAL(19,4) NOT NULL,
    maturity_date       DATE NOT NULL,
    interest_payout     VARCHAR(30) DEFAULT 'ON_MATURITY',
    auto_renew          BOOLEAN DEFAULT FALSE,
    status              VARCHAR(30) DEFAULT 'ACTIVE',
    opened_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    closed_date         DATE,
    premature_penalty   DECIMAL(5,4) DEFAULT 0.01,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- BENEFICIARIES TABLE
-- =============================================
CREATE TABLE beneficiaries (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    beneficiary_name    VARCHAR(200) NOT NULL,
    account_number      VARCHAR(30) NOT NULL,
    ifsc_code           VARCHAR(20),
    bank_name           VARCHAR(200),
    nickname            VARCHAR(100),
    status              VARCHAR(30) DEFAULT 'ACTIVE',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, account_number)
);

-- =============================================
-- OTP TABLE
-- =============================================
CREATE TABLE otps (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp_code        VARCHAR(10) NOT NULL,
    otp_type        VARCHAR(50) NOT NULL,
    is_used         BOOLEAN DEFAULT FALSE,
    attempts        INT DEFAULT 0,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- AUDIT LOGS TABLE
-- =============================================
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(100),
    old_values      TEXT,
    new_values      TEXT,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    status          VARCHAR(30) DEFAULT 'SUCCESS',
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- REFRESH TOKENS TABLE
-- =============================================
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- NOTIFICATIONS TABLE
-- =============================================
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    message         TEXT NOT NULL,
    type            VARCHAR(50) NOT NULL,
    is_read         BOOLEAN DEFAULT FALSE,
    reference_id    UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- INDEXES
-- =============================================
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_national_id ON users(national_id);
CREATE INDEX idx_accounts_number ON accounts(account_number);
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_ref ON transactions(transaction_ref);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_loans_user_id ON loans(user_id);
CREATE INDEX idx_loans_number ON loans(loan_number);
CREATE INDEX idx_fd_account_id ON fixed_deposits(account_id);
CREATE INDEX idx_audit_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- =============================================
-- DEFAULT ADMIN USER (password: Admin@123)
-- =============================================
INSERT INTO users (
    id, first_name, last_name, email, phone_number, password_hash,
    date_of_birth, national_id, role, status, kyc_status, email_verified
) VALUES (
    uuid_generate_v4(),
    'System', 'Admin', 'admin@banking.com', '9999999999',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj2NcVrjRN6i',
    '1990-01-01', 'ADMIN000001', 'ADMIN', 'ACTIVE', 'APPROVED', TRUE
);
