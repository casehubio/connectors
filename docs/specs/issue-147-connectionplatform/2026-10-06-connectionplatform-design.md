# Service Connections — Connectors Integration

**Issues:** casehubio/connectors#157, #158
**Date:** 2026-10-07 (revised)
**Status:** Final
**Supersedes:** Original ConnectionPlatform design (2026-10-06)

## Overview

Platform shipped a complete service connection infrastructure (platform#548, #530). Connectors' role is to consume it: annotate consumers with `@RequiresScopes`, replace ad-hoc credential resolvers with `ServiceConnectionProvider`, and thread tenancy through the API.

No new SPI, no new modules, no OAuth2 engine. The work is wiring and cleanup.

## Platform API Surface (what we consume)

All types in `io.casehub.platform.api.authn`:

### ServiceConnectionProvider

```java
public interface ServiceConnectionProvider {
    ServiceConnection getConnection(String actorId, String provider, String tenancyId);
    List<ServiceConnection> listConnections(String actorId, String tenancyId);
    ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId);
    default void disconnect(String actorId, String provider, String tenancyId) { ... }
    Set<String> missingScopes(String actorId, String provider, String tenancyId);
}
```

### ServiceConnection / ServiceAccessToken

```java
record ServiceConnection(String actorId, String provider, String tenancyId,
    ServiceConnectionStatus status, Set<String> grantedScopes,
    Set<String> missingScopes, Instant connectedAt)

enum ServiceConnectionStatus { CONNECTED, PARTIAL, DISCONNECTED }

record ServiceAccessToken(String accessToken, Instant expiresAt, Set<String> grantedScopes)
```

### Scope Declaration

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresScopes {
    String provider();
    String[] scopes();
}
```

`ScopeRegistryCore` scans CDI beans for `@RequiresScopes` at startup. `ServiceConnectionProviderCore` uses the registry to compute `missingScopes` and derive `ServiceConnectionStatus`.

### Static Credentials

```java
record StaticCredentialRecord(String actorId, String tenancyId, String provider,
    String credential, CredentialType type, Instant createdAt, Instant updatedAt)

enum CredentialType { API_KEY, BOT_TOKEN, PERSONAL_ACCESS_TOKEN }

interface StaticCredentialStore {
    void store(StaticCredentialRecord record);
    Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId);
    List<StaticCredentialRecord> findAll(String actorId, String tenancyId);
    void delete(String actorId, String provider, String tenancyId);
}
```

### Exceptions

`ServiceConnectionException` — thrown when no connection or insufficient scopes. Carries `provider`, `actorId`, `requiredScopes`, `grantedScopes`, `missingScopes`. Same recovery-metadata pattern as `UnsupportedCapabilityException`.

### Incremental Consent

`IncrementalConsentHandler.requestAdditionalScopes(actorId, provider, tenancyId, missingScopes)` → `ConsentRequest(authorizationUrl, provider, requestedScopes, state)`. MCP tool wrappers or UI catch `ServiceConnectionException` and redirect via `ConsentRequest.authorizationUrl()`.

### Social Login Bridge

`ScopeMergingLoginCustomizer` merges `ScopeRegistry.requiredScopes(provider)` into social login OAuth flow via `context.hints().put("additionalScopes", ...)`. One consent screen covers identity + service scopes.

## Connectors Work

### #157 — Wire ServiceConnectionProvider into connectors

**What changes per module:**

| Module | Current | After |
|--------|---------|-------|
| `calendar-google` | `@Inject GoogleCredentialResolver` → `GoogleOAuthConfig(refreshToken, clientId, clientSecret)` → builds `Calendar` service | `@Inject ServiceConnectionProvider` → `getAccessToken(actorId, "google", tenancyId)` → `ServiceAccessToken.accessToken()` → builds `Calendar` service |
| `contacts-google` | Same duplicated `GoogleCredentialResolver` | Same as calendar-google |
| `email-google` | Constructor takes raw `(clientId, clientSecret, refreshToken)` | `@Inject ServiceConnectionProvider` → `getAccessToken()` |
| `document-google` | Constructor takes raw `(clientId, clientSecret, refreshToken)` | `@Inject ServiceConnectionProvider` → `getAccessToken()` |
| `project-github` | `@Inject GitHubCredentialResolver` → `resolveToken(userId)` → raw PAT string | `@Inject ServiceConnectionProvider` → `getAccessToken(actorId, "github", tenancyId)` → `ServiceAccessToken.accessToken()` |
| `location-google` | `@Inject GoogleMapsKeyResolver` → `GoogleMapsConfig(apiKey)` | `@Inject StaticCredentialStore` → `find(actorId, "google-maps", tenancyId)` → `StaticCredentialRecord.credential()` |
| `bank-truelayer` | `TrueLayerConsentService` with its own `ConsentTokenStore` | Gradual: delegate token storage to `OAuthTokenStore`, keep PSD2 consent lifecycle layer |

**Files to delete:**

| File | Module |
|------|--------|
| `GoogleCredentialResolver.java` | `calendar-google` |
| `GoogleOAuthConfig.java` | `calendar-google` |
| `GoogleCredentialResolver.java` | `contacts-google` |
| `GoogleOAuthConfig.java` | `contacts-google` |
| `ConfigGoogleCredentialResolver.java` | `contacts-google` |
| `GitHubCredentialResolver.java` | `project-github` |
| `GoogleMapsKeyResolver.java` | `location-google` |
| `GoogleMapsConfig.java` | `location-google` |

**Tenancy threading:**

Platform uses 3-part keys `(actorId, provider, tenancyId)`. Current connector consumers only pass `userId`. Each consumer must accept and thread `tenancyId`. For Google platforms this means the `buildService(userId)` pattern becomes `buildService(actorId, tenancyId)`. Callers provide tenancy from the request context.

**Dependency addition:**

Affected modules add `platform-api` as a dependency (for `ServiceConnectionProvider`, `StaticCredentialStore`, `@RequiresScopes`). This is the same dependency pattern as `connectors-api` — pure Java interfaces, no runtime.

### #158 — Google scope registration

Add `@RequiresScopes` to each Google consumer:

```java
@RequiresScopes(provider = "google",
    scopes = {"https://www.googleapis.com/auth/calendar.readonly",
              "https://www.googleapis.com/auth/calendar.events"})
