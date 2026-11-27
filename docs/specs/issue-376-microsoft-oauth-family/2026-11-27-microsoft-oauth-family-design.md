# Microsoft OAuth Family Manifest — Multi-Flow Design

**Issue:** casehubio/connectors#376
**Branch:** issue-376-microsoft-oauth-family
**Date:** 2026-11-27

## Problem

Microsoft Graph connectors (calendar-microsoft, email-microsoft, document-microsoft, chat-teams) share a single Azure AD app registration but use three distinct OAuth flows:

| Provider string | Grant type | Used by |
|----------------|------------|---------|
| `"microsoft"` | `authorization_code` (delegated) | calendar, email, document, Teams read ops |
| `"microsoft-app"` | `client_credentials` (application) | Teams write ops (send, reply, reactions, history) |
| `"microsoft-bot"` | `client_credentials` (Bot Framework) | Teams BotFrameworkClient |

The existing `FamilyManifest` model from #375 assumes one family = one OAuth flow. This works for Google (single `authorization_code` flow) but not for Microsoft's dual/triple token model. `FamilyConnectionProvider.getAccessToken()` does `families.get(provider)` — exact match only — so `"microsoft-app"` and `"microsoft-bot"` throw `ServiceConnectionException`.

Three Microsoft modules also lack connector manifests (calendar-microsoft, document-microsoft, chat-teams), and email-microsoft's manifest has redundant inline fields.

## Design

### 1. Named flows in FamilyManifest

Add a `flows` map to `FamilyDeclaration`. Each flow declares its grant type, scopes, and optional URL overrides. The existing top-level `oauth` block becomes the default for flows that don't override URLs.

#### FamilyManifest.java changes

```java
public record FamilyDeclaration(
        String id,
        String description,
        ConsentStrategy consent,
        OAuthDeclaration oauth,
        Map<String, FlowDeclaration> flows,     // NEW
        List<ConnectorManifest.FieldDeclaration> fields) {}

// NEW record
public record FlowDeclaration(
        GrantType grant,
        List<String> scopes,
        @JsonProperty("token-url") String tokenUrl) {}  // null = inherit from family oauth

public enum GrantType { AUTHORIZATION_CODE, CLIENT_CREDENTIALS }
```

#### microsoft.yaml

```yaml
family:
  id: microsoft
  description: "Microsoft 365 — shared Azure AD app registration"
  consent: aggregated
  oauth:
    auth-url: "https://login.microsoftonline.com/{tenant-id}/oauth2/v2/authorize"
    token-url: "https://login.microsoftonline.com/{tenant-id}/oauth2/v2/token"
    redirect-uri: "{callback-base-url}/oauth/callback/microsoft"
  flows:
    delegated:
      grant: authorization_code
      scopes:
        - Calendars.ReadWrite
        - Mail.Read
        - Mail.Send
        - Files.ReadWrite.All
        - Sites.ReadWrite.All
        - User.Read
    application:
      grant: client_credentials
      scopes:
        - "https://graph.microsoft.com/.default"
    bot:
      grant: client_credentials
      scopes:
        - "https://api.botframework.com/.default"
      token-url: "https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token"
  fields:
    - name: tenant-id
      sensitive: false
    - name: client-id
      sensitive: false
    - name: client-secret
```

#### Backward compatibility

When `flows` is null (Google, any existing single-flow family):
- The top-level `oauth.scopes` is the implicit single flow
- `FamilyConnectionProvider` treats the family as having one unnamed flow with `grant: authorization_code`
- No changes to `google.yaml` or any Google connector manifests

When `flows` is present:
- Top-level `oauth.scopes` is removed (scopes live per-flow)
- Top-level `oauth.auth-url`, `oauth.token-url`, `oauth.redirect-uri` are inherited defaults for flows that don't override

### 2. ConnectorManifest flow references

Add `flows` to `AuthDeclaration`:

```java
public record AuthDeclaration(
        String type,
        String family,
        List<String> flows,                      // NEW — null = primary flow
        FamilyManifest.OAuthDeclaration oauth,
        List<FieldDeclaration> fields) {}
```

Per-module manifests:

