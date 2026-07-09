# Budget Tracker — Local Setup + Railway Redeploy Plan

## Context / decisions locked in
- Local backend connects **directly to Supabase Postgres** (no local Docker DB).
- Teller sandbox creds are gone — will regenerate from the Teller dashboard.
- Railway project still exists but "needs a few things fixed" (TBD once we look at it).
- This sandbox is not Gaurav's actual machine — output here is config files +
  copy/paste instructions he runs on his Intel MacBook, not a live local run.

## Open items I need from Gaurav before/while executing
- [ ] Supabase project connection info: host, port, db name, user, password
      (and whether to use the **direct connection** (5432) or **pooler**
      (6543 / pgbouncer) — matters for Hikari settings)
- [ ] Confirm Supabase DB already has the schema (from `db/migration/V3-V7`
      SQL run manually before) or if it's a fresh Supabase project needing
      those scripts re-applied
- [ ] What specifically is broken on the current Railway project (build
      failing? env vars missing/stale? domain/CORS mismatch? something else)
- [ ] New Teller sandbox Application ID + cert.pem/key.pem once regenerated
- [ ] Anthropic API key for categorization (optional locally — can leave blank
      to disable, per existing `application-dev.properties` comment)
- [ ] JWT secret — reuse existing one from Railway env vars, or generate new
      (new secret invalidates any existing sessions/tokens, which is fine for
      a personal project)

## Plan

### 1. Backend: local profile pointed at Supabase
- [x] Add a `local` Spring profile (`application-local.properties`) that sets
      `spring.datasource.url` etc. from env vars (mirrors the existing prod
      pattern, not the hardcoded-localhost dev pattern)
- [x] Set `jwt.cookie-secure=false` and CORS to `http://localhost:5173` for
      local profile
- [x] Add `.env.example` (committed, no real secrets) documenting every env
      var the backend needs
- [x] Add `run-local.sh.example` (copy to gitignored `run-local.sh` with real
      values) that exports env vars and runs `mvn spring-boot:run`
- [x] `sslmode=require` included in the example `DATABASE_URL`
- [x] `ddl-auto=validate` (not `update`) for local profile — safety rail since
      local talks to the same live Supabase DB as prod
- [ ] **You still need to**: fill in `run-local.sh` with real Supabase/JWT/
      Teller values and actually run it

### 2. Frontend: local env
- [x] Removed the committed `.env` (was pointing at prod Railway URL) from
      git tracking, added `.gitignore` entry for `.env`
- [x] Added safe `.env.example`; instructions to copy to `.env.local`
      (already covered by existing `*.local` gitignore rule)
- [ ] **You still need to**: `cp .env.example .env.local` and fill in the new
      Teller sandbox app id

### 3. Teller sandbox regeneration (manual, on Gaurav)
- [ ] Log into Teller dashboard → regenerate/create sandbox application
- [ ] Download `certificate.pem` / `private_key.pem`
- [ ] Note new Application ID → goes in both backend `.env`/`run-local.sh`
      and frontend `.env.local`

### 4. Local smoke test
- [ ] Run backend locally against Supabase, confirm it boots and connects
- [ ] Run frontend (`npm install && npm run dev`)
- [ ] Register/login, load dashboard, confirm basic CRUD (categories/budgets)
      works end-to-end before touching Teller

### 5. Railway backend — audit & fix
- [x] Reviewed current Railway env vars — found and fixed:
      - `CORS_ALLOWED_ORIGINS` had a trailing slash on the Vercel URL, which
        breaks the exact-string origin match in `SecurityConfig` → real CORS
        bug (**you fixed this directly in Railway's dashboard**)
      - Deleted unused `PLAID_*` vars (**you fixed this directly in Railway**)
      - `TELLER_APP_ID` vs `TELLER_APPLICATION_ID` naming mismatch — turned
        out to be a non-issue, `teller.application-id` isn't read anywhere
        in the backend code (dead property)
      - `TELLER_KEYSTORE_PATH` / `TELLER_KEYSTORE_BASE64` both empty — no
        Teller mTLS in prod currently, expected until sandbox creds are
        regenerated
- [x] Found the actual cause of `/actuator/health` returning 500:
      `spring-boot-starter-actuator` was never added to `pom.xml`, so the
      endpoint didn't exist — requests fell through to Spring's static
      resource handler (`NoResourceFoundException`), which then got
      swallowed by the catch-all `@ExceptionHandler(Exception.class)` in
      `GlobalExceptionHandler` and reported as a misleading 500. Fixed both:
      added the actuator dependency, and added an explicit
      `NoResourceFoundException` handler so future unmapped-path hits report
      a real 404 instead of a fake 500.
      Confirmed `/actuator/health` is already `permitAll()`'d in
      `SecurityConfig`, so no security config change needed.
- [ ] **You still need to**: apply the updated backend patch, redeploy to
      Railway, confirm `/actuator/health` returns 200

### 6. Frontend deploy (Vercel)
- [ ] Confirm `VITE_API_BASE_URL` in Vercel project settings matches the
      fixed Railway backend URL
- [ ] Redeploy, smoke test against production

### 7. Documentation
- [x] Add a real `README.md` to the backend repo (setup, env vars, local run,
      deploy)
- [x] Same for frontend repo (kept the existing Vite boilerplate below the
      project-specific section rather than deleting it)

## Review section (filled in after execution)
_(to be completed once implementation is done — summary of what changed,
what was verified working, and any lessons for tasks/lessons.md)_
