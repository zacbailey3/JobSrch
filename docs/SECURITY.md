# JobSrch Security Model

## Authentication

The backend signs an eight-hour HMAC JWT, but it does not return that token to
Angular. `AuthController` places it in the `JOBSRCH_SESSION` cookie with:

- `HttpOnly`, so browser JavaScript cannot read it;
- `Secure` in production, so it is sent only over HTTPS;
- `SameSite=Strict`, so cross-site requests do not include it;
- a lifetime matching the JWT lifetime.

Angular keeps display metadata such as the user's name and email only in
memory. On refresh, protected route guards restore it through
`GET /api/auth/session`; that endpoint validates the HttpOnly cookie and never
returns the JWT. A missing or expired server cookie produces `401 Unauthorized`,
and the frontend clears its display state and returns to login.

State-changing authenticated requests also require Spring Security's CSRF
token. Spring writes `XSRF-TOKEN`; Angular echoes it as `X-XSRF-TOKEN`.
Spring Security's SPA CSRF mode resolves Angular's plain header value while
retaining BREACH protection for encoded tokens and ensures a fresh cookie is
written when authentication or logout clears the previous token.
Authentication and password-recovery endpoints are exempt because a visitor
does not yet have a CSRF token when establishing or recovering a session.

The application installs the JWT authentication filter explicitly instead of
using Spring's resource-server DSL. The DSL assumes bearer tokens do not use
cookies and exempts them from CSRF checks; that assumption is incorrect for
JobSrch. `SecurityHttpTests` verifies that an authenticated mutation without a
valid CSRF token receives `403 Forbidden` and that Angular's matching plain
cookie/header pair authorizes a mutation.

JWT signature, expiration, and issuer are validated. New JWT subjects are the
immutable account UUID and include `authTime`. For one eight-hour token lifetime,
the resolver accepts older email subjects only when the signed `userId` claim
still resolves to that same account. Every token also contains the user's
`securityVersion`. A password reset, password change, or completed email change
increments the persisted version, so all previously issued session cookies
immediately receive `401 Unauthorized`.

## Sensitive account changes

Settings displays email verification state but does not treat it as
authorization. Password, email, and deletion changes require `authTime` to be
within ten minutes. `POST /api/account/reauth/password` verifies BCrypt
credentials and replaces the session cookie with a freshly authenticated JWT.

Email changes never activate based only on a submitted address. The backend
stores a SHA-256 hash of a random token, sends the raw token only to the new
address, expires it after 15 minutes, binds it to the requesting account, and
rejects duplicate, expired, reused, or cross-account tokens. Completion marks
the new address verified, invalidates every session and password-reset token,
and sends a notice to the old address. The email link carries its token in a URL
fragment so it is not sent in HTTP requests or referrer headers; Angular removes
the fragment after reading it. Existing accounts remain honestly marked as not
independently verified until an address completes this flow.

Deletion additionally requires typing `DELETE` exactly. The service removes
the complete current account graph and generated private resume files. All
account mutations retain cookie CSRF protection and dedicated rate limits.

## Password reset email

Development and automated tests may expose the one-time reset token directly by
setting `PASSWORD_RESET_EXPOSE_TOKEN=true`. This must be `false` publicly.

Production sends short-lived password-reset and email-change messages through
Resend. Configure:

- `RESEND_API_KEY`
- `PASSWORD_RESET_FROM`
- `FRONTEND_BASE_URL`

Resend requires SPF and DKIM DNS records for a domain you control before it can
send to users other than the account owner's test address. Tokens are stored as
SHA-256 hashes, expire after 15 minutes, and are deleted after a successful
reset.

## Abuse controls

Registration, login, password-reset operations, account reauthentication,
password/email changes, deletion, resume uploads, resume analysis, and
discovery requests use one-minute limits. Authenticated expensive
operations are keyed by user; unauthenticated operations are keyed by client
address. Direct company-board requests have a stricter limit because they can
make live provider calls.

