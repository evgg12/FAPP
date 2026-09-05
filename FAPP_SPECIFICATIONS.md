FAPP — FINANCIAL AGGREGATION & PLANNING PLATFORM
Portfolio Project Master Summary
================================

PURPOSE
-------
FAPP is a personal, privacy-conscious financial aggregation and planning platform.

The original idea is to build something genuinely useful for personal finances rather than a generic demo application. The main personal motivation is using two or more banks at the same time — Bank of Scotland and Monzo for example — and wanting one application that can bring transactions from both into a single, consistent view.

FAPP should eventually be usable by other people with multiple bank accounts as well.

The acronym is intentionally FAPP:
Financial Aggregation & Planning Platform.


CORE IDEA
---------
FAPP combines financial data from multiple bank accounts, normalises it into one unified transaction model, analyses spending/income patterns, tracks savings goals, and provides scenario-based financial planning. As well as nice statistical view on spendings in form of graphs and pie charts.

The application should INFORM the user rather than act as a financial adviser.

The user remains responsible for financial decisions.

Example questions FAPP should be able to answer:
- Where did most of my money go this month?
- How much did I spend on restaurants over the last six months?
- Why was this month more expensive than my historical average?
- Which payments were unusual compared with my own previous behaviour?
- How much am I saving on average?
- Am I on track for my car savings goal?
- What would happen to my savings timeline if I saved an extra £100 per month?
- What would happen to a goal if I made a hypothetical £1,200 purchase?


PRIMARY FEATURES
----------------

1. MULTI-BANK AGGREGATION
   Initially support:
   - Bank of Scotland
   - Monzo

   The architecture should be designed so additional banks can be added later without rewriting the core transaction system.

   Initial approach:
   - Import bank statements/CSV files.
   - Each bank can have its own parser/adapter.
   - Convert bank-specific formats into a common internal transaction model.
   - Validate imported records.
   - Deduplicate transactions.
   - Store normalised transactions in the central database.
   - Visualize them for user to be able to see graphics and charts for easy visual readings.


2. UNIFIED TRANSACTION MODEL
   Different banks may provide different statement formats.

   Intended pipeline:

   Bank-specific statement
          ->
   Bank adapter/parser
          ->
   Raw transaction
          ->
   Normalisation
          ->
   Validation
          ->
   Deduplication
          ->
   Unified transaction
          ->
   PostgreSQL

   The unified transaction model should contain the information required for analysis, such as:
   - transaction date
   - amount
   - currency
   - merchant/description
   - category
   - account
   - source bank
   - transaction type

   Avoid storing unnecessary personal information.


3. TRANSACTION CATEGORISATION
   Transactions should be categorised consistently across banks.

   Categories can include areas such as:
   - groceries
   - restaurants
   - transport
   - subscriptions
   - bills
   - shopping
   - entertainment
   - income
   - transfers
   - savings

   The categorisation system should be extensible and capable of handling different merchant descriptions.


4. FINANCIAL ANALYTICS
   FAPP should calculate deterministic financial metrics from the transaction database.

   Examples:
   - total income
   - total expenditure
   - net savings
   - spending by category
   - spending by bank/account
   - largest transactions
   - monthly trends
   - month-to-month changes
   - historical averages
   - recurring payments
   - transaction frequency
   - unusual transactions relative to the user's own historical behaviour

   Important principle:
   Critical calculations should be performed by the backend/application logic, not left to an LLM.


5. SAVINGS GOALS
   Users can create goals such as:

   Example:
   Car Fund
   Target: £8,000
   Current amount: £2,350
   Target date: June 2027

   FAPP should use historical income, spending and savings behaviour to provide projections.

   It can show:
   - current progress
   - average saving rate
   - projected completion date
   - whether the current trajectory reaches the target date
   - historical factors affecting the projection


6. FINANCIAL SIMULATOR / WHAT-IF ENGINE
   This is intended to be one of FAPP's signature features.

   Hypothetical scenarios should NOT modify the user's real transaction history.

   Examples:
   - What if I buy a £1,200 laptop?
   - What if I save an extra £100 per month?
   - What if my average monthly spending changes?
   - How would a hypothetical purchase affect my car-fund target date?

   The simulator should create temporary/projected states and compare them with the user's baseline.

   The user decides what to do; FAPP only provides analysis.


