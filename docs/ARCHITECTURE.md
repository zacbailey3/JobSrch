# JobSrch Architecture

## Purpose

JobSrch helps candidates with zero to three years of experience find suitable
roles, save opportunities, track applications, and eventually compare a resume
against a job description.

The current code is an MVP modular monolith: one Spring Boot API, one Angular
application, and one relational database. Domain packages keep feature
boundaries visible without adding distributed-system complexity.

## Request flow

```text
Angular component
    |
    v
ApiService (typed HTTP contract)
    |
    v
Spring REST controller
    |
    v
Domain service (ownership and business rules)
    |
    v
Spring Data repository / ResumeStorageService
    |
    v
MySQL + private resume directory
```

Controllers translate HTTP requests. Services own business rules and
transactions. Repositories only query data. This separation is important:
ownership checks belong in services, not in the browser and not only in
controllers.

## Authentication and ownership

`AuthService` signs an HMAC JWT whose subject is the normalized email address.
Spring Security validates that signature before protected controllers run.

Authentication answers "who made this request." It does not answer "does this
record belong to that user." `CurrentUserService` resolves the database account,
and domain repositories query by both record id and user id. A valid user
therefore cannot retrieve or mutate another user's jobs, applications, profile,
or resumes by guessing a UUID.

Passwords are stored as BCrypt hashes. JWTs are stateless and expire after the
configured duration. Production must provide a private `JWT_SECRET`.

## Database migrations

Flyway owns the database schema:

- `V1__initial_schema.sql`: users, saved jobs, and applications
- `V2__profile_and_resume.sql`: career profiles and resume metadata

Never edit an applied migration. Add `V3__description.sql`, then `V4`, and so
on. Hibernate uses `ddl-auto: validate`, which checks entity mappings against
the migrated schema but never silently changes production tables.

UUID fields use `BINARY(16)` to keep MySQL indexes compact. Hibernate 7 entities
therefore declare the binary JDBC type explicitly.

## Resume storage

The database stores resume metadata, not file bytes. `ResumeStorageService`:

1. Accepts only `.pdf` and `.docx` files up to 10 MB.
2. Keeps the original name for display.
3. Generates a random storage filename.
4. Normalizes and verifies every destination remains under the configured root.
5. Removes the file when its user-owned metadata record is deleted.

The configured directory must be private; do not serve it as static web
content. A future download endpoint should stream a file only after the same
user-id ownership lookup.

## Resume analysis

Resume text extraction and matching run after upload, not inside
`ResumeStorageService`. The current flow is:

```text
ResumeAnalysisService
  -> ownership checks for resume and saved job
  -> ResumeTextExtractor (PDFBox or Apache POI)
  -> deterministic skill, experience, and structure scoring
  -> explainable matched skills, missing skills, and suggestions
```

The overall score is:

- 70% required-skill coverage
- 20% experience evidence
- 10% resume structure

Required skills come from a documented local vocabulary in
`ResumeAnalysisService`. Experience evidence looks for quantified year phrases.
Structure checks contact details and common resume sections.

The score is guidance, not a hiring prediction. Suggestions only recommend
adding a missing skill when it is accurate for the candidate. An external AI
provider may later improve phrasing, but local analysis remains the baseline so
the feature works without sending resume data to a third party.

## Frontend state

`App` currently coordinates four small workspace views: dashboard, discovery,
applications, and profile/resumes. `ApiService` is the typed backend boundary,
and `AuthService` owns persisted browser authentication.

This is deliberate for the early MVP. Extract a view into a routed feature
component when it gains complex filtering, dialogs, or independent tests. Keep
HTTP calls in `ApiService` during that refactor so backend contracts remain
centralized.

## External job sources

Greenhouse and Lever are implemented as on-demand adapters behind
`JobProviderClient`. Each adapter maps its provider response into the same
`DiscoveredJob` contract, so filtering and the Angular UI do not depend on a
provider's JSON shape.

```text
GET /api/discovery
  -> JobDiscoveryService
  -> direct board request or JobBoardCatalog starter sources
  -> provider selected from the JobProviderClient registry
  -> fixed Greenhouse or Lever API host
  -> normalized DiscoveredJob results
  -> optional company, location, role, and entry-level filters
  -> broad relevance ranking with title matches weighted highest
```

`JobBoardCatalog` supplies an explicit starter set of public boards when the
candidate searches by role rather than company. A direct board token remains
optional for targeted searching. Provider hosts are fixed in code, identifiers
allow only letters, numbers, hyphens, and underscores, and requests have
connection/read timeouts. These constraints prevent the endpoint from becoming
a general-purpose server-side URL fetcher.

Career-stage terms such as "junior" and "new grad" are removed from relevance
terms because `ExperienceClassifier` already handles that intent. Remaining
terms use broad OR matching, with title matches weighted above company,
location, and description matches. This avoids requiring a posting to use the
candidate's exact phrase.

Discovery results are transient. A posting enters JobSrch's database only when
the user saves it, at which point the existing saved-job ownership rules apply.
The deterministic `ExperienceClassifier` hides clearly senior titles and roles
whose stated experience exceeds three years. Unknown experience is shown as
"entry-level likely," not asserted as a fact.

The browser compares normalized source URLs, falling back to normalized company
and title, to hide discovery results already recorded with an application
status other than `SAVED`. This presentation filter reacts whenever application
data changes; it does not remove provider results or alter application history.

USAJOBS can be added later through the same provider interface. Do not scrape
LinkedIn or Indeed.
