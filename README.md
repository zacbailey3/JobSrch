# JobSrch

JobSrch is a full-stack job search and application tracker focused on new
graduates and candidates seeking roles that require zero to three years of
experience.

## Stack

- Java 17 and Spring Boot 4
- Angular 21
- MySQL 8.4 with Flyway migrations
- JWT email/password authentication

## Current features

- Email/password registration and login
- User-owned saved jobs with entry-level experience ranges
- Application pipeline with status updates
- Readable candidate profile with a separate editing mode
- PDF/DOCX resume upload with safe server-side filenames
- Explainable local resume-to-job matching with improvement suggestions
- Role-first Greenhouse and Lever discovery with broad relevance ranking
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
    app.ts         MVP view state and user interactions
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
| `RESUME_DIRECTORY` | Persistent private directory for uploaded resumes |
| `PORT` | API port, default `8080` |

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
