# CLAUDE.md — Engineering rules for FAPP

FAPP is a multi-bank financial aggregation and planning platform.
Full requirements live in `FAPP_SPECIFICATIONS.md`; this file holds the rules that
govern *how* code is written here. Read the specification for scope, not this file.

## Build and test

```bash
./mvnw test       # always use the wrapper, never a global mvn
./mvnw package
docker compose up -d   # PostgreSQL only
```

Java 21, Spring Boot 3.5, package root `com.fapp`.

## Non-negotiable rules

1. **Flyway owns the schema.** Migrations in `src/main/resources/db/migration` as
   `V<n>__<description>.sql`, forward-only, never edited after being applied.
   `ddl-auto` stays `none`; Hibernate never creates, updates or validates DDL.
   JPA entities are written to match the migration, not the other way round.
2. **Deterministic finance.** Every financial figure — totals, averages, projections,
   scenario outcomes — is calculated in Java and covered by tests. An LLM never
   calculates, estimates or rounds a financial value.
3. **Bank-specific logic lives only in adapters.** The transaction model, persistence
   and analytics must not contain a single branch on which bank a record came from.
   Adding a bank means adding an adapter, nothing else.
4. **Money is never a `double`.** Use `BigDecimal` with an explicit currency, and be
   explicit about scale and rounding.
5. **Simulations never touch real data.** What-if scenarios compute projected states in
   memory and compare against the baseline; they never write to transaction history.
6. **Privacy by minimisation.** Store only what analysis needs. Never persist or log
   account numbers, sort codes, card numbers or raw statement rows. The AI layer receives
   calculated metrics, never raw statements.
7. **No secrets in the repository.** Configuration comes from environment variables with
   safe non-secret defaults; `.env` is git-ignored.
8. **Validate at the boundary.** Every request body and uploaded statement is validated
   before it reaches business logic. Malformed statement data is rejected with a clear
   error, never partially imported.
9. **Test the financial logic.** Normalisation, deduplication, categorisation, analytics
   and projections need unit tests with explicit arithmetic assertions before they are
   considered done.
10. **The assistant analyses, it does not advise.** No investment, product or purchase
    recommendations, and no fraud claims — factual comparison against the user's own history only.

## Scope discipline

- Build in the phase order given in `FAPP_SPECIFICATIONS.md`. Do not implement a later
  phase because it is convenient.
- Do not add a dependency, layer, interface or abstraction until there is a concrete
  problem in the current phase that requires it. Kafka, Redis, the AI service,
  observability and cloud infrastructure stay out until they are genuinely justified.
- Prefer deleting an abstraction over generalising it. One implementation needs no interface.
- Every technology in this repository must be defensible in an interview.

## Current state

Spring Boot application with the unified transaction model in place: entry point,
`/api/health`, PostgreSQL via Docker Compose, and a Flyway-owned schema of `users`,
`accounts`, `statement_imports`, `transactions` and `transfers` (`V2`). JPA entities and
a `Money` value object map onto that schema. Tests cover the domain rules as units and
verify the migration, the database constraints and the JPA mappings against real
PostgreSQL via Testcontainers, which `./mvnw test` therefore requires Docker for.

There are no repositories yet — no query requirement exists — and no adapters, no
analytics, no savings goals, no security and no frontend. Next up is Phase 2: the bank
adapter concept plus Bank of Scotland and Monzo statement import, which is what will
populate `fingerprint`, `occurrence` and `statement_imports` and give the transfer
detection logic a caller.