7. ANALYTICAL AI ASSISTANT
   The AI assistant should answer questions about the user's own financial data.

   Good use cases:
   - explain why spending increased
   - summarise category trends
   - compare months
   - explain unusual transactions
   - analyse progress toward goals
   - interpret structured financial metrics
   - answer natural-language questions about historical spending

   The AI should NOT:
   - act as a financial adviser
   - recommend investments
   - recommend financial products
   - tell the user what they should buy
   - make unsupported claims about fraud
   - make critical calculations that the deterministic backend should perform

   Example:
   If asked, "Should I stop eating out?", FAPP should provide factual analysis such as:
   "Restaurant spending this month is £X compared with a historical average of £Y."

   It should not make the decision for the user.


8. PRIVACY-FIRST DATA FLOW
   Financial information is sensitive.

   Intended data flow:

   Bank statement
        ->
   Secure upload
        ->
   Parser
        ->
   Normalisation
        ->
   Remove unnecessary personal information
        ->
   Transaction database
        ->
   Deterministic analytics
        ->
   Structured analytical data
        ->
   AI assistant

   The AI should receive only the information necessary to answer a question rather than raw bank statements containing unnecessary personal data.


TECHNOLOGY STACK
----------------

PRIMARY APPLICATION
- Java
- Spring Boot
- Spring Security
- REST API
- OpenAPI

DATABASE
- PostgreSQL
- SQL

FRONTEND
- React
- TypeScript

ASYNC / EVENT-DRIVEN COMPONENTS
- Apache Kafka

CACHING / FAST DATA
- Redis

AI / ANALYTICAL SERVICE
- Python
- FastAPI

CONTAINERS
- Docker
- Docker Compose

TESTING
- JUnit 5
- Mockito
- Testcontainers
- k6 for performance/load testing

CI/CD
- Git
- GitHub
- GitHub Actions

OBSERVABILITY
- OpenTelemetry
- Prometheus
- Grafana

SECURITY
- Spring Security
- OAuth2/JWT where appropriate
- Secure handling of financial data
- Input validation
- Authentication/authorisation
- Careful handling of uploaded statements

CLOUD / INFRASTRUCTURE (LATER STAGE)
- AWS
- Terraform

IMPORTANT:
Do not force every technology into the first version. Each technology should be introduced when it solves a real architectural or engineering problem.


HIGH-LEVEL ARCHITECTURE
-----------------------

React + TypeScript
        |
        v
Spring Boot REST API
        |
        +--> Spring Security / authentication
        |
        v
PostgreSQL
        |
        v
Transaction Engine
        |
        +--> Bank adapters/parsers
        +--> Normalisation
        +--> Validation
        +--> Deduplication
        +--> Categorisation
        |
        v
Analytics Engine
        |
        +--> Spending analysis
        +--> Income analysis
        +--> Recurring payments
        +--> Unusual-spending analysis
        +--> Savings calculations
        +--> Goal projections
        +--> Scenario simulation
        |
        +--> Kafka for appropriate asynchronous events
        |
        +--> Redis for appropriate caching
        |
        +--> Python/FastAPI analytical or AI service
                     |
                     v
              AI Financial Assistant


IMPLEMENTATION FROM SCRATCH
---------------------------

The project should be built incrementally.

PHASE 0 — PROJECT DEFINITION
- Define the scope and requirements.
- Define the main user journeys.
- Define the initial transaction model.
- Define the financial domain concepts.
- Define security/privacy requirements.
- Decide what is in the MVP and what is deliberately postponed.
- Set up Git repository and project documentation.


PHASE 1 — CORE BACKEND
- Create the Java/Spring Boot application.
- Establish the REST API structure.
- Establish PostgreSQL persistence.
- Define users/accounts/transactions and other core domain entities.
- Establish authentication and authorisation.
- Establish validation and error handling.
- Make the core transaction system work independently of AI.


PHASE 2 — MULTI-BANK IMPORT
- Define a provider-independent bank adapter concept.
- Implement Bank of Scotland statement import.
- Implement Monzo statement import.
- Map each bank's format into the common transaction model.
- Add validation.
- Add duplicate detection.
- Handle malformed or unexpected statement data.
- Make the import process extensible for future banks.


PHASE 3 — FINANCIAL ANALYTICS
- Build deterministic financial calculations.
- Add category aggregation.
- Add monthly summaries.
- Add historical comparisons.
- Add recurring payment identification.
- Add unusual-spending detection based on the user's own history.
- Expose the results through the API.


