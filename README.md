# Budget Tracker — Backend

Spring Boot 3.2 / Java 17 backend for the budget tracker app. Postgres
(hosted on Supabase), JWT auth (access + refresh cookies), Teller.io bank
sync, Anthropic-powered transaction categorization.

## Local setup

This connects **directly to your Supabase Postgres** (direct connection,
port 5432) — there is no local/Docker database.

1. Copy the env template and fill in real values:
   ```bash
   cp run-local.sh.example run-local.sh
   chmod +x run-local.sh
   ```
   Edit `run-local.sh` with:
   - `DATABASE_URL` / `DB_USERNAME` / `DB_PASSWORD` — from Supabase dashboard
     → Project Settings → Database → Connection string → **URI** (direct
     connection, not the pooler)
   - `JWT_SECRET` — reuse the value from your Railway project's env vars, or
     generate a new one with `openssl rand -base64 64`
   - `TELLER_APPLICATION_ID` / `TELLER_KEYSTORE_PATH` /
     `TELLER_KEYSTORE_PASSWORD` — from the Teller dashboard (sandbox app)
   - `ANTHROPIC_API_KEY` — optional, leave blank to disable categorization
     while testing

2. Run it:
   ```bash
   ./run-local.sh
   ```
   This activates the `local` Spring profile
   (`application-local.properties`), which:
   - Points at Supabase directly
   - Sets `spring.jpa.hibernate.ddl-auto=validate` (not `update`) since your
     local run talks to the **same live Supabase DB** as production — this
     is a safety rail so a local schema mismatch fails loudly instead of
     silently altering your real data. Bump to `update` only when you
     intentionally want Hibernate to apply a schema change.
   - Disables the scheduler by default (`SCHEDULING_ENABLED=false`) so you
     don't trigger sync/email jobs against real data every time you start
     the app locally
   - Uses non-secure JWT cookies (fine over local http)

3. Health check: `curl http://localhost:8080/actuator/health`

> ⚠️ Because local dev hits the same Supabase project as production, be
> careful with destructive actions (deletes, bulk syncs) while testing
> locally. If this becomes a problem, consider a second Supabase project
> for dev and swapping `DATABASE_URL`.

## Environment variables

See `.env.example` for the full list with descriptions.

## Deploying (Railway)

Set the same variables (`DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET`, `TELLER_*`, `ANTHROPIC_API_KEY`, `CORS_ALLOWED_ORIGINS`) in the
Railway project's environment variables, plus:
- `SPRING_PROFILES_ACTIVE=prod`
- `CORS_ALLOWED_ORIGINS` set to your actual Vercel frontend URL(s)

Railway builds via the committed `Dockerfile`.
