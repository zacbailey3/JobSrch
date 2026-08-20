# JobSrch Agent Guide

This file is the durable working context for agents contributing to JobSrch.
Use it together with `README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, and
`docs/SECURITY.md`. Verify the implementation before relying on a statement
that may have changed.

## Project purpose

JobSrch is a learning-focused portfolio application that helps new graduates
and candidates with zero to three years of experience discover suitable jobs,
save roles, track applications, manage resumes, and compare a resume with a job
description.

The user is building this project primarily to understand agentic programming.
Explain important decisions, tradeoffs, file locations, failure modes, and
verification steps. Prefer free and open-source solutions. Treat paid services
as optional upgrades, and recheck current free-tier terms before recommending
or deploying a hosted service.

## Collaboration rules

- Lead with the outcome, then explain the reasoning in clear language.
- Ask for validation before a choice materially changes architecture, security,
  data retention, deployment cost, or user-visible product behavior.
- Call out assumptions and ambiguous behavior so the user can learn from the
  implementation.
- Work in small, prioritized milestones and leave the repository in a verified
  state.
- Preserve unrelated user changes in a dirty worktree. Inspect `git status`
  before editing and do not reset or overwrite work that is outside the task.
- Update the relevant documentation when an API contract, security rule,
  architecture decision, environment variable, or operating procedure changes.
- IntelliJ IDEA Ultimate is not required. Maven Wrapper, npm, Docker Compose,
  Git, and the application tests are the source of truth.

## Technology and repository map

- Backend: Java 25 LTS, Spring Boot 4, Maven Wrapper
- Frontend: Angular 21, TypeScript, npm
- Data: MySQL 8.4, Flyway migrations
- Production containers: MySQL, ClamAV, Spring API, Angular/Nginx
- CI/security: GitHub Actions, Dependabot, CodeQL, Trivy, npm audit

Important locations:

```text
backend/src/main/java/com/jobsrch/
  auth/          Accounts, sessions, login, password reset
  job/           User-owned saved jobs
  application/   User-owned application pipeline
  profile/       Candidate profile and preferences
  resume/        Resume metadata, private storage, malware scanning
  analysis/      Bounded PDF/DOCX parsing and explainable local matching
  discovery/     Provider adapters, indexing, filtering, ranking, alerts
  config/        Security and typed configuration

backend/src/main/resources/
  application.yml
  application-local.yml
  application-prod.yml
  db/migration/  Append-only Flyway migrations

frontend/src/app/
  app.ts         Current MVP workspace coordination
  core/          Authentication and typed API clients

docs/
  ARCHITECTURE.md
  API.md
  SECURITY.md
```

Keep the modular-monolith boundaries. Controllers translate HTTP requests,
services own business rules and transactions, repositories query data, and
Angular uses typed API clients. Do not introduce microservices without a
specific, validated need.

## Product and discovery rules

- Optimize for legitimate early-career roles requiring zero to three years of
  experience.
- Greenhouse and Lever use public employer-board endpoints. USAJOBS and Adzuna
  require credentials and are optional when credentials are absent.
- Do not scrape LinkedIn or Indeed. Add sources only through documented APIs or
  employer-owned public job-board endpoints.
- Ordinary discovery searches should use the local indexed inventory. A direct
  company-board lookup may make a live provider request.
- Normalize all providers into the shared discovery model; provider JSON shapes
  must not leak into the UI.
- Preserve URL-based deduplication, stale-job expiration, source health
  auditing, broad relevance ranking, and the per-company result cap.
- Unknown country, workplace, date, experience, degree, or sponsorship data
  stays unknown. Do not turn missing evidence into a positive claim.
- Entry-level intent is determined by `ExperienceClassifier`, not merely by
  matching the words "junior" or "new grad."
- Treat common Engineer/Developer I titles as entry signals, III and above as
  senior, and II as neutral because employer conventions vary. Explicit
  experience requirements remain authoritative.
- USAJOBS may return offset-less timestamps. The provider parser currently
  interprets those timestamps as UTC; keep regression coverage for this shape.
- Restart the backend/importer after importer or provider parsing changes so
  indexed records are refreshed before evaluating search results.

## Security invariants

Read `docs/SECURITY.md` before changing authentication, authorization, uploads,
password reset, proxy handling, or production configuration.

- Authentication uses a signed JWT delivered only in the HttpOnly
  `JOBSRCH_SESSION` cookie. Do not expose or store the credential in Angular.
- Cookie-authenticated mutations require CSRF protection. Do not switch back to
  a security configuration that treats the cookie token as a header-only bearer
  token.
- Authentication does not replace ownership checks. User-owned records must be
  queried by both the record identifier and current user identifier.
- Password reset increments the account security version and invalidates all
  previously issued sessions.
- Production must fail closed for development secrets, insecure cookies,
  exposed reset tokens, non-HTTPS origins, missing reset-email configuration,
  or disabled malware scanning.
- Resume uploads remain private and accept only bounded PDF/DOCX input after
  signature, archive, malware, path, parser-resource, and ownership checks.
- Rate limiting is bounded but in-memory and suitable for one backend instance.
  Use a shared limiter such as Redis before horizontal scaling.
- Keep backend, MySQL, and ClamAV private behind the public HTTPS proxy.
- Never weaken a guard to make a failing test or deployment start. Diagnose the
  configuration or update the documented design intentionally.

## Secrets and environment files

- `.env` is local and ignored by Git. Never commit, paste, log, or echo its
  contents.
- Do not read or display secrets unless the exact task requires validating a
  specific setting. Prefer checking whether a value is present over printing it.
- Start from `.env.example` for documented variable names.
- Production must use a separate ignored `.env.production`; never copy local
  development values unchanged.
- USAJOBS API keys may contain trailing `=` characters. Preserve the key
  exactly, without the email label, colon, surrounding spaces, or quotes.
- Use the email registered with USAJOBS as `USAJOBS_EMAIL`.
- Resend production mail requires a verified sender domain. A free dynamic DNS
  hostname may be adequate for HTTPS but should not be assumed sufficient for
  SPF/DKIM email verification.

## Local development on Windows

Run commands from `D:\JavaProjects\JobSrch` in PowerShell.

Start MySQL with Docker:

```powershell
docker compose up -d
```

Start the backend:

```powershell
cd D:\JavaProjects\JobSrch\backend
.\mvnw.cmd spring-boot:run
```

If Docker is unavailable, use the built-in local database profile:

```powershell
cd D:\JavaProjects\JobSrch\backend
$env:SPRING_PROFILES_ACTIVE="local"
.\mvnw.cmd spring-boot:run
```

Start the frontend in a second terminal:

```powershell
cd D:\JavaProjects\JobSrch\frontend
npm.cmd start
```

Open `http://localhost:4200`. The Angular development server proxies `/api` to
the backend on port 8080.