PHASE 4 — FRONTEND
- Create the React/TypeScript application.
- Build authentication screens.
- Build account/bank views.
- Build transaction views.
- Build import workflow.
- Build financial dashboard.
- Build spending/category visualisations.
- Build savings-goal views.
- Build scenario simulation interface.


PHASE 5 — SAVINGS + SIMULATION
- Add savings goals.
- Calculate historical saving rates.
- Project goal completion.
- Implement hypothetical scenarios.
- Ensure simulations do not alter real financial records.
- Allow baseline vs scenario comparisons.


PHASE 6 — AI ASSISTANT
- Create the Python/FastAPI service if separation is useful.
- Define a structured interface between the analytical backend and AI layer.
- Provide the AI with relevant calculated metrics rather than raw unnecessary financial data.
- Support natural-language financial questions.
- Keep deterministic calculations outside the LLM.
- Add guardrails so the assistant remains analytical rather than becoming financial advice.


PHASE 7 — EVENT-DRIVEN / PERFORMANCE FEATURES
Introduce Kafka where asynchronous processing is genuinely useful.

Potential events:
- statement imported
- transaction normalised
- transaction categorised
- analytics recalculated
- goal projection updated

Introduce Redis where repeated reads or expensive calculations benefit from caching.

Do not add Kafka or Redis purely for CV keywords.


PHASE 8 — TESTING
- Unit test business logic.
- Test API behaviour.
- Test bank import adapters.
- Test normalisation and deduplication.
- Test financial calculations.
- Test scenario calculations.
- Use integration testing against PostgreSQL.
- Use Testcontainers where appropriate.
- Test authentication/authorisation.
- Add performance/load testing with k6 where useful.


PHASE 9 — CI/CD
- Use GitHub for version control.
- Establish a branching/commit workflow.
- Create GitHub Actions pipelines.
- Run automated tests on changes.
- Build application/container images.
- Add quality checks.
- Eventually deploy automatically to the chosen infrastructure.


PHASE 10 — OBSERVABILITY
- Add structured application logging.
- Add OpenTelemetry instrumentation.
- Add metrics.
- Use Prometheus for metrics collection.
- Use Grafana for dashboards.
- Add health checks.
- Monitor application and infrastructure behaviour.


PHASE 11 — DEPLOYMENT
Initial deployment can be container-based.

Later:
- AWS infrastructure
- Infrastructure as Code with Terraform
- production-style deployment
- HTTPS
- domain/DNS
- backups
- secrets management
- monitoring
- recovery procedures

The deployment architecture should be documented rather than treated as an afterthought.


SEPARATE SELF-HOSTING PROJECT
-----------------------------

A Raspberry Pi 5 + M.2 SSD can be developed as a separate infrastructure portfolio project.

Purpose:
Build a personal self-hosted platform capable of running FAPP and other projects.

Potential components:
- Linux
- Docker
- Docker Compose
- reverse proxy
- HTTPS
- domain/DNS
- firewall
- SSH
- persistent SSD storage
- backups
- container health checks
- monitoring
- GitHub Actions deployment

The relationship between the projects:

FAPP = the software application
Raspberry Pi platform = infrastructure that can host the application

This creates a strong portfolio story:
"I built the software and built the infrastructure that runs it."


DESIGN PRINCIPLES
-----------------

1. REAL USEFULNESS
   Build features that solve an actual personal problem.

2. MULTI-BANK FIRST
   The system should not be designed around one bank.
   Bank-specific logic belongs in adapters/parsers.

3. PROVIDER INDEPENDENCE
   The core transaction and analytics system should not care which bank produced a transaction.

4. DETERMINISTIC FINANCE
   Financial calculations belong to application/business logic.

5. AI AS AN INTERPRETATION LAYER
   AI interprets structured results rather than becoming the source of truth for calculations.

6. PRIVACY
   Minimise unnecessary financial and personal data exposure.

7. EXTENSIBILITY
   Adding another bank should require adding an adapter/parser rather than rewriting the platform.

8. TESTABILITY
   Core financial logic must be easy to test independently.

9. OBSERVABILITY
   The system should make failures and performance problems visible.

10. SECURITY
   Financial data requires authentication, authorisation, validation and secure data handling.

11. INCREMENTAL COMPLEXITY
   Start simple.
   Introduce Kafka, Redis, AI services, cloud infrastructure and observability when they have a concrete purpose.

12. NO FAKE FEATURES
   Every technology and feature should have a reason to exist and be defensible in an engineering interview.


MVP DEFINITION
--------------

The first genuinely usable version should probably contain:

