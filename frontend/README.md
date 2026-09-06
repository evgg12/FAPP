# FAPP frontend

A React + TypeScript dashboard over the FAPP REST API. Every figure it shows is
calculated by the backend; nothing financial is computed in the browser.

Two runtime dependencies — `react` and `react-dom`. No UI framework, no charting
library, no state library. One stylesheet (`src/styles.css`) holds the design system.

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

```bash
npm test                      # 38 tests
npm run build                 # type-check, then a production bundle in dist/
```

## Configuration

The app calls the API with **relative** paths (`/api/...`) and the Vite dev server
forwards them, so requests are same-origin and the backend needs no CORS configuration.

| Variable | Default | Purpose |
|---|---|---|
| `FAPP_API_URL` | `http://localhost:8080` | Where the dev server proxies `/api` |

```bash
FAPP_API_URL=http://192.168.1.20:8080 npm run dev
```

There is no build-time API URL. In production the bundle is served by nginx, which
proxies `/api` to the backend — see `Dockerfile` and `nginx.conf.template` here, and the
deployment section of the root README.

The backend authenticates with HTTP Basic. Credentials are held for the browser tab only
(`sessionStorage`) and sent on each request, so **serve this over HTTPS**. A token scheme
would remove the need to hold a password at all and is the right next change.

## Using it

1. **Sign in, or create an account.** Registration takes an email, a display name and a
   password of at least 12 characters. Signing in confirms the credentials and returns
   the user id everything else is scoped by — there is no id to paste.
2. **Add an account** under *Accounts*, choosing its bank. The bank is fixed at creation
   and decides which adapter reads its statements, which is why the import form has no
   format selector.
3. **Import a statement** — a CSV exported from Monzo or Bank of Scotland — into the
   selected account. The result shows how many rows were new and how many were already
   held, because re-importing an overlapping statement is a normal thing to do.
4. **Read the dashboard.** Summary cards, monthly income and spending, category and
   account breakdowns, largest expenses and recent transactions, over the period and
   account chosen in the toolbar — one calendar month, or the year so far.
5. **Track goals** under *Goals*. An account and everything imported into it can be
   removed under *Accounts*.

## Layout

- `src/api/` — the typed client and the response types. One `request()` adds the
  Authorization header and turns an error body into an `ApiError`.
- `src/hooks/useAsync.ts` — the loading/failed/loaded state every panel shares. Each
  panel loads independently, so one failing request shows an error in its own panel
  instead of blanking the page.
- `src/components/` — one file per panel. `Async.tsx` renders the three states so no
  panel repeats them.
- `src/format.ts` — display formatting and the named period scales. No arithmetic on a
  financial figure.
- `src/styles.css` — mobile-first. The base rules are the phone layout and the media
  queries widen it. Data tables collapse into one card per row below 700px, so no
  figure ends up behind a sideways scroll.
