# JobSrch Security Model

## Authentication

The backend signs an eight-hour HMAC JWT, but it does not return that token to
Angular. `AuthController` places it in the `JOBSRCH_SESSION` cookie with:

- `HttpOnly`, so browser JavaScript cannot read it;
- `Secure` in production, so it is sent only over HTTPS;
- `SameSite=Strict`, so cross-site requests do not include it;
- a lifetime matching the JWT lifetime.

Angular stores only display metadata such as the user's name and email. That
metadata is not authorization. A missing or expired server cookie produces
`401 Unauthorized`, and the frontend clears its local display state.

State-changing authenticated requests also require Spring Security's CSRF
token. Spring writes `XSRF-TOKEN`; Angular echoes it as `X-XSRF-TOKEN`.
Authentication and password-recovery endpoints are exempt because a visitor
does not yet have a CSRF token when establishing or recovering a session.

## Password reset email

Development and automated tests may expose the one-time reset token directly by
setting `PASSWORD_RESET_EXPOSE_TOKEN=true`. This must be `false` publicly.

Production sends a short-lived link through Resend. Configure:

- `RESEND_API_KEY`
- `PASSWORD_RESET_FROM`
- `FRONTEND_BASE_URL`

Resend requires SPF and DKIM DNS records for a domain you control before it can
send to users other than the account owner's test address. Tokens are stored as
SHA-256 hashes, expire after 15 minutes, and are deleted after a successful
reset.

## Abuse controls

Registration, login, and password-reset operations use per-IP, one-minute
limits. The current limiter is deliberately small and in memory because the
deployment has one backend instance. Before scaling horizontally, replace it
with a shared Redis-backed limiter.

Nginx adds a Content Security Policy, clickjacking protection, MIME-sniffing
protection, a restrictive permissions policy, and a referrer policy.

## Resume uploads

Resume filenames are display metadata only; generated UUID filenames are used
on disk. Paths must remain beneath the configured private storage root. Uploads
are limited to 10 MB and to PDF or DOCX, and the server checks the PDF signature
or required DOCX ZIP entries rather than trusting the browser MIME type or file
extension.

This validation is not an antivirus scanner. Before serving a large public
audience, add malware scanning and move files to private object storage.

## Remaining launch work

- Publish a reviewed privacy policy and choose explicit backup-retention periods.
- Add malware scanning for uploaded documents.
- Add dependency and container scanning in CI.
- Replace the in-memory limiter if multiple backend replicas are deployed.
- Review privacy and terms documents before publishing them.