- User authentication
- Multiple financial accounts
- Bank of Scotland CSV/statement import
- Monzo CSV/statement import
- Normalised transaction model
- Validation
- Deduplication
- Transaction categorisation
- Unified transaction history
- Spending/income summaries
- Category analytics
- Monthly comparisons
- Basic savings goals
- Basic goal projection
- React/TypeScript dashboard
- Spring Boot REST API
- PostgreSQL
- Docker
- Automated tests

Everything else can be layered on after this works reliably.


LATER / ADVANCED FEATURES
-------------------------

- Kafka event processing
- Redis caching
- Python/FastAPI AI service
- Natural-language financial assistant
- Unusual-spending analysis
- Advanced scenario simulation
- OpenTelemetry
- Prometheus/Grafana
- k6 performance testing
- GitHub Actions CI/CD
- AWS deployment
- Terraform
- Additional banks
- Possible Open Banking integrations


PORTFOLIO / CV POSITIONING
--------------------------

Project title:

FAPP — Financial Aggregation & Planning Platform

Suggested CV subtitle:

Personal Software Engineering Project | Java, Spring Boot, PostgreSQL, React/TypeScript, Docker

Core CV story:

Built a privacy-conscious multi-bank financial platform that consolidates and normalises transaction data from multiple accounts, initially supporting Bank of Scotland and Monzo. The platform provides deterministic financial analytics, savings-goal projections and what-if scenario simulation, with an analytical AI assistant layered over structured financial data.

The project demonstrates:
- Java/Spring Boot backend development
- REST API design
- PostgreSQL/SQL
- React/TypeScript
- data modelling
- data ingestion and normalisation
- validation and deduplication
- financial-domain business logic
- testing
- security
- event-driven architecture
- caching
- AI integration
- Docker
- CI/CD
- observability
- infrastructure/deployment
- software architecture


INTERVIEW STORY
---------------

If asked why FAPP exists:

"I use Bank of Scotland and Monzo at the same time, so I wanted one place where I could aggregate transactions from both accounts instead of analysing them separately. I started from that personal problem and designed the application so the core system is bank-independent, meaning additional banks can be added through adapters without changing the underlying transaction and analytics model."

If asked why AI is used:

"The AI isn't responsible for calculating financial figures. The application calculates the financial metrics deterministically and then gives the AI structured results to interpret and explain in natural language. That keeps the financial logic predictable and testable."


WHAT NOT TO FORGET
------------------

FAPP is NOT intended to be:
- a banking replacement
- an investment platform
- a financial adviser
- a fraud-detection authority
- an app that tells users what they should buy
- an AI chatbot with a financial UI attached

FAPP IS intended to be:
- a multi-bank financial aggregation platform
- a personal transaction management system
- a deterministic financial analytics engine
- a savings-goal and scenario-planning tool
- a privacy-conscious analytical assistant
- a serious software engineering portfolio project


BUILD ORDER AT A GLANCE
-----------------------

1. Requirements and domain model
2. Git repository/project structure
3. Spring Boot backend
4. PostgreSQL
5. Authentication/security
6. Unified transaction model
7. Bank of Scotland adapter
8. Monzo adapter
9. Validation + deduplication
10. Categorisation
11. Analytics engine
12. Savings goals
13. Scenario simulator
14. React/TypeScript frontend
15. Testing/Testcontainers
16. Docker
17. AI/FastAPI layer
18. Kafka where justified
19. Redis where justified
20. GitHub Actions CI/CD
21. OpenTelemetry + Prometheus + Grafana
22. Self-hosted deployment
23. AWS/Terraform as a later expansion


FINAL PROJECT IDENTITY
----------------------

Name:
FAPP

Full name:
Financial Aggregation & Planning Platform

Primary purpose:
Aggregate and analyse financial transactions from multiple banks in one privacy-conscious platform.

Initial banks:
Bank of Scotland + Monzo

Primary backend:
Java + Spring Boot

Database:
PostgreSQL

Frontend:
React + TypeScript

Advanced services:
Python + FastAPI

Infrastructure:
Docker / Docker Compose, later self-hosted Raspberry Pi and potentially AWS/Terraform

Engineering focus:
Software architecture, data ingestion, normalisation, business logic, testing, security, asynchronous processing, observability, CI/CD and deployment.

The central idea to remember:
"I wanted one serious application that brings my Bank of Scotland and Monzo finances together, understands my own financial history, helps me track goals and lets me test hypothetical scenarios — without pretending to be a financial adviser."
