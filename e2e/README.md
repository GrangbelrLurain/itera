# @itera/e2e

Playwright end-to-end tests of the admin web. They run against the backend's **`memory` profile**: in-memory
repositories with sample products (`DAD501` cost-priced, `TYO201` manually priced), commissions and no bookings.
No SQL Server is needed, and `POST /api/test/reset` (memory profile only, and only when `MEMORY_RESET_ENDPOINT=true`, which the
Playwright config and the compose `e2e` services set) puts the sample data back before every test.

## Run

One-time: install the browser.

```bash
pnpm --filter @itera/e2e install:browsers
```

Then build the stack once and run (Playwright starts both servers on ports 18080 / 13000, so they never clash with
the docker compose stack on 8080 / 3000):

```bash
pnpm --filter @itera/e2e prepare:stack      # admin build + backend boot jar
pnpm --filter @itera/e2e test               # headless; test:headed to watch
```

Against a stack that is already running (for example the compose `e2e` profile):

```bash
docker compose --profile e2e up -d --build backend-e2e admin-e2e
E2E_BASE_URL=http://localhost:13000 E2E_API_URL=http://localhost:18080 pnpm --filter @itera/e2e test
```

Everything in one go, with the other checks: `check_all.ps1 -E2E`.

## Writing tests

- `support/fixtures.ts`: `admin` (a signed-in page), `firstDeparture(code)`, `createBooking(...)` through the public
  API, and the automatic `freshData` reset. Import `test` and `expect` from there.
- Use `errorBanner(page)` / `successBanner(page)` for messages: Next.js adds its own `role="alert"` route announcer.
- Sample dates are relative to today, so look departures up (`firstDeparture`) instead of hard-coding dates.
- Tests share one backend and run one after another (`workers: 1`).
