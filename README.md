# FAPP - Financial Analysis & Planning Platform

A full-stack personal finance dashboard that pulls transactions from multiple UK bank accounts into one consistent view, categorises and analyses them, and tracks progress towards savings goals.

**FAPP** informs - it is not a financial adviser, an investment platform or a banking replacement. Every figure shown is a deterministic calculation over the user's own imported statements.

## Screenshots

![FAPP Dashboard](screenshots/dashboard.png)
![FAPP Transactions](screenshots/transactions.png)
![FAPP TransactionsLookup](screenshots/transactionlookup.png)
![FAPP Goals](screenshots/goals.png)
![FAPP Account](screenshots/account.png)

## The problem it solves

Using more than one bank at once as Bank of Scotland and Monzo, for example, means financial reality is split across separate apps, separate CSV formats and separate category names, with no combined view.

FAPP imports each bank's statement through a bank-specific adapter, normalises it into a single transaction model, and helps user see what actually happens with their money.

## Why I built it

I started **FAPP** as a software engineering project, where i wanted to build a full-stack application with a real database and proper tests. But using it on my own finances was genuinely eye-opening. Once everything was imported, categorised and visible in one place, seeing where my money actually went, the real numbers, month over month, category by category - changed how I thought about my spending. Some patterns were obvious in hindsight, but others were **surprising...**

What surprised me most wasn't that I spent a lot in certain categories, but how *consistent* my spending patterns were, and how clearly that pattern emerged only when I could see six months at a glance. The spreadsheet approach had felt like enough before; the dashboard approach felt like seeing my finances for the first time.

If you've ever asked "where did all my money go this month?" and felt frustrated without a clear answer, FAPP might give you that clarity. Importing your data and seeing your own spending patterns laid out honestly is, I think, genuinely useful.

## Features

- **Multi-bank import** - CSV adapters for Monzo and Bank of Scotland, normalised into one transaction model. Imports are atomic and deduplicated, so re-uploading an overlapping statement reconciles instead of doubling entries.

- **Automatic categorisation** - a deterministic merchant rule set, with a recategorisation pass that reapplies current rules to historical transactions without overwriting a category the bank or the user already set.

- **Transfer detection** - Internal transfers between a user’s own accounts inflate both income and expense totals if treated like normal transactions. A deterministic algorithm matches debit/credit pairs across different accounts using the same user, currency, amount and opposite direction within the configured 2-day date tolerance. Transfers are shown in the UI but excluded from income/expenditure analytics.

- **Analytics** - income/expenditure summaries, per-category and per-account breakdowns, monthly trends, largest expenses, and period-over-period comparison, all computed deterministically in SQL and Java over half-open date ranges.

- **Savings goals** - target, deadline and progress tracking, with a featured goal highlighted on the dashboard and remaining amount/percentage calculated server-side.

- **Pinned transactions & groups** - pin individual transactions or grouped sets for quick access from the dashboard.

- **Merchant money-flow view** - click any merchant in the transactions list to see a detailed breakdown: total money sent, total received, net flow, transaction count, and all matching transactions. Helps you understand your relationship with regular counterparties.

- **Transaction search** - filter and search the transaction list by merchant, category or account.

- **Account statements** - per-account statement history viewable by calendar month, with the ability to remove and re-import statements.

- **Savings Pot tracking** - Monzo's Savings Pot movements are categorised separately so they don't distort ordinary income/expenditure analytics.

- **Authentication** - HTTP Basic over a stateless API with BCrypt-hashed passwords; every request is scoped to the authenticated user, so one user's id in a path can never be used to read another's data.

- **Responsive UI** - a React dashboard with a user-toggleable light/dark theme, responsive from small phone screens up to desktop.

## Screens & user experience

**Dashboard** - A snapshot of financial health with summary cards (total balance, monthly income, monthly expenses, net savings), monthly income and spending trends, category breakdown, account breakdown, largest expenses, recent transactions, and the featured savings goal. Period and account filters let you narrow the view to any month or account. Each panel loads independently, so one API call failing doesn't blank the page.

**Transactions** - A sortable, filterable transaction list with merchant, category and amount, searchable by description. Click on a merchant name to open a detailed money-flow view showing total money sent, total received, net flow, transaction count, and all matching transactions. Pin individual transactions for quick access from the dashboard. Internal transfers are marked and excluded from the transaction count.

