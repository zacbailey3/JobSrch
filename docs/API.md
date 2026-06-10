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
| `provider` | No | `GREENHOUSE` or `LEVER`; omitted searches both |
| `companyIdentifier` | No | Board token/site name; omitted uses the starter catalog |
| `companyName` | No | Company filter, or display name for a direct board |
| `query` | No | Broad role/skill terms ranked by relevance |
| `location` | No | Case-insensitive location filter |
| `entryLevelOnly` | No | Defaults to `true`; excludes clearly senior or 4+ year roles |

Results are fetched on demand and are not saved until the user chooses
**Save role**. All fields are optional, and a blank search browses likely
entry-level openings from the starter catalog. The API returns at most 100
normalized postings per request.

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
