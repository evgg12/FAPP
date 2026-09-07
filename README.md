# FAPP — Financial Aggregation & Planning Platform

FAPP brings transactions from several bank accounts into one consistent view, analyses
spending and income, tracks savings goals and lets you test hypothetical scenarios.

FAPP **informs**. It is not a financial adviser, an investment platform or a banking
replacement — every figure is a factual calculation over your own statements, and the
user stays responsible for every financial decision.

## The problem it solves

Using more than one bank at the same time — Bank of Scotland and Monzo, for example —
means financial reality is split across separate apps with separate CSV formats,
separate category names and no combined view. Answering *"where did my money actually
go this month?"* means exporting statements and reconciling them by hand.

FAPP imports each bank's statement through a bank-specific adapter, normalises it into a
single transaction model, and then answers questions like:

- Where did most of my money go this month?
- How much did I spend on restaurants over the last six months?
- How much am I saving on average, and am I on track for my car savings goal?
- What would happen to that goal if I saved an extra £100 a month, or made a £1,200 purchase?

## What is implemented

The application is complete and runnable end to end.

**Domain and schema.** Flyway owns the schema (`ddl-auto: none`). `users`, `accounts`,
`statement_imports`, `transactions`, `transfers`, `savings_goals`, with foreign keys that
carry redundant columns so ownership and currency invariants are enforced by PostgreSQL
rather than by application discipline. Money is a `BigDecimal` plus an explicit currency
at a fixed scale — never a `double`, and never rounded implicitly.

**Import pipeline.** Adapters for Monzo and Bank of Scotland CSV exports. Bank-specific
parsing lives only in the adapter; nothing downstream branches on which bank a row came
from. Imports are atomic — a malformed statement is rejected whole — and deduplicated by
external id where the bank provides one and by content fingerprint otherwise, so
re-uploading an overlapping statement reconciles instead of doubling.

**Categorisation.** A deterministic merchant rule set, plus a recategorisation pass that
reapplies today's rules to rows imported before them without overwriting a category the
bank or the user already set.

**Transfer detection.** Money moved between two of your own accounts is matched and
linked, then excluded from income and expenditure — otherwise a £500 move would inflate
both totals and corrupt net savings.

**Analytics.** Summary, per-category, per-month, per-account, largest expenses and
period comparison, all calculated in SQL and Java over half-open date ranges. Mixing
accounts of different currencies is refused with a clear error rather than answered
wrongly.

**Savings goals.** Goals track a target, a date and progress towards it, with the
remaining amount and percentage calculated by the backend.

**Authentication.** HTTP Basic over a stateless API, BCrypt-hashed passwords, and every
`/api/users/**` request checked against the authenticated user, so one user's id in a
path cannot be used to read another's figures.

**Frontend.** A React + TypeScript dashboard over that API: summary cards, an income and
spending chart, category and account breakdowns, largest expenses, transactions, goals
with progress, account removal, statement import and recategorisation. Responsive from a
small phone up, and every figure it displays is one the backend calculated.

There is **no AI assistant**. An earlier phase had one; it was removed deliberately, and
no LLM, no AI dependency and no provider credential remains anywhere in the project.
Every number FAPP shows is deterministic and covered by tests.

Also deliberately absent, because nothing here needs them: Kafka, Redis, a cache, an
Open Banking integration, OAuth, Terraform and a cloud control plane.

## Technology stack

| Area        | Technology                                             |
|-------------|--------------------------------------------------------|
| Language    | Java 21                                                |
| Backend     | Spring Boot 3.5 (Web, Data JPA, Validation, Security)  |
| Database    | PostgreSQL 17, schema owned by Flyway                  |
| Build       | Maven (via the Maven Wrapper)                          |
| Testing     | JUnit 5, AssertJ, Testcontainers                       |
| Frontend    | React 19, TypeScript 5.9, Vite 7                       |
| Frontend tests | Vitest, Testing Library                             |
| Deployment  | Docker, Docker Compose, nginx                          |

The frontend has exactly two runtime dependencies: `react` and `react-dom`. The chart is
hand-drawn SVG rather than a charting library.

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

