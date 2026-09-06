# FAPP frontend

A plain React + TypeScript dashboard over the existing FAPP REST API. It is a working
interface, not a design exercise: every figure it shows is calculated by the backend, and
nothing financial is computed in the browser.

## Running it

Two processes. The backend first:

```bash
# from the repository root
docker compose up -d          # PostgreSQL
./mvnw spring-boot:run        # API on http://localhost:8080
```

Then the frontend:

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173
```

## Configuration

The app calls the API with **relative** paths (`/api/...`) and the Vite dev server
forwards them, so requests are same-origin and the backend needs no CORS configuration.

| Variable | Default | Purpose |
|---|---|---|
| `FAPP_API_URL` | `http://localhost:8080` | Where the dev server proxies `/api` to |

```bash
FAPP_API_URL=http://192.168.1.20:8080 npm run dev
```

For a production build (`npm run build` → `dist/`), serve `dist/` behind something that
routes `/api` to the backend. There is no build-time API URL to set.

## Using it

1. **Pick a user.** There is no authentication yet and no endpoint that lists users, so
   identity is an explicit id: create a user, or paste one you already have. The choice is
   remembered in the browser.
2. **Add an account**, choosing its bank. The bank is fixed at creation and is what
   decides which adapter reads that account's statements — there is no format picker on
   upload.
3. **Import a statement.** Select a single account and upload its CSV. The result shows
   how many rows were new and how many the account already held, so re-uploading an
   overlapping statement visibly reconciles rather than doubles.
4. **Set the period.** `from` is inclusive, `to` is exclusive. Both are always sent; the
   API never guesses a window. The default is the last twelve months.
5. **Read the dashboard.** Summary, category and monthly breakdowns, per-account totals,
   largest expenses, and the selected account's transactions. Selecting *All accounts*
   drops the account filter from the analytics requests.

Use the sanitised fixtures in `src/test/resources/monzo/statement.csv` and
`src/test/resources/bankofscotland/statement.csv` for a quick look. **Never put a real
bank statement in this repository.**

## Checks

```bash
npm run build    # type-checks with tsc, then builds
npm test         # Vitest: API client, formatting, component rendering
```

## What is not here

No authentication, no charts library, no AI assistant, no simulator, no savings goals.
Breakdowns are drawn as CSS bars rather than pulling in a charting dependency.
