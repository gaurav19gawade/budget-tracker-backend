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

### 8. Prod bug: sync hangs at "Categorizing" (found live, 2026-07-09)
- [x] Root cause #1: `AnthropicCategorizationService`'s `RestTemplate` field
      is autowired by type, and Spring resolves the ambiguity between the
      two `RestTemplate` beans (`restTemplate` in
      `BudgetTrackerBackendApplication`, `tellerRestTemplate` in
      `TellerConfig`) by matching the field/parameter name — so it actually
      gets the plain default bean, not the Teller one. Not itself a bug, but
      worth knowing.
- [x] Root cause #2 (the real bug): **neither** `RestTemplate` bean had any
      connect/read timeout configured. Once `ANTHROPIC_API_KEY` was added on
      Railway, the categorization step made a real HTTP call that could hang
      indefinitely on any network hiccup — exactly matching the observed
      "stuck spinning on Categorizing forever" symptom. Same latent bug
      existed on the Teller HTTP client(s) too (relevant once Teller sandbox
      creds are back).
- [x] Fixed: added explicit connect (10s) / read (45s) timeouts to all three
      RestTemplate construction paths (default bean, Teller mTLS path,
      Teller plain-fallback path). A hung external API now fails fast with a
      clear error instead of hanging the UI forever.
- [ ] **You still need to**: apply this update, redeploy, retest the resync
      flow with the Anthropic key in place

### 9. Bug: categorization barely working (0/62 keyword, 1/62 LLM)
- [x] Root cause: your actual categories (12 total: Amazon, Credit Card Payment,
      Entertainment, Food Delivery, Groceries, HOA, Internal Transfer, Internet,
      Misc, Restaurants, Shopping, Zelle) don't match the ~23 hardcoded
      canonical category names the keyword rules AND the LLM prompt's "RULES"
      section were written against (Salary, Utilities, Gas, Transportation,
      Mortgage, Healthcare, Bank Fee, Transfer, etc. — most don't exist for
      you; "Transfer" is named "Internal Transfer" in your data).
      The LLM prompt told Claude "use ONLY these 12 categories" then
      immediately gave detailed matching rules for ~23 different ones —
      contradictory instructions, so Claude often answered with a category
      name that isn't actually yours, and `parseResponse` correctly (by
      design) discards anything not in your real list — hence almost
      everything got dropped silently.
- [x] Fixed `AnthropicCategorizationService.buildPrompt()` to only include
      rule text for categories that actually exist for the user (with alias
      resolution for "Transfer" → "Internal Transfer"), and added an explicit
      fallback to your "Misc" category instead of letting the model invent
      an invalid name.
- [x] Fixed `CategorizationService.findByKeyword()` the same way — keyword
      rule matches now also try the "Internal Transfer" alias when "Transfer"
      isn't found, so those get caught in the free keyword pass instead of
      needing an LLM call.
- [ ] **Known gap, not fixed (by design choice)**: you're missing categories
      that will likely be genuinely useful once real Chase/BofA data flows in
      — especially **Utilities**, **Gas**, **Transportation**, **Salary**.
      Worth adding these yourself via the Categories page before the fresh
      resync, or they'll all fall to "Misc"/null.
- [ ] **You still need to**: apply this update, redeploy, then do the fresh
      resync and check real categorization coverage

_(to be completed once implementation is done — summary of what changed,
what was verified working, and any lessons for tasks/lessons.md)_
