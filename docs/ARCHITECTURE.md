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
The controller delivers it only through an HttpOnly session cookie, so Angular
cannot read or persist the credential. Spring Security resolves and validates
that cookie before protected controllers run. CSRF tokens protect
state-changing cookie-authenticated requests.

Authentication answers "who made this request." It does not answer "does this
record belong to that user." `CurrentUserService` resolves the database account,
and domain repositories query by both record id and user id. A valid user
therefore cannot retrieve or mutate another user's jobs, applications, profile,
or resumes by guessing a UUID.

Passwords are stored as BCrypt hashes. JWTs are stateless and expire after the
configured duration. Production must provide a private `JWT_SECRET`, enable the
cookie's `Secure` attribute, and use HTTPS.

## Database migrations

Flyway owns the database schema:

- `V1__initial_schema.sql`: users, saved jobs, and applications
- `V2__profile_and_resume.sql`: career profiles and resume metadata
- `V3__job_index_and_saved_searches.sql`: indexed provider jobs, saved filters,
  and alert matches
- `V4__expand_indexed_job_location.sql`: room for multi-office provider labels
- `V5__early_career_job_signals.sql`: opportunity type, career stage, degree
  language, sponsorship language, and saved-search filters

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

Greenhouse and Lever company boards use `JobProviderClient`. USAJOBS and
Adzuna use `AggregateJobProviderClient` and are enabled only when credentials
are configured. Every adapter maps into the same `DiscoveredJob` contract, so
filtering and the Angular UI do not depend on a provider's JSON shape.

```text
GET /api/discovery
  -> JobDiscoveryService
  -> active IndexedJob rows
  -> initial live provider request when the index has no matching coverage
  -> normalized DiscoveredJob results from all enabled sources
  -> normalized country, workplace, and source freshness metadata
  -> optional company, location, country, workplace, freshness, and entry-level filters
  -> broad relevance ranking with title matches weighted highest
  -> maximum five results per company
```

`JobImportService` refreshes the curated US employer catalog and enabled
aggregate sources on a fixed delay. `JobIndexService` hashes normalized source
URLs with SHA-256 for compact cross-provider deduplication. Jobs become
inactive when their provider expiration passes or no provider has returned
them within the configured stale window.

`JobBoardCatalog` supplies an explicit set of public company boards. A direct
board token remains optional for targeted Greenhouse or Lever searching.
Provider hosts are fixed in code, identifiers allow only letters, numbers,
hyphens, and underscores, and requests have connection/read timeouts. These
constraints prevent the endpoint from becoming a general-purpose server-side
URL fetcher.

Career-stage terms such as "junior" and "new grad" are removed from relevance
terms because `ExperienceClassifier` already handles that intent. Remaining
terms use broad OR matching, with title matches weighted above company,
location, and description matches. This avoids requiring a posting to use the
candidate's exact phrase.

Discovery defaults to country code `US` and the most recent 30 days. Lever
provides an ISO country code and workplace type directly. Greenhouse country
and workplace values are inferred conservatively from the posting and office
locations; unknown values remain unknown instead of being assumed to be US.
The country selector can be changed to `ANY` when the candidate intentionally
wants a global search.

Provider date fields are normalized as `publishedAt`, but the interface labels
them as freshness because Greenhouse exposes `updated_at`, which may represent
an edit rather than the original publication date. Selecting a freshness
window excludes results whose provider does not supply a date.

Indexed jobs are shared search data and do not belong to a user. A posting
enters a user's shortlist only when the user chooses **Save role**, at which
point the existing saved-job ownership rules apply. The deterministic
`ExperienceClassifier` hides clearly senior titles and roles whose stated
experience exceeds three years. Unknown experience is shown as "entry-level
likely," not asserted as a fact.

`JobInsightClassifier` adds conservative metadata for opportunity type, career
stage, degree language, and visa sponsorship. Unknown values stay unknown
rather than being inferred optimistically. `CandidateMatchExplainer` then adds
profile-specific evidence such as matching skills and experience range. The
API returns reasons and cautions instead of a hidden suitability score, so a
candidate can inspect why a role was included and what still needs verification
on the employer's page.

Saved searches are user-owned filter snapshots. Each search records the import
time at creation. After a refresh, `SavedSearchAlertService` compares jobs first
seen since the previous check and stores unique matches in
`search_alert_matches`. This keeps alerts incremental and avoids presenting the
entire existing index as new.

The browser compares normalized source URLs, falling back to normalized company
and title, to hide discovery results already recorded with an application
status other than `SAVED`. This presentation filter reacts whenever application
data changes; it does not remove provider results or alter application history.

Do not scrape LinkedIn or Indeed. Additional sources should use documented
APIs or employer-owned public job-board endpoints and map into
`DiscoveredJob`.
