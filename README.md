# FAPP — Financial Aggregation & Planning Platform

FAPP is a personal, privacy-conscious platform that brings transactions from several
bank accounts into one consistent view, analyses spending and income, tracks savings
goals and lets you test hypothetical financial scenarios.

FAPP **informs**. It is not a financial adviser, an investment platform or a banking
replacement — the user stays responsible for every financial decision.

## The problem it solves

Using more than one bank at the same time — Bank of Scotland and Monzo, for example —
means financial reality is split across separate apps with separate formats, separate
category names and no combined view. Answering a simple question such as *"where did
my money actually go this month?"* means exporting statements and reconciling them by
hand.

FAPP imports statements from each bank through a bank-specific adapter, normalises them
into a single transaction model, and then answers questions like:

- Where did most of my money go this month?
- How much did I spend on restaurants over the last six months?
- Why was this month more expensive than my historical average?
- How much am I saving on average, and am I on track for my car savings goal?
- What would happen to that goal if I saved an extra £100 per month, or made a £1,200 purchase?

## Current project status

**Phase 0–1 foundation.** The repository currently contains a runnable Spring Boot 3
skeleton and nothing more:

- Spring Boot 3.5 / Java 21 application with the Maven Wrapper
- Flyway migration infrastructure with a baseline migration (**no domain tables yet**)
- PostgreSQL configured through environment variables
- `docker-compose.yml` running PostgreSQL for local development
- `GET /api/health` liveness endpoint
- Context-load and endpoint tests (`./mvnw test`)

Not yet implemented: domain entities, bank import adapters, categorisation, analytics,
savings goals, the scenario simulator, authentication, the frontend and every later-phase
technology. See [FAPP_SPECIFICATIONS.md](FAPP_SPECIFICATIONS.md) for the full plan.

## Technology stack

Currently in use:

| Area     | Technology                              |
|----------|-----------------------------------------|
| Language | Java 21                                 |
| Backend  | Spring Boot 3.5 (Web, Data JPA, Validation) |
| Database | PostgreSQL 17, schema owned by Flyway   |
| Build    | Maven (via Maven Wrapper)               |
| Testing  | JUnit 5, Spring Boot Test               |
| Local infra | Docker Compose                       |

Intended later, each introduced only when it solves a real problem: Spring Security with
JWT, OpenAPI, React + TypeScript, Testcontainers, k6, GitHub Actions, Python/FastAPI for
the AI assistant, Kafka, Redis, OpenTelemetry/Prometheus/Grafana, and AWS/Terraform.

## MVP scope

The first genuinely usable version:

- User authentication
- Multiple financial accounts
- Bank of Scotland and Monzo statement import
- Normalised transaction model with validation and deduplication
- Transaction categorisation and a unified transaction history
- Spending/income summaries, category analytics, monthly comparisons
- Basic savings goals with projection
- React/TypeScript dashboard over the Spring Boot REST API
- PostgreSQL, Docker, automated tests

Deliberately postponed: the AI assistant, Kafka, Redis, unusual-spending analysis,
advanced simulation, observability stack, CI/CD and cloud deployment.

## High-level architecture

```
React + TypeScript  (later phase)
        |
        v
Spring Boot REST API  ──> Spring Security / authentication  (later phase)
        |
        v
Transaction Engine        Bank adapters/parsers -> normalisation ->
        |                 validation -> deduplication -> categorisation
        v
PostgreSQL  (schema owned by Flyway, never by Hibernate)
        |
        v
Analytics Engine          spending/income analysis, recurring payments,
        |                 savings calculations, goal projections, simulation
        v
Python/FastAPI AI service (later phase) -> AI financial assistant
```

Two rules shape this design. Bank-specific logic lives **only** in adapters, so adding a
bank never means changing the core model. Financial calculations are **deterministic** and
belong to application logic — the AI layer interprets calculated results, it never produces
the numbers.

## Running locally

Requirements: Java 21 and Docker. Maven is not needed — the wrapper handles it.
No `.env` file is required: `application.yml` and `docker-compose.yml` share the same
safe non-secret local defaults (database `fapp`, user `fapp`, password `fapp`).

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Run the application (Flyway applies migrations on startup)
./mvnw spring-boot:run

# 3. Verify
curl http://localhost:8080/api/health
# {"status":"UP","application":"fapp"}
```

Running from IntelliJ works the same way — start the container, then run
`FappApplication`. No run-configuration environment variables are needed.

Tests and build:

```bash
./mvnw test       # unit and web-layer tests, no database required
./mvnw package    # builds target/fapp-0.0.1-SNAPSHOT.jar
```

Configuration is environment-driven; **no credentials are committed**. The application
reads `FAPP_DB_HOST`, `FAPP_DB_PORT`, `FAPP_DB_NAME`, `FAPP_DB_USER`, `FAPP_DB_PASSWORD`
and `FAPP_SERVER_PORT`, and the same `FAPP_DB_*` variables configure the PostgreSQL
container — one name per setting, so the two sides cannot drift.

No environment file is tracked in this repository — not even a template. To override a
default, create your own `.env` (git-ignored) with any subset of the variables above and
export it before running:

```bash
cat > .env <<'EOF'
FAPP_DB_PASSWORD=your-local-password
FAPP_SERVER_PORT=8080
EOF
set -a && source .env && set +a   # docker compose reads .env automatically
```

Changing `FAPP_DB_PASSWORD` after the container's volume already exists does **not**
change the database password — PostgreSQL only applies `POSTGRES_PASSWORD` when the
volume is first initialised. Either update the role
(`docker exec fapp-postgres psql -U fapp -c "ALTER USER fapp WITH PASSWORD '<new>';"`)
or recreate the volume with `docker compose down -v`. A real deployment supplies these
as real environment variables, or sets `SPRING_DATASOURCE_URL` directly.

## Database migrations

Flyway owns the schema; `spring.jpa.hibernate.ddl-auto` is `none` and must stay that way.
Migrations live in `src/main/resources/db/migration` as `V<n>__<description>.sql`, are
forward-only, and are never edited once they have been applied.