Two rules shape this design. Bank-specific logic lives **only** in adapters, so adding a
bank means adding an adapter and nothing else. Financial calculations are
**deterministic** and covered by tests with explicit arithmetic assertions.

## Running locally

Requirements: Java 21, Node 20+, and Docker. Maven is not needed — the wrapper handles
it. No `.env` file is required: `application.yml` and `docker-compose.yml` share the
same safe non-secret local defaults (database `fapp`, user `fapp`, password `fapp`).

```bash
# 1. PostgreSQL
docker compose up -d

# 2. The API — Flyway applies migrations on startup
./mvnw spring-boot:run

# 3. The dashboard, in a second terminal
cd frontend && npm install && npm run dev
```

Then open http://localhost:5173, create an account, add a bank account and import a CSV
statement exported from Monzo or Bank of Scotland.

Health check:

```bash
curl http://localhost:8080/api/health
# {"status":"UP","application":"fapp"}
```

The dev server proxies `/api` to the backend, so the browser makes same-origin requests
and the backend needs no CORS configuration. Point it elsewhere with
`FAPP_API_URL=http://host:8080 npm run dev`.

## Tests and build

```bash
./mvnw test                   # 479 tests; requires Docker for Testcontainers
./mvnw package                # target/fapp-0.0.1-SNAPSHOT.jar
cd frontend && npm test       # 43 tests
cd frontend && npm run build  # type-check, then dist/
```

The integration tests start a throwaway PostgreSQL through Testcontainers rather than
using the Compose database, so running them never touches local development data. They
exist because Flyway owns the schema and `ddl-auto` is `none`: nothing else would notice
if an entity and a migration stopped agreeing.

## Production deployment

Three containers — PostgreSQL, the backend, and nginx serving the frontend bundle and
proxying `/api` to the backend. Nothing else.

```bash
export FAPP_DB_PASSWORD='<a real secret>'
docker compose -f docker-compose.prod.yml up -d --build
curl http://localhost:8081/api/health
```

The dashboard is then on `http://localhost:8081` (override with `FAPP_HTTP_PORT`).

Notes that matter for a real deployment:

- **Secrets come from the environment only.** `docker-compose.prod.yml` has no default
  for `FAPP_DB_PASSWORD` and refuses to start without it. Nothing secret is in the
  repository, in an image, or in any committed file.
- **Terminate TLS in front of it.** Authentication is HTTP Basic, so the password is
  sent on every request. Put the frontend container behind a TLS terminator.
- The database port is **not** published in the production compose file. Only the
  frontend is reachable.
- Flyway migrates on startup, and the backend container waits for PostgreSQL to be
  healthy before starting.
- `docker compose -f docker-compose.prod.yml build` does not run the test suites — the
  integration tests need a Docker daemon the image build has no access to. Run
  `./mvnw test` and `npm test` before building.
- Images run as a non-root user and both containers have a health check.

## Configuration

Configuration is environment-driven and **no credentials are committed**.

| Variable | Default | Used by | Purpose |
|---|---|---|---|
| `FAPP_DB_HOST` | `localhost` | backend | Database host |
| `FAPP_DB_PORT` | `5432` | backend, Compose | Database port |
| `FAPP_DB_NAME` | `fapp` | backend, Compose | Database name |
| `FAPP_DB_USER` | `fapp` | backend, Compose | Database user |
| `FAPP_DB_PASSWORD` | `fapp` locally, **required** in production | backend, Compose | Database password |
| `FAPP_SERVER_PORT` | `8080` | backend | Port the API listens on |
| `FAPP_API_URL` | `http://localhost:8080` | Vite dev server | Where `/api` is proxied in development |
| `FAPP_BACKEND_URL` | `http://backend:8080` | frontend container | Where nginx proxies `/api` |
| `FAPP_HTTP_PORT` | `8081` | Compose (production) | Published port for the dashboard |

The same `FAPP_DB_*` names configure both the application and the PostgreSQL container,
so the two sides cannot drift. To override a default locally, create a git-ignored
`.env`:

```bash
printf 'FAPP_DB_PASSWORD=your-local-password\n' > .env
set -a && source .env && set +a   # docker compose reads .env automatically
```

