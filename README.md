# JobSrch

JobSrch is a full-stack job search and application tracker focused on new
graduates and candidates seeking roles that require zero to three years of
experience.

## Stack

- Java 25 LTS and Spring Boot 4
- Angular 21
- MySQL 8.4 with Flyway migrations
- JWT email/password authentication delivered through secure HttpOnly cookies

## Current features

- Email/password registration and login
- Routed Settings with recent password confirmation, password changes,
  verified email changes, reset-email requests, and permanent deletion
- User-owned saved jobs with entry-level experience ranges
- Application pipeline with status updates and saved-role handoff
- Readable candidate profile with a separate editing mode
- PDF/DOCX resume upload with safe server-side filenames
- Explainable local resume-to-job matching with improvement suggestions
- Role-first indexed discovery across Greenhouse, Lever, USAJOBS, and Adzuna
- Company-balanced results so one employer cannot dominate a search
- Scheduled provider importing with URL deduplication and stale-job expiration
- Persistent import health history with per-provider and per-board outcomes
- Saved searches with in-app alerts for newly imported matches
- US-first country, workplace, freshness, and newest-first filters
- Early-career filters for internships, apprenticeships, new-grad roles,
  experience limits, degree language, and visa sponsorship
- Explainable suitability notes using posting evidence and candidate profile skills
- Optional filtering of positions already recorded as applications
- Dashboard totals for saved jobs, applications, interviews, and offers

## Project layout

```text
backend/
  src/main/java/com/jobsrch/
    auth/          Registration, login, and JWT creation
    job/           Saved job CRUD
    application/   Application pipeline CRUD
    profile/       Career preferences and personal career details
    resume/        Resume metadata and filesystem storage
    analysis/      Local resume text extraction and job matching
    discovery/     Greenhouse/Lever adapters and experience classification
    config/        Security and typed configuration
  src/main/resources/db/migration/
                  Versioned database schema changes
frontend/
  src/app/
    app.ts         Root router host
    workspace/     Shared authenticated workspace state and shell
    settings/      Secure account-preference interactions
    core/          Authentication and typed API clients
docs/
  ARCHITECTURE.md  Request flow, ownership rules, and extension points
  API.md           REST endpoint reference
```

The Java classes include Javadocs where a security rule, storage decision, or
cross-layer responsibility is not obvious. Straightforward getters and CRUD
methods are intentionally left uncluttered.

## Local development

Start MySQL:

```powershell
docker compose up -d
```

Start the API:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

If Docker Desktop is not running, use the built-in local database profile:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="local"
.\mvnw.cmd spring-boot:run
```

Start Angular in another terminal:

```powershell
cd frontend
npm.cmd start
```

Open `http://localhost:4200`. Angular proxies `/api` requests to the backend.

## Configuration

The defaults are suitable only for local development. Deployments should set:

| Variable | Purpose |
| --- | --- |
| `DB_URL` | MySQL JDBC connection URL |
| `DB_USERNAME` | MySQL application user |
| `DB_PASSWORD` | MySQL application password |
| `JWT_SECRET` | Secret of at least 32 bytes used to sign tokens |
| `PASSWORD_RESET_EXPOSE_TOKEN` | Shows reset codes locally; the production profile always forces this to `false` |
| `EMAIL_CHANGE_EXPOSE_TOKEN` | Shows email-change codes locally; the production profile always forces this to `false` |
| `RESEND_API_KEY` | Resend API key used for production password-reset email |
| `PASSWORD_RESET_FROM` | Sender on a domain verified with Resend |
| `FRONTEND_BASE_URL` | Public frontend URL used to build password-reset links |
| `AUTH_COOKIE_SECURE` | Local override; the production profile always forces secure HTTPS-only cookies |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins allowed to call the API |
| `RESUME_DIRECTORY` | Persistent private directory for uploaded resumes |
| `MALWARE_SCAN_ENABLED` | Optional locally; the production profile requires ClamAV scanning |
| `CLAMAV_HOST` / `CLAMAV_PORT` | ClamAV location; production Compose supplies the private sidecar |
| `PORT` | API port, default `8080` |
| `USAJOBS_EMAIL` | Email registered with the optional USAJOBS API |
| `USAJOBS_API_KEY` | Optional [USAJOBS API](https://developer.usajobs.gov/) key |
| `ADZUNA_APP_ID` | Optional [Adzuna API](https://developer.adzuna.com/) application id |
| `ADZUNA_APP_KEY` | Optional Adzuna application key |
| `JOB_IMPORT_ENABLED` | Enables scheduled indexing, default `true` |
| `JOB_IMPORT_FIXED_DELAY_MS` | Delay between imports, default six hours |
| `JOB_EXPIRE_AFTER_HOURS` | Marks jobs stale after this many hours, default `72` |
| `JOB_IMPORT_AUDIT_RETENTION_DAYS` | Retains import health history, default `30` days |

Greenhouse and Lever work without credentials. USAJOBS and Adzuna are skipped
until their environment variables are configured.

Production password reset and email verification use Resend. Its free plan is sufficient for a small
learning deployment, but sending to arbitrary recipients requires a domain you
control and DNS verification. Local development may keep
`PASSWORD_RESET_EXPOSE_TOKEN=true` and `EMAIL_CHANGE_EXPOSE_TOKEN=true`; the
production profile always disables both.

## Portfolio production

Create a separate production environment file from `.env.example`; do not reuse
the local `.env`. `.env.production` is ignored by Git. Replace every placeholder
and use public HTTPS URLs:

```powershell
docker compose --env-file .env.production -f docker-compose.prod.yml config
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

The production stack includes MySQL, the official multi-architecture ClamAV
Debian image, the Spring API, and the Angular Nginx container. Spring activates
the `prod` profile and refuses to start with development secrets, insecure
origins/cookies, exposed reset tokens, or disabled malware scanning. ClamAV's
initial virus-definition download may take several minutes.

The frontend publishes `WEB_PORT` on host loopback only. Terminate TLS with a
host reverse proxy such as Caddy and proxy to `127.0.0.1:WEB_PORT`; do not expose
that port through the cloud firewall. Keep the backend, database, and ClamAV
ports private. Free hosting is appropriate for a portfolio deployment, but it
does not replace tested backups or availability monitoring.

See [docs/SECURITY.md](docs/SECURITY.md) for the authentication, CSRF, rate
limiting, upload-validation, and deployment decisions.
See [docs/BACKUPS.md](docs/BACKUPS.md) for encrypted production backups,
retention, OCI Object Storage setup, and isolated restoration testing.

## Verification

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm.cmd test -- --watch=false
npm.cmd run build
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) before adding a domain feature
and [docs/API.md](docs/API.md) when changing frontend/backend contracts.