**Accounts** - Add or remove bank accounts, choose the bank and link name, and import CSV statements. View imported statements by calendar month, with the ability to remove a statement and re-import it. The import form shows how many rows were new and how many were already held.

**Savings Goals** - Create goals with a target amount and deadline. Each goal shows progress towards the target and time remaining. One goal is featured on the dashboard.

**Pinned Items** - Transactions or groups of transactions pinned for quick reference. Groups let you bundle related transactions (e.g., your three monthly subscriptions or quarterly payments) under one label.

## Architecture

```
React + TypeScript front-end  (nginx serves the bundle and proxies /api)
        |
        v
Spring Boot REST API ──> Spring Security: HTTP Basic, per-user scoping
        |
        v
Import pipeline:  bank adapters -> normalisation -> validation ->
        |         deduplication -> categorisation -> transfer detection
        v
PostgreSQL  (schema owned by Flyway, never by Hibernate)
        |
        v
Analytics, savings goals — deterministic, in Java and SQL
```

Two rules shape this design:

1. **Bank-specific logic lives only in adapters.** Adding a bank means adding an adapter, parsing that bank's CSV format, and producing `RawTransaction` objects. Everything downstream is bank-agnostic.

2. **Every financial calculation is deterministic and covered by tests.** With explicit arithmetic assertions. No floating-point calculations on money. No hidden state affecting a figure. Every analytics query is repeatable and provable.

The import pipeline is synchronous within a transaction, so a validation error rolls everything back. The frontend makes same-origin requests to `/api` and Flyway migrations run on every startup, so deployment is a matter of starting the container.

## Technology stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Backend | Spring Boot 3.5 (Web, Data JPA, Validation, Security) |
| Database | PostgreSQL 17, schema owned by Flyway migrations |
| Build | Maven (via Maven Wrapper) |
| Backend testing | JUnit 5, AssertJ, Testcontainers |
| Frontend | React 19, TypeScript 5.9, Vite 7 |
| Frontend testing | Vitest, Testing Library |
| API documentation | OpenAPI 3.0, Swagger UI, springdoc-openapi |
| Deployment | Docker, Docker Compose, nginx |

## Data and financial modelling

Money handling in a personal finance tool is where subtle bugs become visible only in real data. FAPP makes several deliberate choices to avoid those bugs:

- **Signed money convention** - All amounts are signed: negative for outflows, positive for inflows. This makes a category total or account balance the simple sum of its transactions, with no separate in/out distinction needed. Transfers are always paired, so the sum across all accounts is always zero.

- **BigDecimal precision** - Monetary amounts are stored and calculated as `BigDecimal`, never `double`. The database column is `numeric`, which supports arbitrary precision. Every calculation happens in Java where rounding is explicit and every arithmetic assertion in tests is exact.

- **Deduplication by fingerprint** - Each transaction gets a deterministic fingerprint (based on amount, date, currency and description). Imports deduplicate by fingerprint before hitting the database, so a statement can be re-imported and will correctly reconcile.

- **Transfer detection** - Transactions are matched pairwise across accounts: a £50 debit from checking and a £50 credit to savings (matching amounts, opposite directions, same currency, same user, within 2 days) become a single "transfer" and are excluded from income/expenditure analytics. The transfer is shown in the UI but doesn't affect savings rate or spending totals.

