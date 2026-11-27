# Google OAuth Family — Unified Consent for Calendar, Drive, Contacts, Gmail

**Issue:** casehubio/connectors#375
**Date:** 2026-11-27
**Branch:** issue-375-google-oauth-family

## Problem

Four Google connectors (`calendar-google`, `document-google`, `contacts-google`, `email-google`) each need Google OAuth2 credentials. Only `email-google` has a connector manifest. The consent flow (`OAuthConsentService`) stores tokens in `ProviderCredentialFacade`, but the Google platform classes read tokens via `ServiceConnectionProvider` (from platform-api) — there is no bridge between these two credential systems. A user cannot authenticate once and have all four connectors work.

## Design

### 1. Per-Module Connector Manifests

Create minimal connector manifests for the three missing Google modules. Each references `auth.family: google` — the family manifest provides OAuth config and shared fields via `enrichWithFamily()`.

**calendar-google** (`calendar-google/src/main/resources/META-INF/connector-manifest.yaml`):
```yaml
connector:
  id: calendar-google
  platform: calendar
  description: "Google Calendar — calendar and event management via Google API"
  auth:
    type: oauth2
    family: google
  provides:
    - capability: calendar
```

**document-google** (`document-google/src/main/resources/META-INF/connector-manifest.yaml`):
```yaml
connector:
  id: document-google
  platform: document
  description: "Google Drive — file management, search, and sharing via Google API"
  auth:
    type: oauth2
    family: google
  provides:
    - capability: document
      sub: [files, folders, search, sharing]
```

**contacts-google** (`contacts-google/src/main/resources/META-INF/connector-manifest.yaml`):
```yaml
connector:
  id: contacts-google
  platform: contacts
  description: "Google Contacts — contact and group management via Google People API"
  auth:
    type: oauth2
    family: google
  provides:
    - capability: contacts
      sub: [read, groups, write]
```

**email-google** — strip redundant per-connector fields (they are overwritten by `enrichWithFamily()` anyway):
```yaml
connector:
  id: email-google
  platform: email
  description: "Gmail — email query, read, search, and attachments via Google API"
  auth:
    type: oauth2
    family: google
  provides:
    - capability: email
      sub: [receive, search]
```

### 2. FamilyConnectionProvider

New class in `connectors-api`: `io.casehub.connectors.lifecycle.FamilyConnectionProvider implements ServiceConnectionProvider`.

Reads tokens from `ProviderCredentialFacade` for family-based connectors. Handles token refresh internally.

**Construction:**
- Injected with `ProviderCredentialFacade` and `Map<String, FamilyManifest>` (from `FamilyManifestLoader.discover()`)
- Only handles providers that have a corresponding family manifest — delegates unknown providers to `ServiceConnectionException`

**Token resolution:**
- `getAccessToken(actorId, provider, tenancyId)` → resolve `access-token` from `ProviderCredentialFacade` using the family's credential key (e.g. `"google"`)
- Check expiry via `ProviderCredentialFacade.resolve(familyId, "expires-at")` (stored as ISO-8601 string). If expired or absent, refresh using stored `refresh-token`, `client-id`, `client-secret`, and the family manifest's `tokenUrl`
- On successful refresh, write updated `access-token`, `expires-at`, and optionally rotated `refresh-token` back to `ProviderCredentialFacade`
- `OAuthConsentService.exchangeCode()` already stores `access-token` and `refresh-token`; it must also store `expires-at` (computed from `expires_in` in the token response)

**Token refresh:**
- Reuse the HTTP logic from `OAuthConsentService.postTokenRequest()` — extract to a shared utility or call directly
- Read `client-id` and `client-secret` from `ProviderCredentialFacade.resolve(familyId, "client-id")`
- Read `token-url` from `FamilyManifest.OAuthDeclaration.tokenUrl()`
- Standard OAuth2 refresh_token grant: `grant_type=refresh_token&refresh_token=...&client_id=...&client_secret=...`

**CDI resolution:**
- Annotate with `@Alternative @Priority(100)` so it overrides `NoOpServiceConnectionProvider` (the `@DefaultBean` in platform-core)
- In full-platform deployments where `ServiceConnectionProviderCore` is present (from `AuthnBeans`), the platform bean has natural priority. `FamilyConnectionProvider` acts as the default for standalone/dev deployments
- For deployments that need both: a composite `ServiceConnectionProvider` that checks `ServiceConnectionProviderCore` first, falls back to `FamilyConnectionProvider` for family-based providers. This is a follow-up concern — not needed for initial delivery

