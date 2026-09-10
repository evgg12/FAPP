# FAPP — Financial Analysis & Planning Platform

A full-stack personal finance dashboard that pulls transactions from multiple UK
bank accounts into one consistent view, categorises and analyses them, and tracks
progress towards savings goals.

FAPP **informs** — it is not a financial adviser, an investment platform or a
banking replacement. Every figure shown is a deterministic calculation over the
user's own imported statements.

## The problem it solves

Using more than one bank at once — Bank of Scotland and Monzo, for example — means
financial reality is split across separate apps, separate CSV formats and separate
category names, with no combined view. Answering *"where did my money actually go
this month?"* means exporting statements and reconciling them by hand.

FAPP imports each bank's statement through a bank-specific adapter, normalises it
into a single transaction model, and answers questions like:

- Where did most of my money go this month, and how does it compare to last month?
- How much did I spend on restaurants over the last six months?
- How much am I saving on average, and am I on track for my car savings goal?

## Features

- **Multi-bank import** — CSV adapters for Monzo and Bank of Scotland, normalised
  into one transaction model. Imports are atomic and deduplicated, so re-uploading
  an overlapping statement reconciles instead of doubling entries.
- **Automatic categorisation** — a deterministic merchant rule set, with a
  recategorisation pass that reapplies current rules to historical transactions
  without overwriting a category the bank or the user already set.
- **Transfer detection** — money moved between a user's own accounts is matched,
  linked and excluded from income/expenditure totals so it can't inflate them.
- **Analytics** — income/expenditure summaries, per-category and per-account
  breakdowns, monthly trends, largest expenses, and period-over-period comparison,
  computed in SQL/Java over half-open date ranges.
- **Savings goals** — target, deadline and progress tracking, with a featured goal
  highlighted on the dashboard and remaining amount/percentage calculated server-side.
- **Pinned transactions & groups** — pin individual transactions or grouped sets
  for quick access from the dashboard.
- **Transaction search** — filter and search the transaction list by merchant,
  category or account.
- **Account statements** — per-account statement history with a period picker for
  reviewing any custom date range.
- **Authentication** — HTTP Basic over a stateless API with BCrypt-hashed
  passwords; every request is scoped to the authenticated user, so one user's id
  in a path can never be used to read another's data.
- **Light/dark theme** — a responsive React dashboard with a user-toggleable theme,
  working from a small phone up.

There is **no AI assistant** and no LLM dependency anywhere in the project — every
number FAPP shows is deterministic and covered by tests. Also deliberately absent,
because nothing here needs them: Kafka, Redis, a cache, Open Banking, OAuth,
Terraform and a cloud control plane.

## Technology stack

| Area            | Technology                                            |
|------------------|--------------------------------------------------------|
| Language         | Java 21                                                |
| Backend          | Spring Boot 3.5 (Web, Data JPA, Validation, Security)  |
| Database         | PostgreSQL 17, schema owned by Flyway migrations       |
| Build            | Maven (via the Maven Wrapper)                          |
| Backend testing  | JUnit 5, AssertJ, Testcontainers                       |
| Frontend         | React 19, TypeScript 5.9, Vite 7                       |
| Frontend testing | Vitest, Testing Library                                |
| Deployment       | Docker, Docker Compose, nginx                          |

The frontend has exactly two runtime dependencies — `react` and `react-dom`. Charts
are hand-drawn SVG rather than a charting library, and there is no CSS framework or
state-management library.

## Architecture

```
React + TypeScript  (nginx serves the bundle and proxies /api)
        |
        v
Spring Boot REST API ──> Spring Security: HTTP Basic, per-user scoping
        |
        v
Import pipeline           bank adapters -> normalisation -> validation ->
        |                 deduplication -> categorisation
        v
PostgreSQL  (schema owned by Flyway, never by Hibernate)
        |
        v
Analytics, savings goals, transfer detection — deterministic, in Java and SQL
```

Two rules shape this design: bank-specific logic lives **only** in adapters, so
adding a bank means adding an adapter and nothing else; and every financial
calculation is **deterministic** and covered by tests with explicit arithmetic
assertions.