The map is bounded to 10,000 active buckets and expired windows are removed
periodically. Production enables Tomcat's native forwarding support and trusts
forwarded addresses only from private internal proxy ranges. The backend is not
published directly by Compose, so public clients cannot supply trusted proxy
headers themselves. Before scaling horizontally, replace this single-instance
limiter with a shared Redis-backed implementation.

Nginx adds a Content Security Policy, clickjacking protection, MIME-sniffing
protection, a restrictive permissions policy, and a referrer policy.

## Resume uploads

Resume filenames are display metadata only; generated UUID filenames are used
on disk. Paths must remain beneath the configured private storage root. Uploads
are limited to 10 MB and to PDF or DOCX, and the server checks the PDF signature
or required DOCX ZIP entries rather than trusting the browser MIME type or file
extension.

DOCX validation reads the complete archive with limits on entry count,
individual expanded entry size, total expanded size, and path-like entry names.
Apache POI receives additional compression-ratio, file-count, entry-size, and
text-size limits. PDF analysis limits page count, extracted text, scratch
storage, parser concurrency, queue length, and processing time.

Local development leaves malware scanning disabled so it does not require an
extra service. The `prod` profile refuses to start unless scanning is enabled.
Production Compose starts a private ClamAV sidecar, and uploads fail closed with
`503 Service Unavailable` if the scanner cannot provide a clean result.

ClamAV is defense in depth rather than proof that a document is harmless.
Parser dependencies and virus definitions still need regular updates.

## Production fail-safe profile

Production Compose activates the `prod` Spring profile. That profile and
`ProductionSecurityValidator` refuse startup when:

- the JWT or database password uses a development value;
- the session cookie is not HTTPS-only;
- reset tokens are exposed in API responses;
- email-change tokens are exposed in API responses;
- Resend settings are absent;
- the frontend or CORS origin is not an explicit public HTTPS URL; or
- malware scanning is disabled.

Production credentials have no Compose fallbacks. Use a production-only
environment file that is never copied from the local `.env`. The containers run
with restricted capabilities; the backend and frontend filesystems are
read-only except for declared resume and temporary volumes.

Production Compose binds the frontend only to host loopback. Docker-published
ports can bypass UFW rules, so the public cloud firewall must expose only the
host HTTPS proxy on ports 80 and 443; port 8080, the backend, MySQL, and ClamAV
remain private. A host Caddy service can proxy HTTPS traffic to
`127.0.0.1:8080` without adding another container to the four-service app stack.
Nginx preserves Caddy's sanitized `X-Forwarded-Proto` value so Spring sees the
original HTTPS request rather than the internal HTTP proxy hop. This is needed
for scheme-aware security behavior such as the CSRF cookie.

HSTS is emitted by Nginx, but it only protects users when the public deployment
actually terminates TLS and is accessed over HTTPS.

## Automated checks

`.github/workflows/security-ci.yml` runs:

- all Maven tests, including HTTP authorization and CSRF checks;
- Angular tests and the production build;
- `npm audit --omit=dev`;
- an immutable SHA-pinned Trivy filesystem, secret, and configuration scan; and
- CodeQL for Java and JavaScript/TypeScript.

Dependabot checks npm, Maven, Docker, and GitHub Actions dependencies weekly.
Security alerts still require review; do not automatically merge dependency
updates without passing tests.

## Remaining launch work

- Production backup retention is seven daily encrypted copies on the VM and 30
  daily encrypted copies in private OCI Object Storage. Deleted account or
  resume data can remain in those backups for at most 30 days. Follow
  `docs/BACKUPS.md` for setup and restoration testing.
- Publish a reviewed privacy policy that discloses the backup-retention period.
- Replace the in-memory limiter if multiple backend replicas are deployed.
- Store resumes in private object storage if the local volume becomes difficult
  to back up or the backend is replicated.
- Add external uptime/error monitoring and test database/resume restoration.
- Review privacy and terms documents before publishing them.