**Scope tracking:**
- `getConnection()` returns `ServiceConnection` with `grantedScopes` and `missingScopes`
- `grantedScopes` tracked by storing the `scope` value from the token exchange response in `ProviderCredentialFacade` as `"google.granted-scopes"`
- `missingScopes` computed by comparing granted scopes against `@RequiresScopes` annotations (via `ScopeRegistry` if available, or by reading per-connector manifest scopes)

### 3. Incremental Consent with Upfront Default

**Default (family-level consent):**
When `credentialInput` is called for any Google family connector:
- Request all scopes from `google.yaml` family manifest
- One consent screen → tokens stored → all 4 connectors become READY

**Incremental (per-connector consent):**
When a specific connector is enabled after initial setup with a scope subset:
- `OAuthConsentService.buildAuthorizationUrl()` accepts an optional `scopeOverride` parameter
- Appends `include_granted_scopes=true` for families with `consent: incremental`
- Only the new scopes appear on Google's consent screen; previously granted scopes are preserved

**Changes to `OAuthConsentService.buildAuthorizationUrl()`:**
- Add `include_granted_scopes=true` when the family declares `consent: incremental`
- Accept an optional `Set<String> scopeOverride` parameter for per-connector consent
- When `scopeOverride` is null, use all family scopes (upfront default)

### 4. Family-Aware DependencyResolver

Extend `DependencyResolver` to auto-infer OAuth requirements from `auth.family` during construction:

- During manifest scan, when a connector has `auth.type: oauth2` + `auth.family: X`, generate an implicit requirement: "needs OAuth consent for family X"
- `unmetRequirements()` checks `ProviderCredentialFacade.isConfigured(familyId, List.of("access-token"))` to determine if the family consent has been completed
- Resolution option: `credentialInput(connectorId)` triggers the family consent flow
- No explicit `requires: [{capability: oauth2}]` needed in manifests — the resolver infers it

**Changes to `DependencyResolver`:**
- Constructor accepts `ProviderCredentialFacade` (or a simplified check interface)
- `unmetRequirements()` checks family OAuth status alongside `provides`/`requires` matching
- New `ResolutionOption.FamilyOAuthOption(familyId, connectorIds)` variant for OAuth consent resolution

### 5. `email-google` Manifest Cleanup

`enrichWithFamily()` replaces per-connector `auth.fields` with family fields. The `email-google` manifest's redundant `client-id`, `client-secret`, and `refresh-token` fields are dead code after enrichment. Remove them — the family manifest is the single source of truth.

Existing deployments with `email-google.refresh-token` in their credential store are unaffected: `ProviderCredentialFacade` stores under the credential key (`"google"` for family-based connectors, via `ConnectorManifest.credentialKey()`), not the connector id. The OAuth consent flow stores `refresh-token` under `"google"` as well.

## Out of Scope

- **CAPTCHA pass-through** — Neocortex has code for this; migrate to connectors as a follow-up issue
- **Composite `ServiceConnectionProvider`** for full-platform deployments that need both social login and family consent tokens — follow-up when deployment scenario arises
- **Google app verification** for sensitive scopes (`gmail.modify`, `contacts`) — operational concern, not architectural
- **Read/write scope toggle UI** — the scope pairs are ready (each module has `.readonly` and read-write scopes), but the enable/disable UI is a frontend concern

## Module Changes

| Module | Change |
|--------|--------|
| `connectors-api` | `FamilyConnectionProvider` class; `DependencyResolver` extended with family awareness |
| `calendar-google` | New `connector-manifest.yaml` |
| `document-google` | New `connector-manifest.yaml` |
| `contacts-google` | New `connector-manifest.yaml` |
| `email-google` | Simplified `connector-manifest.yaml` (remove redundant fields) |
| `graphql` | `OAuthConsentService.buildAuthorizationUrl()` — add `include_granted_scopes`, optional scope override |

## References

- `connectors-api/src/main/resources/META-INF/families/google.yaml` — family manifest with 8 scopes
- `ConnectorManifestLoader.resolveFamilies()` — family enrichment logic
- `OAuthConsentService.java` — PKCE consent flow + token exchange
- `OAuthCallbackEndpoint.java` — `/oauth/callback/{family}` redirect handler
- `ProviderCredentialFacade.java` — credential store interface
- `PersistentCredentialFacade.java` — file-backed implementation
- `ServiceConnectionProvider.java` (platform-api) — runtime token resolution interface
- `ServiceConnectionProviderCore.java` (platform/authn-social-core) — platform implementation
- `OAuthTokenManagerCore.java` (platform/authn-social-core) — platform token refresh
- `DependencyResolver.java` — backward-chaining requirement resolution
- `ConnectorRegistry.updateStatusByCredentialKey()` — family-wide status propagation
- Decision review: `/Users/mdproctor/reviews/casehub-connectors/issue-375-decision-20261127-150122/`
