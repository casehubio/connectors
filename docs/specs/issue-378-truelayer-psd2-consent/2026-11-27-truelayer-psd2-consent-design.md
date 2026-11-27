# TrueLayer PSD2 Consent Flow — Manifest Integration Design

**Issue:** casehubio/connectors#378
**Branch:** issue-378-truelayer-psd2-consent
**Date:** 2026-11-27

## Problem

`bank-truelayer` has a working PSD2 consent lifecycle (`TrueLayerConsentService`) with per-user token storage via `ConsentTokenStore`. It needs manifest integration so the credential-input flow can discover and trigger the OAuth redirect, and so `FamilyConnectionProvider` can resolve per-user tokens through the standard `ServiceConnectionProvider` interface.

The existing manifest declares `auth.type: oauth2` with inline fields but no OAuth endpoints. `FamilyConnectionProvider.getAccessToken(actorId, provider, tenancyId)` ignores `actorId` — every caller passes userId but the implementation only resolves tenant-scoped tokens from `ProviderCredentialFacade`.

## Design

### 1. UserTokenResolver CDI SPI

New interface in `connectors-api`:

```java
package io.casehub.connectors.lifecycle;

import java.util.Optional;

public interface UserTokenResolver {
    String providerId();
    Optional<String> getAccessToken(String userId);
}
```

Provider modules implement this for per-user token resolution. `FamilyConnectionProvider` discovers all `UserTokenResolver` beans at startup and indexes by `providerId()`.

### 2. per-user flag on FlowDeclaration