## Running locally

Requirements: Java 21, Node 20+, and Docker. Maven is not needed — the wrapper
handles it. No `.env` file is required for local development.

```bash
# 1. PostgreSQL
docker compose up -d

# 2. The API — Flyway applies migrations on startup
./mvnw spring-boot:run

# 3. The dashboard, in a second terminal
cd frontend && npm install && npm run dev
```

Open http://localhost:5173, create an account, add a bank account and import a CSV
statement exported from Monzo or Bank of Scotland.

```bash
curl http://localhost:8080/api/health
# {"status":"UP","application":"fapp"}
```

## Tests and build

```bash
./mvnw test                   # backend test suite; requires Docker for Testcontainers
./mvnw package                # target/fapp-0.0.1-SNAPSHOT.jar
cd frontend && npm test       # frontend test suite
cd frontend && npm run build  # type-check, then dist/
```

Integration tests start a throwaway PostgreSQL instance through Testcontainers
rather than the Compose database, so running them never touches local development
data.

## Production deployment

Three containers — PostgreSQL, the backend, and nginx serving the frontend bundle
and proxying `/api` to the backend.

```bash
export FAPP_DB_PASSWORD='your-production-password'
docker compose -f docker-compose.prod.yml up -d --build
curl http://localhost:8081/api/health
```

- Secrets come from the environment only; `docker-compose.prod.yml` refuses to
  start without `FAPP_DB_PASSWORD` set, and nothing secret is committed to the repo.
- TLS should be terminated in front of the frontend container, since authentication
  is HTTP Basic.
- The database port is not published in the production compose file.
- Flyway migrates on startup; the backend container waits for PostgreSQL's health
  check before starting.

## API overview

All paths are under `/api`. Everything except `POST /api/users` and
`GET /api/health` requires authentication, and every `/api/users/{userId}/**` path
must match the authenticated user.

| Area | Examples |
|---|---|
| Health | `GET /health` |
| Users & auth | `POST /users`, `GET /auth/me`, `GET /users/{userId}` |
| Accounts | `POST /accounts`, `GET /accounts/{accountId}`, `DELETE /accounts/{accountId}` |
| Statement import | `POST /accounts/{accountId}/statements`, `GET /accounts/{accountId}/statements` |
| Transactions | `GET /accounts/{accountId}/transactions`, `PATCH /transactions/{transactionId}/category` |
| Analytics | `GET /users/{userId}/analytics/{summary,categories,monthly,accounts,largest-expenses,comparison,savings-pot}` |
| Savings goals | `POST`/`GET /users/{userId}/goals`, `GET`/`PUT`/`DELETE /users/{userId}/goals/{goalId}` |
| Pinned items | `/pinned-groups`, `/pinned-transactions` endpoints |

Analytics endpoints take `from` (inclusive) and `to` (exclusive) date parameters
and an optional `accountId`. Errors return a stable code and message, e.g.
`USER_NOT_FOUND`, `INVALID_DATE_RANGE`, `MIXED_CURRENCY_ACCOUNTS`,
`DUPLICATE_STATEMENT`, `VALIDATION_FAILED`.

## Database migrations

Flyway owns the schema; `spring.jpa.hibernate.ddl-auto` is `none` and stays that
way. Migrations live in `src/main/resources/db/migration` as
`V<n>__<description>.sql`, are forward-only, and are never edited once applied.

## Privacy

Only what analysis needs is stored. No account numbers, sort codes, card numbers or
raw statement rows are persisted or logged, and statement filenames are discarded
because they routinely embed an account number. An import is identified by a hash
of its bytes.

## Known limitations

- Cross-currency accounts cannot be aggregated: analytics refuses to sum unlike
  currencies rather than apply an exchange rate it has no source for.
- Authentication is HTTP Basic, so TLS must be terminated in front of the
  application. A token scheme is the natural next change.
- Statement import is CSV-only, for the two supported banks. There is no Open
  Banking connection.
- There is no CI pipeline, no metrics endpoint and no log aggregation.