Changing `FAPP_DB_PASSWORD` after the container's volume exists does **not** change the
database password — PostgreSQL only applies `POSTGRES_PASSWORD` when the volume is first
initialised. Either update the role
(`docker exec fapp-postgres psql -U fapp -c "ALTER USER fapp WITH PASSWORD '<new>';"`)
or recreate the volume with `docker compose down -v`.

## API

All paths are under `/api`. Everything except `POST /api/users` and `GET /api/health`
requires authentication, and every `/api/users/{userId}/**` path must match the
authenticated user.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness |
| `POST` | `/users` | Register |
| `GET` | `/auth/me` | Who the credentials belong to |
| `GET` | `/users/{userId}` | The user |
| `POST` | `/accounts` | Add an account |
| `GET` | `/accounts/{accountId}` | One account |
| `DELETE` | `/accounts/{accountId}` | Remove an account and its history |
| `GET` | `/users/{userId}/accounts` | The user's accounts |
| `POST` | `/accounts/{accountId}/statements` | Import a CSV statement |
| `GET` | `/imports/{importId}` | An import's outcome |
| `DELETE` | `/imports/{importId}` | Remove one loaded statement and its transactions |
| `GET` | `/accounts/{accountId}/statements` | The statements loaded into an account |
| `GET` | `/accounts/{accountId}/transactions` | Transactions on an account |
| `PATCH` | `/transactions/{transactionId}/category` | Change one transaction's category |
| `POST` | `/users/{userId}/transactions/recategorise` | Reapply merchant rules |
| `GET` | `/users/{userId}/analytics/summary` | Income, expenditure, net |
| `GET` | `/users/{userId}/analytics/categories` | Per category |
| `GET` | `/users/{userId}/analytics/monthly` | Per month |
| `GET` | `/users/{userId}/analytics/savings-pot` | Savings-pot movement, excluded from categories |
| `GET` | `/users/{userId}/analytics/accounts` | Per account |
| `GET` | `/users/{userId}/analytics/largest-expenses` | Biggest outgoings |
| `GET` | `/users/{userId}/analytics/comparison` | One period against another |
| `POST`/`GET` | `/users/{userId}/goals` | Create / list savings goals |
| `GET`/`PUT`/`DELETE` | `/users/{userId}/goals/{goalId}` | One goal |

Analytics take `from` (inclusive) and `to` (exclusive) and an optional `accountId`.
Errors return a stable code and message: `USER_NOT_FOUND`, `INVALID_DATE_RANGE`,
`MIXED_CURRENCY_ACCOUNTS`, `DUPLICATE_STATEMENT`, `VALIDATION_FAILED`, and so on.

## Database migrations

Flyway owns the schema; `spring.jpa.hibernate.ddl-auto` is `none` and must stay that
way. Migrations live in `src/main/resources/db/migration` as `V<n>__<description>.sql`,
are forward-only, and are never edited once applied.

| Migration | Contents |
|-----------|----------|
| `V1__baseline.sql` | Establishes the migration baseline. No domain tables. |
| `V2__transaction_domain.sql` | `users`, `accounts`, `statement_imports`, `transactions`, `transfers`. |
| `V3__savings_goals.sql` | `savings_goals`. |
| `V4__user_credentials.sql` | Password hashes on `users`. |
| `V5__custom_categories.sql` | `CUSTOM` category plus its user-supplied label. |

## Privacy

Only what analysis needs is stored. No account numbers, sort codes, card numbers or raw
statement rows are persisted or logged, and statement filenames are discarded because
they routinely embed an account number. An import is identified by a hash of its bytes.

## Known limitations

- Cross-currency accounts cannot be aggregated: analytics refuses to sum unlike
  currencies rather than apply an exchange rate it has no source for.
- Authentication is HTTP Basic, so the password is sent on every request and TLS must be
  terminated in front of the application. A token scheme is the natural next change.
- Statement import is CSV-only, for the two supported banks. There is no Open Banking
  connection.
- There is no CI pipeline, no metrics endpoint and no log aggregation.

See [FAPP_SPECIFICATIONS.md](FAPP_SPECIFICATIONS.md) for the original requirements and
[CLAUDE.md](CLAUDE.md) for the engineering rules the code follows.