Add `perUser` to `FlowDeclaration` (from #376):

```java
public record FlowDeclaration(
        GrantType grant,
        List<String> scopes,
        @JsonProperty("token-url") String tokenUrl,
        @JsonProperty("per-user") Boolean perUser) {

    public boolean isPerUser() {
        return perUser != null && perUser;
    }
}
```

YAML:
```yaml
flows:
  delegated:
    grant: authorization_code
    scopes: [accounts, balance, transactions]
    per-user: true
```

Default is `false` — existing Google/Microsoft flows are unaffected.

### 3. actorId-aware FamilyConnectionProvider

Update `getAccessToken()` to check per-user flows:

```java
@Override
public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
    var resolved = resolveFamily(provider);

    // Per-user flow: delegate to UserTokenResolver
    if (actorId != null && resolved.flow() != null && resolved.flow().isPerUser()) {
        var resolver = userTokenResolvers.get(resolved.familyId());
        if (resolver == null) {
            // Fall back: try provider key directly
            resolver = userTokenResolvers.get(provider);
        }
        if (resolver != null) {
            var token = resolver.getAccessToken(actorId)
                    .orElseThrow(() -> new ServiceConnectionException(
                            "No user token for " + provider + " user " + actorId,
                            provider, actorId, Set.of(), Set.of(), Set.of()));
            return new ServiceAccessToken(token, null, Set.of());
        }
    }

    // Existing path: client_credentials or tenant-scoped authorization_code
    // ... (unchanged from #376)
}
```

Constructor discovers `UserTokenResolver` beans:

```java
@Inject
public FamilyConnectionProvider(Instance<ProviderCredentialFacade> facadeInstance,
                                 Instance<UserTokenResolver> resolverInstances) {
    this(facadeInstance.isResolvable() ? facadeInstance.get() : null,
         FamilyManifestLoader.discover(),
         buildResolverMap(resolverInstances));
}

private static Map<String, UserTokenResolver> buildResolverMap(Instance<UserTokenResolver> instances) {
    var map = new HashMap<String, UserTokenResolver>();
    if (instances.isResolvable()) {
        instances.forEach(r -> map.put(r.providerId(), r));
    }
    return map;
}
```

### 4. TrueLayerConsentService implements UserTokenResolver

```java
@ApplicationScoped
public class TrueLayerConsentService implements UserTokenResolver {

    @Override
    public String providerId() { return "truelayer"; }

    @Override
    public Optional<String> getAccessToken(String userId) {
        return Optional.ofNullable(getUserToken(userId));
    }

    // ... existing methods unchanged
}
```

No changes to the consent flow mechanics, storage, or lifecycle. The interface just exposes the existing `getUserToken(userId)` through the SPI.

### 5. Updated TrueLayer connector manifest

```yaml
connector:
  id: truelayer
  platform: bank
  description: "TrueLayer — UK Open Banking (AISP + PISP) via PSD2 consent"
  auth:
    type: oauth2
    oauth:
      auth-url: "{auth-url}/connect/auth"
      token-url: "{auth-url}/connect/token"
      redirect-uri: "{callback-base-url}/auth/truelayer/callback"
    flows:
      delegated:
        grant: authorization_code
        scopes:
          - accounts
          - balance
          - transactions
        per-user: true
      app:
        grant: client_credentials
    fields:
      - name: client-id
        sensitive: false
        description: "TrueLayer client ID"
      - name: client-secret
        description: "TrueLayer client secret"
      - name: auth-url
        required: false
        sensitive: false
        default: "https://auth.truelayer.com"
        description: "TrueLayer auth base URL"
      - name: base-url
        required: false
        sensitive: false
        default: "https://api.truelayer.com"
        description: "TrueLayer API base URL"
  provides:
    - capability: bank
      sub:
        - account-information
        - payment-initiation
  requires:
    - capability: email
      sub: [receive]
      reason: "PSD2 consent flow sends verification emails to the account holder"
```

Key additions:
- `oauth` block with auth-url/token-url/redirect-uri (uses `{auth-url}` template resolved from fields)
- `flows` with `delegated` (per-user: true) and `app` (client_credentials)
- Inline fields preserved (standalone, no family)

### 6. DependencyResolver per-user awareness

For flows with `per-user: true`, `DependencyResolver` checks whether a `UserTokenResolver` bean exists for the provider (via a new optional `Set<String> availableUserResolvers` parameter) rather than checking for a stored token — per-user tokens are created on demand via consent, not provisioned upfront.

For the `app` flow (client_credentials without per-user), the existing credential check applies: client-id + client-secret must be in the facade.

### 7. OAuthConsentService — no changes

TrueLayerConsentService handles its own consent flow. `OAuthConsentService` is not involved. The manifest's `oauth` block is declarative metadata for the credential-input UI to know where to redirect — it doesn't change how the consent code runs.

## What doesn't change

- `TrueLayerConsentService` consent flow mechanics (auth URL, code exchange, token refresh, state CSRF)
- `ConsentTokenStore` per-user storage (JPA persistence, `removeExpiredBefore()`, `StoredConsent` model)
- `TrueLayerClient` call-time token pattern
- `TrueLayerBankPlatform` token resolution via `consentService.getUserToken(userId)`
- `TrueLayerPaymentWebhook` (JWS signature validation, unrelated)
- `OAuthConsentService` (tenant-scoped consent, unchanged)
- Google/Microsoft flows (`per-user` defaults to false)
- Quarkus OIDC client_credentials for TrueLayer payments (stays independent)

## File inventory

| File | Change |
|------|--------|
| `connectors-api/.../UserTokenResolver.java` | New interface |
| `connectors-api/.../FamilyManifest.java` | Add `perUser` to `FlowDeclaration` |
| `graphql/.../FamilyConnectionProvider.java` | actorId-aware routing, discover UserTokenResolver beans |
| `connectors-api/.../DependencyResolver.java` | Per-user flow requirement checking |
| `bank-truelayer/.../TrueLayerConsentService.java` | Implement `UserTokenResolver` |
| `bank-truelayer/src/main/resources/META-INF/connector-manifest.yaml` | Add oauth block, flows, per-user flag |

## Testing

- Unit test: `FlowDeclaration` with `per-user: true` deserializes correctly
- Unit test: `FamilyConnectionProvider` delegates to `UserTokenResolver` when actorId non-null and flow is per-user
- Unit test: `FamilyConnectionProvider` falls through to tenant path when actorId is null or flow is not per-user
- Unit test: `DependencyResolver` per-user flow checks resolver availability, not stored tokens
- Unit test: `TrueLayerConsentService` implements `UserTokenResolver` correctly
- Unit test: backward compatibility — Google/Microsoft flows unaffected (no per-user flag)
- Integration test: TrueLayer manifest loads with flows and per-user flag

## References

- `bank-truelayer/.../TrueLayerConsentService.java` — existing PSD2 consent lifecycle
- `bank-truelayer/.../ConsentTokenStore.java` — per-user token storage interface
- `bank-truelayer/.../StoredConsent.java` — per-user consent record
- `bank-truelayer/.../TrueLayerBankPlatform.java` — token resolution via getUserToken()
- `graphql/.../FamilyConnectionProvider.java` — actorId parameter ignored
- `connectors-api/.../FamilyManifest.java` — FlowDeclaration from #376
- `platform-api/.../ServiceConnectionProvider.java` — actorId in interface contract
- casehubio/connectors#376 — Microsoft OAuth family (established multi-flow pattern)
- casehubio/connectors#375 — Google OAuth family (established family pattern)
- casehubio/connectors#373 — PersistentCredentialFacade (landed)