```yaml
# calendar-microsoft
connector:
  id: calendar-microsoft
  platform: calendar
  auth:
    type: oauth2
    family: microsoft
    flows: [delegated]
  provides:
    - capability: calendar
      sub: [list-calendars, list-events, get-event, create-event, update-event, delete-event, sync-events]

# document-microsoft
connector:
  id: document-microsoft
  platform: document
  auth:
    type: oauth2
    family: microsoft
    flows: [delegated]
  produces:
    - bean: microsoft-onedrive
    - bean: microsoft-sharepoint
  provides:
    - capability: document
      sub: [file-operations, folder-operations, search-operations, sharing-operations]

# chat-teams
connector:
  id: chat-teams
  platform: chat
  auth:
    type: oauth2
    family: microsoft
    flows: [delegated, application, bot]
  provides:
    - capability: chat
      sub: [messaging, threading, discovery, presence, reactions, members,
            channel-management, member-management, message-history]

# email-microsoft (cleanup — remove redundant inline fields)
connector:
  id: email-microsoft
  platform: email
  auth:
    type: oauth2
    family: microsoft
    flows: [delegated]
  provides:
    - capability: email
      sub: [receive, search]
```

When `flows` is null/omitted, `enrichWithFamily()` uses the family's primary flow (first entry, or the implicit single flow for families without a `flows` map).

### 3. Convention-based provider string resolution

`FamilyConnectionProvider` resolves provider strings to family + flow:

```
"microsoft"     → family: microsoft, flow: delegated (primary)
"microsoft-app" → family: microsoft, flow: application
"microsoft-bot" → family: microsoft, flow: bot
"google"        → family: google,    flow: (implicit single)
```

Algorithm:
1. Try `families.get(provider)` — exact match means primary flow (backward compat)
2. If no match, find the longest family ID prefix: split `provider` into `{familyId}-{flowSuffix}`
3. Look up `flowSuffix` in the family's `flows` map
4. If no match, throw `ServiceConnectionException`

Matching the longest prefix handles future families whose IDs might contain hyphens.

### 4. FamilyConnectionProvider client_credentials support

Current `doRefresh()` is hardcoded to `grant_type=refresh_token`. Add a parallel path for `client_credentials`:

```java
private TokenResult acquireApplicationToken(FamilyManifest family, FlowDeclaration flow) {
    String tokenUrl = resolveTokenUrl(family, flow);
    String clientId = facade.resolve(family.family().id(), "client-id");
    String clientSecret = facade.resolve(family.family().id(), "client-secret");

    // POST grant_type=client_credentials&client_id=...&client_secret=...&scope=...
    // Parse response: access_token, expires_in
    // Store via facade.store(providerKey, "access-token", token)
    // Store via facade.store(providerKey, "expires-at", expiresAt)
    // Return token
}
```

Decision flow in `getAccessToken()`:
1. Resolve family + flow from provider string
2. Check cached token + expiry
3. If expired or missing:
   - `authorization_code` flow → call existing `doRefresh()` (refresh_token grant)
   - `client_credentials` flow → call `acquireApplicationToken()` (no refresh token; re-acquire)

Credentials are resolved from the **family ID** namespace (e.g. `"microsoft"`), not the provider string — because all flows share one app registration.

### 5. DependencyResolver multi-flow awareness

`unmetRequirements()` currently checks one token per family. With named flows, it checks each declared flow independently:

For a module declaring `flows: [delegated, application]`:
- Delegated: check `facade.resolve("microsoft", "access-token")` — requires user consent redirect
- Application: check `facade.resolve(familyId, "client-id")` and `facade.resolve(familyId, "client-secret")` — if present, token can be auto-acquired; if absent, admin must provision credentials
- Generate separate `UnmetRequirement` entries for each unresolvable flow

The `ResolutionOption` distinguishes flow types:
- Delegated → `FamilyOAuthOption` (user consent redirect needed)
- Application/Bot → `FamilyClientCredentialsOption` (admin must provision client-id/client-secret in the credential facade; once credentials exist, tokens are acquired automatically)

### 6. enrichWithFamily() changes