- **Normalisation pipeline** - Each bank adapter produces a `RawTransaction` (bank's raw data). A normalisation layer cleans merchant names, standardises dates and amounts, and produces a `ParsedStatement`. That's validated, deduplicated, and categorised before persisting.

- **Categorisation rules** - A deterministic rule set maps merchants to categories. The rule set can be updated without overwriting user recategorisations. The recategorisation pass re-applies rules to historical transactions, filling in category gaps from new rules while respecting any user-set category.

- **User scoping** - Transactions, accounts and goals belong to a single user. Queries never cross user boundaries. The database schema includes `user_id` columns, and the API enforces user-scoped access on requests.

- **Savings Pot handling** - Monzo's Savings Pot movements are categorised separately so they do not distort ordinary income/expenditure analytics.

## Security and privacy

- **Authentication** - HTTP Basic authentication with BCrypt-hashed passwords. Every request is validated and scoped to the authenticated user.

- **User isolation** - Financial entities (account, transaction, goal) belong to a single user. Paths like `/api/users/{userId}/goals/{goalId}` require authentication as that user; using another user's id returns 403. Ownership is checked on authenticated requests.

- **Raw bank identifiers not stored** - Account numbers and sort codes from CSV imports are not persisted as part of the account model or transaction data. Statement filenames, which often embed account identifiers, are not retained.

- **Inputs validated** - Amounts are validated to non-zero, dates to half-open ranges, merchant names to non-empty strings. The database enforces constraints. Error responses are stable (e.g., `INVALID_DATE_RANGE`, `DUPLICATE_STATEMENT`, `USER_NOT_FOUND`) and don't leak implementation details.

- **Fixtures and test data sanitised** - All test data uses fake merchants and fake amounts. No real transaction data anywhere in version control.

- **Secrets from environment only** - Database password, server port, any credential comes from an environment variable. `docker-compose.prod.yml` explicitly refuses to start without `FAPP_DB_PASSWORD` set. Nothing secret is ever committed.

- **Comprehensive security testing** - Dedicated security tests cover authentication, authorisation, cross-user access control, and boundary conditions. New features include security regression tests.

- **HTTPS on deployment** - HTTP Basic sends the password encoded in a header. Deployment must terminate TLS in front of the application. The deployment instructions make this explicit.

FAPP is not claimed to be production-grade or financially regulated software. It is a personal tool with security appropriate to that scope. A real banking app would need regulatory compliance, security audits and formal threat modelling.

## Testing and CI

Comprehensive test coverage across backend and frontend:

**Backend** (39 test files):
- Integration tests starting a real PostgreSQL instance through Testcontainers
- API tests covering all endpoints, authentication, and error cases
- Security tests for authentication edge cases, write-operation protection, and cross-user access control
- OpenAPI documentation tests to ensure Swagger/API docs stay consistent
- Statement import and parsing tests for both Monzo and Bank of Scotland
- Categorisation and transfer detection tests
- Analytics and savings goal calculations with explicit arithmetic assertions
- Domain model and persistence tests

Backend tests run through `./mvnw test` (which requires Docker for Testcontainers).

**Frontend**:
- Vitest test suite covering React components, state management, and user interactions
- TypeScript type-checking on every build (`tsc --noEmit`)
- Production build verification (`npm run build`)
- Tests use Testing Library for user-behavior-focused testing

**CI** (GitHub Actions):
- On every push to main and every pull request, automatically:
  - Backend: `./mvnw verify` (builds, tests, and packages)
  - Frontend: `npm ci`, `npm test`, `tsc --noEmit`, `npm run build`
- Failures block merges, so nothing broken reaches main

The test philosophy: domain calculations are tested with explicit assertions. API endpoints are tested across authentication, error handling, and business logic. Security decisions are tested with dedicated tests. Backend integration tests hit a real PostgreSQL database (via Testcontainers). Frontend tests cover components and user interactions.

## API documentation

FAPP exposes a documented REST API using OpenAPI 3.0 and provides a Swagger UI for interactive exploration.

**Local development**: start the backend and visit `http://localhost:8080/swagger-ui.html`. Every endpoint, parameter, request body and response is documented inline.

Analytics endpoints take `from` (inclusive) and `to` (exclusive) date parameters and an optional `accountId`. Errors return a stable code and message, e.g. `USER_NOT_FOUND`, `INVALID_DATE_RANGE`, `MIXED_CURRENCY_ACCOUNTS`, `DUPLICATE_STATEMENT`, `VALIDATION_FAILED`.

## Running locally

**Requirements:** Java 21, Node 20+, and Docker. Maven is not needed, the wrapper handles it.

```bash
# 1. PostgreSQL database
docker compose up -d

# 2. Backend API (Flyway applies migrations on startup)
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

```bash
# 3. Frontend, in a second terminal
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Create an account, add a bank account (choose Monzo or Bank of Scotland), and import a CSV statement exported from your bank.

Verify the setup:
```bash
curl http://localhost:8080/api/health
# {"status":"UP","application":"fapp"}
```

**Running tests:**

```bash
./mvnw test                   # Backend tests; requires Docker for Testcontainers
cd frontend && npm test       # Frontend tests
cd frontend && npm run build  # Type-check, then build to dist/
```

## Design decisions and engineering highlights

**Deterministic financial calculations.** Every number FAPP shows is the result of a deterministic query or calculation. No floating-point arithmetic on money, no caching that can get stale, no statistical estimates. Tests assert exact arithmetic so a rounding error or off-by-one mistake is caught immediately. This matters because someone may make a financial decision based on FAPP's output.

**Bank adapters.** Each bank's CSV format is different (different columns, different metadata, different transaction codes). Each adapter translates that format to a common `RawTransaction` model. The normalisation pipeline is completely bank-agnostic. Adding a new bank means adding an adapter, not touching the core logic.

**Deduplication by fingerprint.** Statements overlap (you export 1 Jun–30 Jun, then 15 Jun–15 Jul). Each transaction gets a deterministic fingerprint that lets the import pipeline idempotently merge overlapping statements. The same statement can be uploaded twice and produce the same result the second time.

**Transfer detection.** Internal transfers between a user's own accounts inflate both income and expense totals if treated like normal transactions. A deterministic algorithm matches debit/credit pairs across accounts (same date, same amount, same user) and marks them as transfers. Transfers are shown in the UI but excluded from income/expenditure analytics.

**Flyway migrations.** The database schema is owned by Flyway, not by Hibernate. Migrations are written by hand as SQL. This is more work upfront but makes the schema explicit, reviewable, and migratable in production without re-mapping entities.

**User ownership enforcement.** Queries and mutations are scoped to the authenticated user. The database schema includes `user_id` columns for scoping. The API enforces user ownership checks. There is no broad per-request filtering that could accidentally leak data.

**Single stylesheet.** No CSS-in-JS, no CSS modules, no framework. One `styles.css` with mobile-first design rules and media queries. The design system is understandable and changes are visible at a glance.

**HTTP Basic over HTTPS.** No token management, no session table, no logout backend logic. The browser holds the credentials for the browser tab only (sessionStorage) and sends them on each request. This is stateless for the backend, but it requires TLS so the password isn't sent in plaintext.

## Known limitations

- **Cross-currency aggregation:** Analytics refuse to sum unlike currencies rather than apply an exchange rate. If you have a GBP account and a EUR account, each is analysed separately. Aggregating them properly needs a reliable exchange-rate source, which FAPP doesn't have.

- **CSV import only:** Statement import is CSV-only, via the Monzo and Bank of Scotland adapters. There is no Open Banking / PSD2 API integration. You must export a CSV and upload it manually.

- **Supported banks:** Only Monzo and Bank of Scotland. Adding a bank requires writing an adapter and testing it; contributions are welcome.

- **Authentication:** HTTP Basic means the password travels in every request (encoded in the Authorization header). Deployment must terminate TLS in front of the application. A token scheme is the natural next improvement.

- **No metrics or logs:** There is no Prometheus endpoint, no log aggregation, and no alerting. The application logs to stdout; operational visibility would be added on deployment.

- **Single user per deployment:** This is a personal finance tool. There is no shared workspace or shared statement. One deployment serves one person.

## Future scope

The architecture supports natural extensions if needed:

- **Open Banking integration** — add an adapter that fetches transactions directly from an API instead of CSV
- **Token authentication** — replace HTTP Basic with JWT or similar
- **Categorisation learning** — track user recategorisations and improve the rule set
- **Budget tracking** — per-category monthly budgets and alerts
- **Recurring transactions** — detect and display subscriptions and regular payments
- **Data export** — export transactions or summaries as CSV

None of these are planned unless they solve a real problem. FAPP is feature-complete for its current scope.

## Project status

FAPP is a **complete, usable personal finance application** and a **software engineering portfolio project**. It is stable, tested, and ready to use on your own financial data.

It is not a commercial product or a mobile app. It is one person's tool for understanding their finances, built with good software engineering practices to demonstrate software engineering skill.

## About this project

Built by [Yevhenii Dovhyi](https://github.com/evgg12) as a personal finance tool and software engineering portfolio project.

Repository: [github.com/evgg12/FAPP](https://github.com/evgg12/FAPP)