public class GoogleCalendarPlatform implements CalendarPlatform { ... }

@RequiresScopes(provider = "google",
    scopes = {"https://www.googleapis.com/auth/contacts.readonly",
              "https://www.googleapis.com/auth/contacts"})
public class GoogleContactsPlatform implements ContactsPlatform { ... }

@RequiresScopes(provider = "google",
    scopes = {"https://www.googleapis.com/auth/gmail.readonly",
              "https://www.googleapis.com/auth/gmail.modify"})
public class GoogleEmailPlatform implements EmailPlatform { ... }

@RequiresScopes(provider = "google",
    scopes = {"https://www.googleapis.com/auth/drive.readonly",
              "https://www.googleapis.com/auth/drive.file"})
public class GoogleDocumentPlatform implements DocumentPlatform { ... }
```

Also add to GitHub:

```java
@RequiresScopes(provider = "github", scopes = {"repo", "project"})
public class GitHubProjectPlatform implements ProjectPlatform { ... }
```

At startup, `ScopeRegistryCore` discovers these annotations and registers the scope requirements. `ScopeMergingLoginCustomizer` then merges them into social login — a user logging in with Google gets Calendar + Drive + Gmail + Contacts scopes in one consent screen.

### TrueLayer migration (gradual, within #157)

`TrueLayerConsentService` currently has its own `ConsentTokenStore` with full OAuth2 flow. Migration path:

1. Token storage: delegate to `OAuthTokenStore` (platform) — `StoredConsent` maps to `OAuthTokenRecord`
2. Token refresh: delegate to `OAuthTokenManagerCore.getValidToken()` — replaces the manual `refreshToken()` method
3. PSD2 consent lifecycle: retained — 90-day expiry, regulatory revocation stay in TrueLayer-specific code
4. `ConsentTokenStore` interface: deprecated, superseded by `OAuthTokenStore`

`TrueLayerConsentService` becomes a thin PSD2 adapter over platform's OAuth infrastructure.

## Data Flow (revised)

```
User → Social Login (platform #528) → Google OAuth tokens stored
  → ScopeMergingLoginCustomizer adds Calendar/Drive/Gmail/Contacts scopes
  → One consent screen for identity + service scopes
  → OAuthTokenStore holds tokens with full scope set

GoogleCalendarPlatform.buildService(actorId, tenancyId):
  → serviceConnectionProvider.getAccessToken(actorId, "google", tenancyId)
  → ServiceAccessToken(accessToken, expiresAt, grantedScopes)
  → Build Calendar service with accessToken

If scopes insufficient (user declined Calendar during login):
  → getAccessToken() throws ServiceConnectionException(missingScopes: calendar.*)
  → MCP tool wrapper catches, calls IncrementalConsentHandler
  → ConsentRequest(authorizationUrl) → user grants → scopes updated
  → Retry succeeds
```

## Testing

- Existing platform `NoOpServiceConnectionProvider` serves as `@DefaultBean` for dev/test
- Unit tests for each migrated consumer: mock `ServiceConnectionProvider`, verify correct `provider` and scope handling
- Integration tests: verify `@RequiresScopes` annotations are discovered by `ScopeRegistryCore`
- TrueLayer: verify PSD2 consent layer still works after delegation to platform stores

## What Was Dropped (platform owns these)

| Originally planned | Platform delivered |
|---|---|
| `ConnectionPlatform` SPI | `ServiceConnectionProvider` (platform#530) |
| `Connection` sealed hierarchy | `ServiceConnection` + `OAuthTokenRecord` + `StaticCredentialRecord` |
| `ConnectionStore` | `OAuthTokenStore` + `StaticCredentialStore` |
| OAuth2 flow engine | `AbstractOAuthAuthenticationProvider` + `OAuthTokenManagerCore` |
| Google/GitHub adapters | `GoogleAuthenticationProvider` + `GitHubAuthenticationProvider` |
| `connection-ref` | `NoOpServiceConnectionProvider` `@DefaultBean` |
| `@RequiresScopes` + `ScopeRegistry` | Platform#548 |
| `InsufficientScopesException` | `ServiceConnectionException` |
| Incremental consent | `IncrementalConsentHandler` + `ScopeMergingLoginCustomizer` |

## Original Epic Disposition

| Issue | Status |
|---|---|
| #147 Epic | Close — superseded by platform#548 + #530 |
| #148 ConnectionPlatform SPI | Close — platform#530 shipped `ServiceConnectionProvider` |
| #149 OAuth2 flow engine | Close — platform#526 shipped it |
| #150 Google adapter | Close — platform#526 shipped `GoogleAuthenticationProvider` |
| #151 GitHub adapter | Close — platform#526 shipped `GitHubAuthenticationProvider` |
| #152 Slack adapter | Defer — no OAuth2 social login for Slack yet |
| #153 Discord adapter | Defer — no OAuth2 social login for Discord yet |
| #154 Refactor CredentialResolvers | → connectors#157 |
| #155 connection-ref | Close — platform has `NoOpServiceConnectionProvider` |
| #156 Step-up auth | Remains open, deferred |
| #157 Wire ServiceConnectionProvider | **Active — this spec** |
| #158 Google scope registration | **Active — this spec** |
| #159 Connection status UI | Moved to blocks-ui#232 |

## References

- `ServiceConnectionProvider` — `platform-api/.../api/authn/ServiceConnectionProvider.java`
- `ServiceConnection` — `platform-api/.../api/authn/ServiceConnection.java`
- `ServiceAccessToken` — `platform-api/.../api/authn/ServiceAccessToken.java`
- `ServiceConnectionException` — `platform-api/.../api/authn/ServiceConnectionException.java`
- `@RequiresScopes` — `platform-api/.../api/authn/RequiresScopes.java`
- `ScopeRegistry` — `platform-api/.../api/authn/ScopeRegistry.java`
- `StaticCredentialStore` — `platform-api/.../api/authn/StaticCredentialStore.java`
- `IncrementalConsentHandler` — `platform-api/.../api/authn/IncrementalConsentHandler.java`
- `ScopeMergingLoginCustomizer` — `authn-social-core/.../social/ScopeMergingLoginCustomizer.java`
- Platform #525 — Identity Authentication epic
- Platform #548 — Service connection infrastructure (static credentials, scope registry)
- Platform #530 — ServiceConnectionProvider SPI and social login bridge
- Connectors protocol: `credential-config-ownership.md`
- Connectors protocol: `spi-id-method-naming.md`
- `contacts-google/GoogleCredentialResolver.java:3` — to delete
- `calendar-google/GoogleCredentialResolver.java:3` — to delete
- `project-github/GitHubCredentialResolver.java:3` — to delete
- `location-google/GoogleMapsKeyResolver.java` — to delete