Currently replaces the connector's `oauth` and `fields` wholesale from the family. With flows:
- `fields` still comes from the family (shared credentials)
- `oauth` comes from the primary flow's merged config (family defaults + flow overrides)
- The `flows` list is preserved on the enriched `AuthDeclaration`

### 7. OAuthConsentService scope awareness

`OAuthConsentService.buildAuthorizationUrl()` currently uses the family's top-level `oauth.scopes`. With flows, it reads scopes from the `delegated` flow. The `scopeOverride` parameter (for incremental consent) continues to work — it overrides the flow's scope list.

No changes needed for `client_credentials` flows — `OAuthConsentService` is never involved with them.

## What doesn't change

- `MicrosoftGraphClient` — still takes token at call time (credential-config-ownership protocol)
- `TeamsChatPlatform` provider strings — `"microsoft"`, `"microsoft-app"`, `"microsoft-bot"` already follow the convention
- `BotFrameworkClient` provider string — `"microsoft-bot"` already correct
- `OAuthConsentService` delegated flow — unchanged, just reads scopes from a different location
- `google.yaml` — no `flows` map, backward compatible
- Google connector manifests — no `flows` field, backward compatible
- `TokenRetry` — works with any token source

## File inventory

| File | Change |
|------|--------|
| `connectors-api/.../FamilyManifest.java` | Add `FlowDeclaration`, `GrantType`, `flows` field |
| `connectors-api/.../FamilyManifestLoader.java` | No change (Jackson handles new fields) |
| `connectors-api/.../ConnectorManifest.java` | Add `flows` to `AuthDeclaration` |
| `connectors-api/.../ConnectorManifestLoader.java` | Update `enrichWithFamily()` for flow-aware enrichment |
| `connectors-api/.../DependencyResolver.java` | Multi-flow unmet requirement checks |
| `graphql/.../FamilyConnectionProvider.java` | Provider string parsing, `client_credentials` grant support |
| `graphql/.../OAuthConsentService.java` | Read scopes from flow instead of top-level oauth |
| `connectors-api/src/main/resources/META-INF/families/microsoft.yaml` | Add `flows` map, move scopes into flows |
| `email-microsoft/src/main/resources/META-INF/connectors/email-microsoft.yaml` | Remove redundant fields, add `flows: [delegated]` |
| `calendar-microsoft/src/main/resources/META-INF/connectors/calendar-microsoft.yaml` | New manifest |
| `document-microsoft/src/main/resources/META-INF/connectors/document-microsoft.yaml` | New manifest |
| `chat-teams/src/main/resources/META-INF/connectors/chat-teams.yaml` | New manifest |

## Testing

- Unit test: `FamilyManifestLoader` deserializes `microsoft.yaml` with flows
- Unit test: `FamilyConnectionProvider` resolves `"microsoft"`, `"microsoft-app"`, `"microsoft-bot"` to correct family+flow
- Unit test: `FamilyConnectionProvider` acquires `client_credentials` token
- Unit test: `DependencyResolver` generates per-flow `UnmetRequirement` entries
- Unit test: `enrichWithFamily()` handles flow-aware enrichment
- Unit test: backward compatibility — Google family (no flows) works unchanged
- Integration test: `MicrosoftFamilyConsentIntegrationTest` — round-trip delegated consent + application token acquisition

## References

- `connectors-api/.../FamilyManifest.java` — current single-flow model
- `graphql/.../FamilyConnectionProvider.java` — current token resolution
- `graphql/.../OAuthConsentService.java` — PKCE consent flow
- `connectors-api/.../DependencyResolver.java` — backward chaining
- `chat-teams/.../TeamsChatPlatform.java` — dual token usage (`resolveDelegatedToken`, `resolveApplicationToken`)
- `chat-teams/.../BotFrameworkClient.java` — bot token usage
- `META-INF/families/microsoft.yaml` — existing family manifest
- `META-INF/families/google.yaml` — reference single-flow family
- Microsoft Bot Framework REST Connector Authentication docs — token endpoints and scopes
- casehubio/connectors#375 — Google family manifest (established the pattern)
- casehubio/connectors#373 — PersistentCredentialFacade (landed, provides write-through storage)
