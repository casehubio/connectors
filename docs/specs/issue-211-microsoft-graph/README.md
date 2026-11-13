# Issue #211 — Microsoft Graph Providers

## Goal

One SDK (`microsoft-graph`), one credential resolver, four Platform SPI providers:
- `email-microsoft` → EmailPlatform (Outlook)
- `calendar-microsoft` → CalendarPlatform (Outlook Calendar)
- `document-microsoft` → DocumentPlatform (OneDrive)
- `document-sharepoint` → DocumentPlatform (SharePoint)

Optional fifth: `chat-teams` → ChatPlatform (Teams channels/messages via Graph API).

## Prerequisites

- Microsoft 365 Developer Program account (free E5 sandbox)
  - Sign up: https://developer.microsoft.com/en-us/microsoft-365/dev-program
  - Provisions: sandbox tenant, 25 test users, sample data
  - Register app in Entra ID for OAuth2 client credentials
- Add `microsoft-graph` SDK dependency

## Module Structure

```
microsoft-graph-client/        # Shared module
  ├── MicrosoftGraphClient     # HTTP client, pagination, error mapping
  ├── MicrosoftCredentialResolver  # OAuth2 via Entra ID, per-user
  └── GraphErrorMapping        # HTTP status → ProviderApiException

email-microsoft/               # EmailPlatform provider
  └── MicrosoftEmailPlatform   # Delegates to GraphClient.mail()

calendar-microsoft/            # CalendarPlatform provider
  └── MicrosoftCalendarPlatform # Delegates to GraphClient.calendar()

document-microsoft/            # DocumentPlatform provider (OneDrive)
  └── MicrosoftDrivePlatform   # Delegates to GraphClient.drive()

document-sharepoint/           # DocumentPlatform provider (SharePoint)
  └── SharePointPlatform       # Delegates to GraphClient.sites()
```

## Implementation Order

1. `microsoft-graph-client` — shared foundation (auth, client, errors)
2. `email-microsoft` — most complex SPI, highest n8n deploys (96.3K)
3. `calendar-microsoft` — well-defined SPI, clean Graph API mapping
4. `document-microsoft` — OneDrive file operations
5. `document-sharepoint` — SharePoint sites (similar to OneDrive, different Graph endpoint)
6. `chat-teams` (stretch) — if time permits, ChatPlatform via Graph

## Key Graph API Endpoints

### Mail (email-microsoft)
- `GET /me/mailFolders` → list mailboxes
- `GET /me/messages?$filter=...` → list/search messages
- `GET /me/messages/{id}` → get message with body
- `GET /me/messages/{id}/attachments` → attachments
- `POST /me/sendMail` → send (if needed)

### Calendar (calendar-microsoft)
- `GET /me/calendars` → list calendars
- `GET /me/calendarView?startDateTime=...&endDateTime=...` → list events
- `GET /me/events/{id}` → get event
- `POST /me/events` → create event
- `PATCH /me/events/{id}` → update event
- `DELETE /me/events/{id}` → delete event
- `GET /me/calendarView/delta` → incremental sync

### OneDrive (document-microsoft)
- `GET /me/drive/root/children` → list files/folders
- `GET /me/drive/items/{id}` → get file metadata
- `GET /me/drive/items/{id}/content` → download
- `PUT /me/drive/items/{parent-id}:/{filename}:/content` → upload
- `DELETE /me/drive/items/{id}` → delete
- `POST /me/drive/root/search(q='...')` → search
- `POST /me/drive/items/{id}/createLink` → share link
- `GET /me/drive/root/delta` → incremental sync

### SharePoint (document-sharepoint)
- `GET /sites/{site-id}/drive/root/children` → list files
- Same CRUD pattern as OneDrive but scoped to site drives

### Teams (chat-teams — stretch)
- `POST /teams/{id}/channels/{id}/messages` → send message
- `GET /teams/{id}/channels` → list channels
- `GET /teams/{id}/members` → list members
- `GET /users/{id}/presence` → presence

## SPI Capability Mapping

### EmailPlatform
| Capability | Graph API support |
|---|---|
| listMailboxes | `GET /me/mailFolders` |
| listMessages | `GET /me/messages` with `$top`, `$skip`, `$filter` |
| getMessage | `GET /me/messages/{id}` |
| getAttachment | `GET /me/messages/{id}/attachments/{id}` |
| search | `GET /me/messages?$search="..."` |

### CalendarPlatform
| Capability | Graph API support |
|---|---|
| listCalendars | `GET /me/calendars` |
| listEvents | `GET /me/calendarView` |
| getEvent | `GET /me/events/{id}` |
| createEvent | `POST /me/events` |
| updateEvent | `PATCH /me/events/{id}` |
| deleteEvent | `DELETE /me/events/{id}` |
| sync | `GET /me/calendarView/delta` → SyncResult |

### DocumentPlatform
| Capability | Sub-interface | Graph API support |
|---|---|---|
| FileOperations.list | `GET /me/drive/root/children` |
| FileOperations.get | `GET /me/drive/items/{id}` |
| FileOperations.download | `GET /me/drive/items/{id}/content` |
| FileOperations.upload | `PUT .../content` |
| FileOperations.delete | `DELETE /me/drive/items/{id}` |
| FileOperations.sync | `GET /me/drive/root/delta` → SyncResult |
| FolderOperations.list | `GET /me/drive/root/children` (folders) |
| FolderOperations.create | `POST /me/drive/root/children` |
| FolderOperations.move | `PATCH /me/drive/items/{id}` (parentReference) |
| SearchOperations.search | `POST /me/drive/root/search(q='...')` |
| SharingOperations.getShareLink | `POST /me/drive/items/{id}/createLink` |

## Auth Model

Microsoft Graph uses OAuth2 via Entra ID (formerly Azure AD):
- Delegated permissions (on behalf of user) — for per-user operations
- Application permissions (daemon) — for service-to-service

`MicrosoftCredentialResolver` CDI SPI resolves per-user OAuth2 tokens, same pattern as `GoogleCredentialResolver`.

## Error Mapping

| Graph HTTP Status | Maps to |
|---|---|
| 401 | `ProviderAuthException(TOKEN_EXPIRED)` |
| 403 | `ProviderAuthException(INSUFFICIENT_SCOPE)` |
| 404 | Resource not found (SPI-specific) |
| 410 | `SyncTokenExpiredException` (delta queries) |
| 429 | `ProviderRateLimitedException(retryAfterSeconds)` |
| 5xx | `ProviderApiException(statusCode, body, "microsoft")` |

## Reference

- Gap analysis: `docs/specs/2026-11-13-connector-gap-analysis.md`
- Graph API docs: https://learn.microsoft.com/en-us/graph/api/overview
- Graph Java SDK: https://github.com/microsoftgraph/msgraph-sdk-java
- Existing Google pattern to follow: `email-google`, `calendar-google`, `document-google`
