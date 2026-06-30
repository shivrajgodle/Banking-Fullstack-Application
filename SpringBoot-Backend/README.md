# 🏦 Banking Backend — Production Grade Spring Boot API

A fully production-ready banking software backend built with **Java 17**, **Spring Boot 3.2**, and **PostgreSQL**.

---

## 📋 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Quick Start](#-quick-start)
- [API Reference](#-api-reference)
- [Security Model](#-security-model)
- [Database Schema](#-database-schema)
- [Configuration](#-configuration)
- [Testing](#-testing)
- [Deployment](#-deployment)

---

## ✅ Features

| Module | Capabilities |
|---|---|
| **Authentication** | JWT + Refresh Tokens, BCrypt passwords, account locking after 5 failed attempts |
| **User Management** | Registration, KYC workflow, profile updates, role-based access |
| **Accounts** | Savings / Current / Salary / NRI accounts, freeze/unfreeze, close |
| **Transactions** | Cash deposit, OTP-secured withdrawal & transfer, daily + per-transaction limits |
| **Loans** | Apply → Approve → Disburse → Repay, EMI calculator (reducing balance), overdue detection |
| **Fixed Deposits** | Compound interest, auto-renewal, premature closure with penalty |
| **Beneficiaries** | Add (OTP-secured), list, delete |
| **OTP System** | Multi-purpose OTPs, 10-min expiry, 3-attempt limit, invalidation on reuse |
| **Notifications** | In-app + email alerts for all transactions |
| **Audit Logs** | Immutable async audit trail for every action |
| **Scheduled Jobs** | Overdue loan marking (1 AM), FD maturity processing (1:30 AM) |
| **API Docs** | Swagger UI at `/api/v1/swagger-ui.html` |

---

## 🛠 Tech Stack

```
Java 17                Spring Boot 3.2.3        PostgreSQL 16
Spring Security 6      JWT (jjwt 0.12.3)        Flyway (DB migrations)
Spring Data JPA        Hibernate 6              HikariCP (connection pooling)
Lombok                 MapStruct                SpringDoc OpenAPI 2.3
Spring Mail            Spring Cache             Spring Actuator
JUnit 5                Mockito                  H2 (test)
Docker                 Docker Compose
```

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    REST Controllers                      │
│  Auth │ User │ Account │ Transaction │ Loan │ FD │ ...  │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                    Service Layer                         │
│  AuthService │ AccountService │ TransactionService       │
│  LoanService │ FdService │ BeneficiaryService            │
│  OtpService  │ AuditService   │ NotificationService      │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│               Repository Layer (Spring Data JPA)        │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                   PostgreSQL 16                         │
│  users │ accounts │ transactions │ loans │ fixed_deposits│
│  beneficiaries │ otps │ audit_logs │ notifications       │
└─────────────────────────────────────────────────────────┘
```

### Security Flow

```
Client → [HTTPS] → JwtAuthFilter → SecurityFilterChain → Controller
                        ↓
                  Validate JWT
                        ↓
                  Load UserDetails
                        ↓
                  Set SecurityContext
```

---

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 16+ (or Docker)

### Option 1 — Docker Compose (recommended)

```bash
# 1. Clone and enter directory
git clone https://github.com/your-org/banking-backend.git
cd banking-backend

# 2. Copy and configure environment
cp .env.example .env
# Edit .env with your values

# 3. Start all services
docker-compose up -d

# 4. View logs
docker-compose logs -f banking-app

# 5. Open Swagger UI
open http://localhost:8080/api/v1/swagger-ui.html
```

### Option 2 — Local Development

```bash
# 1. Create PostgreSQL database
psql -U postgres -c "CREATE DATABASE banking_db;"

# 2. Configure application
cp src/main/resources/application.yml application-local.yml
# Update DB credentials in application-local.yml

# 3. Run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Or build and run JAR
./mvnw clean package -DskipTests
java -jar target/banking-backend-1.0.0.jar
```

---

## 📡 API Reference

Base URL: `http://localhost:8080/api/v1`

### 🔐 Authentication

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/auth/register` | Register new customer | No |
| POST | `/auth/login` | Login, receive JWT | No |
| POST | `/auth/refresh` | Refresh access token | No |
| POST | `/auth/logout` | Revoke refresh token | Yes |
| POST | `/auth/otp/send` | Send OTP to email | Yes |
| GET | `/auth/me` | Get current user | Yes |

### 👤 Users

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| GET | `/users/profile` | Get own profile | Any |
| PUT | `/users/profile` | Update profile | Any |
| POST | `/users/change-password` | Change password | Any |
| GET | `/users` | List all users | ADMIN/MANAGER |
| GET | `/users/{id}` | Get user by ID | ADMIN/MANAGER/TELLER |
| PATCH | `/users/{id}/status` | Update user status | ADMIN/MANAGER |
| PATCH | `/users/{id}/kyc` | Update KYC status | ADMIN/MANAGER |

### 🏦 Accounts

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| POST | `/accounts` | Open new account | Any |
| GET | `/accounts` | List own accounts | Any |
| GET | `/accounts/{number}` | Account details | Owner |
| GET | `/accounts/dashboard` | Financial dashboard | Any |
| POST | `/accounts/{number}/close` | Close account | Owner |
| POST | `/accounts/{number}/freeze` | Freeze account | ADMIN/MANAGER |
| POST | `/accounts/{number}/unfreeze` | Unfreeze account | ADMIN/MANAGER |

### 💸 Transactions

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| POST | `/transactions/deposit` | Cash deposit | TELLER/ADMIN |
| POST | `/transactions/withdraw` | Withdrawal (OTP) | Owner |
| POST | `/transactions/transfer` | Fund transfer (OTP) | Owner |
| GET | `/transactions/account/{number}` | Transaction history | Owner |
| GET | `/transactions/ref/{ref}` | Lookup by reference | Any |
| POST | `/transactions/{id}/reverse` | Reverse transaction | ADMIN |

### 🏛 Loans

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| POST | `/loans/apply` | Apply for loan | Any |
| GET | `/loans` | My loans (paginated) | Any |
| GET | `/loans/{id}` | Loan details | Owner |
| POST | `/loans/repay` | Repay loan (OTP) | Owner |
| GET | `/loans/emi-calculator` | EMI calculator | Any |
| POST | `/loans/{id}/approve` | Approve loan | MANAGER/ADMIN |
| POST | `/loans/{id}/reject` | Reject loan | MANAGER/ADMIN |
| POST | `/loans/{id}/disburse` | Disburse loan | MANAGER/ADMIN |

### 📦 Fixed Deposits

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| POST | `/fixed-deposits` | Create FD | Any |
| GET | `/fixed-deposits` | My FDs (paginated) | Any |
| POST | `/fixed-deposits/{id}/close` | Premature closure | Owner |
| GET | `/fixed-deposits/calculator` | Maturity calculator | Any |

### 👥 Beneficiaries

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/beneficiaries/otp` | Request OTP for adding |
| POST | `/beneficiaries` | Add beneficiary (OTP) |
| GET | `/beneficiaries` | List beneficiaries |
| DELETE | `/beneficiaries/{id}` | Remove beneficiary |

### 🔔 Notifications

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/notifications` | Paginated notifications |
| GET | `/notifications/unread-count` | Unread count |
| POST | `/notifications/mark-all-read` | Mark all as read |

### 🛡 Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/stats` | System-wide statistics |
| GET | `/admin/audit-logs` | All audit logs |
| GET | `/admin/audit-logs/user/{id}` | User audit logs |

---

## 🔒 Security Model

### Roles

| Role | Description |
|------|-------------|
| `CUSTOMER` | Standard banking customer — own accounts only |
| `TELLER` | Bank staff — can do cash deposits, view customer info |
| `MANAGER` | Branch manager — approve/reject loans, freeze accounts |
| `ADMIN` | System admin — full access, reversal, audit logs |

### JWT Flow

```
1. POST /auth/login  →  { accessToken, refreshToken }
2. All requests:  Authorization: Bearer <accessToken>
3. Token expires (24h)  →  POST /auth/refresh  →  new tokens
4. POST /auth/logout  →  refresh token revoked in DB
```

### OTP-Protected Operations

All sensitive operations require a valid OTP sent to the registered email:
- Withdrawal
- Fund Transfer
- Loan Repayment
- Adding a Beneficiary

---

## 🗄 Database Schema

```
users
  ├── accounts (1:N)
  │     ├── transactions (1:N)
  │     └── fixed_deposits (1:N)
  ├── loans (1:N)
  ├── beneficiaries (1:N)
  ├── otps (1:N)
  ├── notifications (1:N)
  ├── refresh_tokens (1:N)
  └── audit_logs (1:N)
```

### Key Design Decisions

- All monetary values use `DECIMAL(19,4)` — never `FLOAT`
- UUIDs for all primary keys — no sequential integer IDs
- `available_balance` separate from `balance` for future hold management
- All timestamps in UTC
- Flyway manages all schema migrations

---

## ⚙️ Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `jwt.expiration` | 86400000 (24h) | Access token TTL in ms |
| `jwt.refresh-expiration` | 604800000 (7d) | Refresh token TTL in ms |
| `banking.transaction.single-limit` | 200,000 | Max single txn amount |
| `banking.transaction.daily-limit` | 1,000,000 | Max daily outflow |
| `banking.otp.expiry-minutes` | 10 | OTP validity window |
| `banking.interest.savings-rate` | 0.04 (4%) | Annual savings rate |
| `banking.interest.fixed-deposit-rate` | 0.065 (6.5%) | Annual FD rate |

### Loan Interest Rates (configured in LoanService)

| Loan Type | Annual Rate |
|-----------|-------------|
| Personal | 12.00% |
| Home | 8.75% |
| Auto | 9.50% |
| Education | 8.00% |
| Business | 11.00% |
| Gold | 7.50% |

---

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# Run with coverage report
./mvnw test jacoco:report
# Report at: target/site/jacoco/index.html

# Run only unit tests
./mvnw test -Dgroups="unit"

# Run only integration tests
./mvnw test -Dgroups="integration"
```

### Test Coverage

| Layer | Tests |
|-------|-------|
| `AuthServiceTest` | Registration, login, duplicate checks, logout |
| `AccountServiceTest` | Create, freeze, close, ownership checks |
| `TransactionServiceTest` | Deposit, withdrawal, transfer, limits, OTP |
| `LoanServiceTest` | EMI calculation, apply, approve, disburse, repay |
| `BankingIntegrationTest` | Full HTTP flows against H2 in-memory DB |

---

## 🐳 Deployment

### Production Docker Compose

```bash
# Start with pgAdmin tools included
docker-compose --profile tools up -d

# Scale app if needed (behind a load balancer)
docker-compose up -d --scale banking-app=3
```

### Environment Variables (Required for Production)

```bash
DB_USERNAME=prod_user
DB_PASSWORD=very_strong_password
JWT_SECRET=64_char_hex_string   # openssl rand -hex 32
MAIL_USERNAME=noreply@yourdomain.com
MAIL_PASSWORD=smtp_password
```

### Health Check

```
GET /api/v1/actuator/health
→ {"status":"UP","components":{...}}
```

### Monitoring Endpoints (Actuator)

```
/actuator/health     → Application health
/actuator/info       → Build information
/actuator/metrics    → JVM & HTTP metrics
```

---

## 📁 Project Structure

```
banking-backend/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/banking/
    │   │   ├── BankingApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── AsyncConfig.java
    │   │   │   └── OpenApiConfig.java
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   ├── UserController.java
    │   │   │   ├── AccountController.java
    │   │   │   ├── TransactionController.java
    │   │   │   ├── LoanController.java
    │   │   │   ├── FixedDepositController.java
    │   │   │   ├── BeneficiaryController.java
    │   │   │   ├── NotificationController.java
    │   │   │   └── AdminController.java
    │   │   ├── dto/
    │   │   │   ├── request/   (15 request DTOs)
    │   │   │   └── response/  (10 response DTOs + ApiResponse)
    │   │   ├── entity/        (User, Account, Transaction, Loan, FD, ...)
    │   │   ├── enums/         (10 enums)
    │   │   ├── exception/     (BankingException, GlobalExceptionHandler)
    │   │   ├── repository/    (9 JPA repositories)
    │   │   ├── security/      (JwtAuthFilter, UserDetailsServiceImpl)
    │   │   ├── service/       (10 services)
    │   │   └── util/          (JwtUtil, AccountNumberGenerator, OtpGenerator)
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__initial_schema.sql
    └── test/
        ├── java/com/banking/
        │   ├── AuthServiceTest.java
        │   ├── AccountServiceTest.java
        │   ├── TransactionServiceTest.java
        │   ├── LoanServiceTest.java
        │   └── BankingIntegrationTest.java
        └── resources/
            └── application.yml
```

---

## 🔑 Default Credentials

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@banking.com | Admin@123 |

> ⚠️ Change the admin password immediately after first login in production.

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.