## Required verification

Run the narrowest relevant tests while iterating. Before handing off a normal
code change, run the full affected suite:

```powershell
cd D:\JavaProjects\JobSrch\backend
.\mvnw.cmd test

cd D:\JavaProjects\JobSrch\frontend
npm.cmd test -- --watch=false
npm.cmd run build
```

For dependency or production-security changes, also run:

```powershell
cd D:\JavaProjects\JobSrch\frontend
npm.cmd audit --omit=dev

cd D:\JavaProjects\JobSrch
docker compose --env-file .env.production -f docker-compose.prod.yml config
```

Do not claim verification passed unless it ran successfully in the current
worktree. If a check cannot run, report the exact blocker and what remains
unverified. Do not expose secret values in test or Compose output.

## Database and contract changes

- Flyway owns the schema and Hibernate validates it.
- Never edit a migration that may have been applied. Inspect the migration
  directory and add the next sequential `V#__description.sql` file. The current
  latest migration is V9, so the next migration is normally V10.
- Update entities, repositories, services, tests, and API documentation as one
  coherent change.
- Update `docs/API.md` whenever request or response shapes, status codes, or
  authentication requirements change.
- Add regression tests for every bug fix, especially provider parsing,
  authorization, CSRF, session invalidation, rate limits, and hostile uploads.

## Prioritized roadmap

1. **Secure accounts and user data — completed.** Secure cookie sessions, CSRF,
   issuer validation, reset-session invalidation, bounded rate limiting,
   production fail-safe validation, upload scanning/parser limits, non-root
   containers, dependency updates, and security CI are implemented.
2. **Make job inventory reliable — active improvement area.** Continue measuring
   provider import counts, freshness, coverage, duplicates, relevance, and
   early-career classification. Prefer evidence from import audits and stored
   data over guesses about provider credentials.
3. **Establish recoverable production infrastructure — not deployed.** The
   intended free portfolio path is an Oracle Cloud Always Free VM if still
   available, Docker/Compose, a production-only environment file, the existing
   four-container stack, a hostname, Caddy or Nginx HTTPS, and automated MySQL
   plus resume-file backups. Recheck official free-tier terms first.
4. **Verify the main user journey.** Improve responsive UX and end-to-end
   coverage for registration, discovery, saving, applying, resume upload,
   analysis, password reset, and account deletion.
5. **Launch gradually and monitor.** Activate pushed GitHub CI, configure free
   uptime monitoring, review privacy/retention language, test backup restoration,
   and observe provider/import health before treating the site as publicly
   available.

The production Compose file currently defines four services. A public Caddy
reverse proxy would be an additional edge service unless installed directly on
the host. Uptime monitoring, public deployment, and automated backup/restore
testing have not yet been completed.

## Definition of done

A change is complete when:

1. The requested behavior works and preserves ownership/security invariants.
2. Relevant regression tests exist and all affected test/build commands pass.
3. No secrets or generated artifacts are staged.
4. API, architecture, security, setup, and environment documentation are
   updated where applicable.
5. `git diff` contains only intentional changes, and the handoff clearly states
   what changed, what was verified, and any remaining limitation.
