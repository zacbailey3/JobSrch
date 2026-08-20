# JobSrch REST API

All request and response bodies use JSON unless marked as multipart. Login and
registration set the protected `JOBSRCH_SESSION` cookie; browser JavaScript
never receives the JWT. State-changing protected requests also send Angular's
`X-XSRF-TOKEN` header from the `XSRF-TOKEN` cookie.

Validation errors use RFC 9457 problem details and may include an `errors`
object keyed by field name.

## Authentication

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | No | Create an account and set the session cookie |
| `POST` | `/api/auth/login` | No | Verify credentials and set the session cookie |
| `GET` | `/api/auth/session` | Yes | Restore browser-safe account metadata from the session cookie |
| `POST` | `/api/auth/logout` | Yes | Expire the session cookie |
| `POST` | `/api/auth/password-reset/request` | No | Request a one-time reset link |
| `POST` | `/api/auth/password-reset/confirm` | No | Consume a reset token and set a new password |
| `GET` | `/api/account` | Yes | Return email verification and recent-authentication status |
| `POST` | `/api/account/reauth/password` | Yes | Confirm the password and issue a fresh session with a new `authTime` |
| `PUT` | `/api/account/password` | Recent auth | Change the password and invalidate every session |
| `POST` | `/api/account/email-change/request` | Recent auth | Send a hashed, 15-minute confirmation token to a new address |
| `POST` | `/api/account/email-change/confirm` | Recent auth | Confirm the token, change email, and invalidate every session |
| `DELETE` | `/api/account` | Recent auth | Permanently delete the account after an exact `DELETE` confirmation |

Registration fields: `email`, `password`, `firstName`, `lastName`.

The session response contains `expiresIn`, `userId`, `email`, `firstName`,
`lastName`, and `authenticatedAt`. It never contains the JWT or cookie value.

Recent authentication lasts ten minutes from `authenticatedAt`. Password
reauthentication sets a new HttpOnly cookie. Password change, completed email
change, and account deletion expire the browser cookie; password and email
changes also increment `securityVersion`, invalidating every other session.

Email-change request fields: `email`. Confirmation fields: `token`. Local
development may return `developmentToken` and `expiresAt`; production always
returns those fields as `null` and sends the token through configured email.
Deletion fields: `confirmation`, which must equal `DELETE` exactly.

## Dashboard

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/dashboard` | Return saved-job and application status counts |

## Saved jobs

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/jobs?query=` | List the user's jobs, optionally filtering title/company |
| `POST` | `/api/jobs` | Save a job |
| `PUT` | `/api/jobs/{id}` | Replace a user-owned saved job |
| `DELETE` | `/api/jobs/{id}` | Delete a user-owned saved job |

Job fields: `company`, `title`, `location`, `description`, `sourceUrl`,
`experienceMin`, `experienceMax`, and `publishedAt`.

## Job discovery

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/discovery` | Search starter public boards or one supplied board token |

Query parameters:

| Name | Required | Description |
| --- | --- | --- |
| `provider` | No | `GREENHOUSE`, `LEVER`, `USAJOBS`, or `ADZUNA`; omitted searches all indexed sources |
| `companyIdentifier` | No | Board token/site name; omitted uses the starter catalog |
| `companyName` | No | Company filter, or display name for a direct board |
| `query` | No | Broad role/skill terms ranked by relevance |
| `location` | No | Case-insensitive location filter |
| `countryCode` | No | Defaults to `US`; use `ANY` to disable country filtering |
| `workplaceType` | No | `REMOTE`, `HYBRID`, or `ON_SITE` |
| `postedWithinDays` | No | Freshness window from 1 to 60 days |
| `sort` | No | `RELEVANCE` (default) or `NEWEST` |
| `entryLevelOnly` | No | Defaults to `true`; excludes clearly senior or 4+ year roles |
| `opportunityType` | No | `FULL_TIME`, `PART_TIME`, `INTERNSHIP`, `APPRENTICESHIP`, or `CONTRACT` |
| `careerStage` | No | `NEW_GRAD`, `ENTRY_LEVEL`, `EARLY_CAREER`, `INTERNSHIP`, or `APPRENTICESHIP` |
| `degreeRequirement` | No | Filter by detected degree language |
| `sponsorshipStatus` | No | `AVAILABLE`, `NOT_AVAILABLE`, or `NOT_STATED` |
| `maximumExperience` | No | Maximum stated upper experience bound, from 0 to 10 |

Scheduled imports populate a shared provider-neutral index. A broad search uses
that index and does not wait on external providers when coverage is empty. A
direct Greenhouse or Lever board token performs a targeted live request. The
API returns at most 100 normalized postings, with no more
than five results from one company.

Each result includes normalized `opportunityType`, `careerStage`,
`degreeRequirement`, `sponsorshipStatus`, and `verifiedAt` fields.
`matchReasons` and `cautions` explain posting evidence and, when a profile is
available, matching skills, desired roles, education, and experience. These
signals are guidance; the employer's original posting remains authoritative.

## Import status

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/import-status` | Return active inventory freshness and the 20 most recent import batches |

This authenticated operational endpoint reports batch status, received and
expired counts, and sanitized per-provider/company-board outcomes. It never
returns provider credentials or raw exception messages. `PARTIAL_FAILURE`
means useful inventory was indexed even though one or more independent sources
failed.

## Saved searches and alerts

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/saved-searches` | List the authenticated user's filters |
| `POST` | `/api/saved-searches` | Save filters and enable future-match alerts |
| `DELETE` | `/api/saved-searches/{id}` | Delete a user-owned saved search |
| `GET` | `/api/saved-searches/alerts` | List imported matches for the user |
| `POST` | `/api/saved-searches/alerts/seen` | Mark all of the user's matches read |

Saved-search fields: `name`, `query`, `location`, `countryCode`,
`workplaceType`, `postedWithinDays`, `entryLevelOnly`, `opportunityType`,
`careerStage`, `degreeRequirement`, `sponsorshipStatus`, `maximumExperience`,
and `alertsEnabled`.
Existing indexed jobs form the baseline; alerts are created only for matching
jobs first seen during a later import.

## Applications

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/applications` | List the user's application pipeline |
| `POST` | `/api/applications` | Add an application |
| `PUT` | `/api/applications/{id}` | Replace application details/status |
| `DELETE` | `/api/applications/{id}` | Delete an application |

Application fields: `jobPostingId` (optional), `company`, `title`, `sourceUrl`,
`status`, `appliedAt`, and `notes`.

Statuses: `SAVED`, `APPLIED`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN`.

## Career profile

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/profile` | Return profile fields or an empty profile |
| `PUT` | `/api/profile` | Create or replace the user's profile |

Profile fields: `phone`, `location`, `headline`, `education`,
`graduationYear`, `yearsExperience`, `desiredRoles`, `skills`, `linkedinUrl`,
and `portfolioUrl`.

## Resumes

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/profile/resumes` | List uploaded resume metadata |
| `POST` | `/api/profile/resumes` | Upload one PDF or DOCX as multipart field `file` |
| `DELETE` | `/api/profile/resumes/{id}` | Delete user-owned metadata and file |

The maximum accepted file size is 10 MB. Files are private and are not exposed
by a public static URL.

## Resume analysis

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/api/resume-analysis` | Compare one user-owned resume with one user-owned saved job |

Request:

```json
{
  "resumeId": "uuid",
  "jobPostingId": "uuid"
}
```

The response includes overall, keyword, experience, and structure scores plus
`matchedSkills`, `missingSkills`, and plain-language `suggestions`. Analysis is
local and deterministic; resume text is not sent to an external service.
