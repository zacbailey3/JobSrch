# JobSrch REST API

All request and response bodies use JSON unless marked as multipart. Protected
routes require:

```http
Authorization: Bearer <accessToken>
```

Validation errors use RFC 9457 problem details and may include an `errors`
object keyed by field name.

## Authentication

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | No | Create an account and return a JWT |
| `POST` | `/api/auth/login` | No | Verify credentials and return a JWT |

Registration fields: `email`, `password`, `firstName`, `lastName`.

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
that index and performs an initial live fetch only when no indexed coverage is
available. A direct Greenhouse or Lever board token always performs a targeted
live request. The API returns at most 100 normalized postings, with no more
than five results from one company.

Each result includes normalized `opportunityType`, `careerStage`,
`degreeRequirement`, `sponsorshipStatus`, and `verifiedAt` fields.
`matchReasons` and `cautions` explain posting evidence and, when a profile is
available, matching skills, desired roles, education, and experience. These
signals are guidance; the employer's original posting remains authoritative.

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
