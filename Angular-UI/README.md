# NexBank — Angular 17 Banking Frontend

Production-grade Angular 17 banking application connected to the Spring Boot backend.

## Design
- **Theme**: Luxury dark — deep navy backgrounds with warm gold accents
- **Fonts**: Sora (headings) + DM Sans (body) + DM Mono (numbers)
- **Architecture**: Standalone components, signals, lazy loading, functional guards

## Tech Stack
- Angular 17 (standalone, no NgModule)
- TypeScript 5.4 with strict mode
- RxJS 7.8 (Observables for all API calls)
- Angular Signals (reactive state without NgRx)
- SCSS with CSS custom properties (design tokens)

## Quick Start

### Prerequisites
- Node.js 18+
- Spring Boot backend running on `http://localhost:8080`

### Install & Run
```bash
cd banking-frontend
npm install
npm start
# Opens at http://localhost:4200
```

### Build for Production
```bash
npm run build
# Output in dist/banking-frontend/
```

## Project Structure
```
src/
├── app/
│   ├── core/
│   │   ├── guards/          # authGuard, publicGuard
│   │   ├── interceptors/    # jwtInterceptor (auto-adds Bearer token)
│   │   └── services/
│   │       ├── auth.service.ts   # login/register/logout/token mgmt
│   │       ├── api.service.ts    # all backend API calls
│   │       └── toast.service.ts  # global notifications
│   ├── features/            # lazy-loaded page components
│   │   ├── auth/login/
│   │   ├── auth/register/
│   │   ├── dashboard/
│   │   ├── accounts/
│   │   ├── transactions/
│   │   ├── loans/
│   │   ├── fixed-deposits/
│   │   ├── beneficiaries/
│   │   ├── notifications/
│   │   └── profile/
│   └── shared/
│       ├── components/shell/     # sidebar + topbar layout
│       ├── components/toast/     # toast notification container
│       └── models/              # TypeScript interfaces (mirror Spring DTOs)
├── environments/            # API URL config
├── styles.scss              # global design system
└── index.html
```

## Backend Connection

### Development Proxy
`proxy.conf.json` routes all `/api/*` requests to `http://localhost:8080`:
```json
{ "/api": { "target": "http://localhost:8080", "changeOrigin": true } }
```
This means Angular on port 4200 seamlessly talks to Spring Boot on port 8080.

### JWT Flow
1. User logs in → `AuthService.login()` stores `access_token` + `refresh_token` in localStorage
2. `jwtInterceptor` reads the token and adds `Authorization: Bearer <token>` to every HTTP request
3. If backend returns 401 (token expired), interceptor auto-calls `/auth/refresh` and retries
4. On logout, tokens are cleared from localStorage and refresh tokens are revoked on backend

### API Base URL
- Development: `/api/v1` (proxied to `localhost:8080`)
- Production: `https://api.nexbank.com/api/v1` (set in `environment.prod.ts`)

## Features

| Page | What it does |
|------|-------------|
| Login | JWT auth, token storage, redirect to returnUrl |
| Register | Validation, password strength, success redirect |
| Dashboard | Balance summary, accounts list, recent transactions |
| Accounts | View/open/close accounts, account detail modal |
| Transactions | Paginated history, fund transfer, withdrawal (OTP flow) |
| Loans | Apply, EMI calculator, repay with progress tracking |
| Fixed Deposits | Create FD, maturity display, premature close |
| Beneficiaries | Add (OTP secured), list, remove |
| Notifications | Read/unread, mark all read |
| Profile | Edit details, change password, security status |

## OTP Flow (Withdrawal/Transfer/Beneficiary Add)
1. User clicks action → Modal opens (Step 1: OTP request)
2. User clicks "Send OTP" → POST `/auth/otp/send` → email sent
3. User enters OTP from email → Form shown (Step 2)
4. User submits with OTP → Backend verifies → Transaction processed

## Environment Variables
Edit `src/environments/environment.ts` for development:
```typescript
export const environment = {
  production: false,
  apiUrl: '/api/v1',    // proxied to localhost:8080
  appName: 'NexBank'
};
```
