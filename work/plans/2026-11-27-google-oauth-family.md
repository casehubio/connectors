# Google OAuth Family Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #375 — google-oauth-family
**Issue group:** #375

**Goal:** Enable single Google OAuth consent that activates all four Google connectors (Calendar, Drive, Contacts, Gmail) with token refresh and family-aware dependency resolution.

**Architecture:** `FamilyConnectionProvider` implements `ServiceConnectionProvider` (platform-api) in connectors, reading tokens from `ProviderCredentialFacade` and refreshing via the family manifest's token URL. `DependencyResolver` auto-infers OAuth requirements from `auth.family`. `OAuthConsentService` gains `include_granted_scopes` support and stores `expires-at` for refresh.

**Tech Stack:** Java 21, Quarkus 3.32.2, platform-api (`ServiceConnectionProvider`, `ServiceAccessToken`, `ServiceConnection`)

## Global Constraints

- Java source level 21 (running on Java 26 JVM)
- Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
- Use `mvn` not `./mvnw`
- Use `HttpHelper.CLIENT` for outbound HTTP (protocol: `connectors/shared-http-client.md`)
- Connector manifests at `META-INF/connector-manifest.yaml`
- Family manifests at `META-INF/families/*.yaml`
- IntelliJ MCP for all code navigation and structural editing

---

## Batch 1: Manifests and OAuthConsentService enhancements

### Task 1: Create connector manifests for calendar-google, document-google, contacts-google; simplify email-google

**Files:**
- Create: `calendar-google/src/main/resources/META-INF/connector-manifest.yaml`
- Create: `document-google/src/main/resources/META-INF/connector-manifest.yaml`
- Create: `contacts-google/src/main/resources/META-INF/connector-manifest.yaml`
- Modify: `email-google/src/main/resources/META-INF/connector-manifest.yaml`
- Test: `connectors-api/src/test/java/io/casehub/connectors/lifecycle/ConnectorManifestLoaderTest.java`

**Interfaces:**
- Consumes: `ConnectorManifestLoader.resolveFamilies()`, `FamilyManifestLoader.discover()`, `google.yaml` family manifest
- Produces: Four connector manifests discoverable via `ConnectorManifestLoader.discoverFromClasspath()`

- [ ] **Step 1: Write a failing test that verifies all four Google connectors are discovered with family enrichment**

In `ConnectorManifestLoaderTest.java`, add a test that loads manifests from a test classpath containing the four Google manifests and the google family, and asserts:
- 4 Google connector manifests discovered
- Each has `auth.oauth()` non-null (enriched from family)
- Each has `auth.family()` == `"google"`
- `credentialKey()` returns `"google"` for all four
- `email-google` provides `email` capability with `[receive, search]`
- `calendar-google` provides `calendar` capability
- `document-google` provides `document` capability with `[files, folders, search, sharing]`
- `contacts-google` provides `contacts` capability with `[read, groups, write]`

```java
@Test
void discover_resolves_all_google_family_connectors() {
    var manifests = ConnectorManifestLoader.discoverFromClasspath();
    var googleConnectors = manifests.stream()
            .filter(m -> "google".equals(m.connector().credentialKey()))
            .toList();

    assertThat(googleConnectors).hasSize(4);
    for (var m : googleConnectors) {
        assertThat(m.connector().auth().oauth()).isNotNull();
        assertThat(m.connector().auth().family()).isEqualTo("google");
    }

    var byId = googleConnectors.stream()
            .collect(java.util.stream.Collectors.toMap(m -> m.connector().id(), m -> m));

    assertThat(byId.get("calendar-google").connector().provides())
            .extracting(ConnectorManifest.CapabilityDeclaration::capability)
            .containsExactly("calendar");
    assertThat(byId.get("document-google").connector().provides())
            .extracting(ConnectorManifest.CapabilityDeclaration::capability)
            .containsExactly("document");
    assertThat(byId.get("contacts-google").connector().provides())
            .extracting(ConnectorManifest.CapabilityDeclaration::capability)
            .containsExactly("contacts");
    assertThat(byId.get("email-google").connector().provides())
            .extracting(ConnectorManifest.CapabilityDeclaration::capability)
            .containsExactly("email");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl connectors-api -Dtest=ConnectorManifestLoaderTest#discover_resolves_all_google_family_connectors -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — only `email-google` manifest exists, the other three are missing.

- [ ] **Step 3: Create the three new connector manifests**

`calendar-google/src/main/resources/META-INF/connector-manifest.yaml`:
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

`document-google/src/main/resources/META-INF/connector-manifest.yaml`:
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

`contacts-google/src/main/resources/META-INF/connector-manifest.yaml`:
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

- [ ] **Step 4: Simplify the email-google manifest**

Replace `email-google/src/main/resources/META-INF/connector-manifest.yaml` with:
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

- [ ] **Step 5: Run the test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl connectors-api -Dtest=ConnectorManifestLoaderTest#discover_resolves_all_google_family_connectors -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

Note: This test may need adjustment depending on the test classpath — the test module may not see `calendar-google`'s resources. If so, create equivalent test manifests under `connectors-api/src/test/resources/META-INF/` and test with `resolveFamilies()` directly using those test fixtures.

- [ ] **Step 6: Commit**

```bash
git add calendar-google/src/main/resources/META-INF/connector-manifest.yaml document-google/src/main/resources/META-INF/connector-manifest.yaml contacts-google/src/main/resources/META-INF/connector-manifest.yaml email-google/src/main/resources/META-INF/connector-manifest.yaml
git commit -m "feat(#375): connector manifests for all Google family members

Create manifests for calendar-google, document-google, contacts-google.
Simplify email-google manifest to minimal family reference.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 2: OAuthConsentService — store `expires-at` and add `include_granted_scopes`

**Files:**
- Modify: `graphql/src/main/java/io/casehub/connectors/graphql/OAuthConsentService.java`
- Modify: `connectors-api/src/main/java/io/casehub/connectors/lifecycle/FamilyManifest.java`
- Test: `graphql/src/test/java/io/casehub/connectors/graphql/OAuthConsentServiceTest.java`

**Interfaces:**
- Consumes: `ProviderCredentialFacade.store()`, `FamilyManifest.ConsentStrategy.INCREMENTAL`
- Produces: `expires-at` field stored in facade alongside tokens; `include_granted_scopes=true` in auth URL for incremental families; `buildAuthorizationUrl(credentialKey)` overload accepting optional `Set<String> scopeOverride`

- [ ] **Step 1: Write failing test for `expires-at` storage**

```java
@Test
void exchangeCode_stores_expires_at_in_facade() throws Exception {
    var tokenJson = """
            {"access_token":"at","refresh_token":"rt","expires_in":3600}""";
    service = createTestableService(tokenJson);
    var request = service.buildAuthorizationUrl("google");

    service.exchangeCode("auth-code", request.state());

    assertThat(facade.resolve("google", "expires-at")).isPresent();
    var expiresAt = Instant.parse(facade.resolve("google", "expires-at").get());
    assertThat(expiresAt).isAfter(Instant.now().plusSeconds(3500));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl graphql -Dtest=OAuthConsentServiceTest#exchangeCode_stores_expires_at_in_facade -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `expires-at` not stored

- [ ] **Step 3: Implement `expires-at` storage in `exchangeCode()`**

In `OAuthConsentService.exchangeCode()`, after storing `access-token` and `refresh-token`, add:
```java
credentialFacade.store(credentialKey, "expires-at", expiresAt.toString());
```

- [ ] **Step 4: Run test to verify it passes**

Run same command as Step 2.
Expected: PASS

- [ ] **Step 5: Write failing test for `include_granted_scopes` on incremental families**

```java
@Test
void buildAuthorizationUrl_includes_granted_scopes_for_incremental_family() {
    var request = service.buildAuthorizationUrl("google");
    assertThat(request.url()).contains("include_granted_scopes=true");
}
```

- [ ] **Step 6: Run test to verify it fails**

Expected: FAIL — `include_granted_scopes` not in URL

- [ ] **Step 7: Implement `include_granted_scopes` in `buildAuthorizationUrl()`**

In `OAuthConsentService.buildAuthorizationUrl()`, after the existing URL construction, look up the family manifest's consent strategy. If the `OAuthDeclaration` came from a family with `consent: incremental`, append `&include_granted_scopes=true`.

This requires the `OAuthConfigRegistry` to also carry the `ConsentStrategy`. Modify `OAuthConfigRegistry` to store `record OAuthConfig(FamilyManifest.OAuthDeclaration oauth, FamilyManifest.ConsentStrategy consent)` instead of bare `OAuthDeclaration`. Update `OAuthConfigRegistryCreator` and `FamilyManifestLoader` accordingly.

Then in `buildAuthorizationUrl()`:
```java
var config = oauthConfigs.lookup(credentialKey).orElseThrow(...);
// ... existing URL construction using config.oauth() ...
if (config.consent() == FamilyManifest.ConsentStrategy.INCREMENTAL) {
    sb.append("&include_granted_scopes=true");
}
```

- [ ] **Step 8: Run test to verify it passes**

Expected: PASS

- [ ] **Step 9: Write failing test for scope override parameter**

```java
@Test
void buildAuthorizationUrl_uses_scope_override_when_provided() {
    var overrideScopes = Set.of("https://www.googleapis.com/auth/calendar.readonly");
    var request = service.buildAuthorizationUrl("google", overrideScopes);
    assertThat(request.url()).contains("calendar.readonly");
    assertThat(request.url()).doesNotContain("gmail");
}
```

- [ ] **Step 10: Add `buildAuthorizationUrl` overload accepting scope override**

```java
public AuthorizationRequest buildAuthorizationUrl(String credentialKey, Set<String> scopeOverride) {
    // Same logic as existing, but use scopeOverride instead of oauth.scopes() when non-null
}

public AuthorizationRequest buildAuthorizationUrl(String credentialKey) {
    return buildAuthorizationUrl(credentialKey, null);
}
```

- [ ] **Step 11: Run all OAuthConsentServiceTest tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl graphql -Dtest=OAuthConsentServiceTest`
Expected: ALL PASS

- [ ] **Step 12: Store `granted-scopes` in facade after token exchange**

In `exchangeCode()`, after storing tokens, also store the scopes that were granted. Google's token response includes a `scope` field with space-separated granted scopes:

```java
var grantedScope = json.getString("scope", null);
if (grantedScope != null) {
    credentialFacade.store(credentialKey, "granted-scopes", grantedScope);
}
```

- [ ] **Step 13: Commit**

```bash
git add graphql/src/main/java/io/casehub/connectors/graphql/OAuthConsentService.java connectors-api/src/main/java/io/casehub/connectors/lifecycle/OAuthConfigRegistry.java graphql/src/test/java/io/casehub/connectors/graphql/OAuthConsentServiceTest.java
git commit -m "feat(#375): OAuthConsentService — expires-at, include_granted_scopes, scope override

Store expires-at and granted-scopes in ProviderCredentialFacade.
Add include_granted_scopes=true for incremental consent families.
Add buildAuthorizationUrl overload for per-connector scope subsets.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Batch 2: FamilyConnectionProvider

### Task 3: FamilyConnectionProvider — token resolution and refresh

**Files:**
- Create: `connectors-api/src/main/java/io/casehub/connectors/lifecycle/FamilyConnectionProvider.java`
- Create: `connector-lifecycle/src/main/java/io/casehub/connectors/lifecycle/deployment/FamilyConnectionProviderCreator.java`
- Modify: `connector-lifecycle/src/main/java/io/casehub/connectors/lifecycle/deployment/ConnectorLifecycleProcessor.java`
- Test: `connectors-api/src/test/java/io/casehub/connectors/lifecycle/FamilyConnectionProviderTest.java`

**Interfaces:**
- Consumes: `ProviderCredentialFacade.resolve()`, `ProviderCredentialFacade.store()`, `FamilyManifestLoader.discover()`, `FamilyManifest.OAuthDeclaration.tokenUrl()`, `HttpHelper.CLIENT`
- Produces: `ServiceConnectionProvider` implementation — `getAccessToken(actorId, provider, tenancyId)` returns `ServiceAccessToken` for family-based providers; `getConnection()` returns `ServiceConnection` with granted/missing scopes; `missingScopes()` for scope gap detection

- [ ] **Step 1: Write failing test for `getAccessToken` with valid token**

```java
@Test
void getAccessToken_returns_token_from_facade() {
    var facade = new InMemoryCredentialFacade(Map.of(
            "google", Map.of(
                    "access-token", "test-access-token",
                    "refresh-token", "test-refresh-token",
                    "expires-at", Instant.now().plusSeconds(3600).toString(),
                    "granted-scopes", "calendar.readonly gmail.readonly")));

    var families = Map.of("google", googleFamily());
    var provider = new FamilyConnectionProvider(facade, families);

    var token = provider.getAccessToken("user1", "google", "default");

    assertThat(token.accessToken()).isEqualTo("test-access-token");
    assertThat(token.expiresAt()).isAfter(Instant.now());
    assertThat(token.grantedScopes()).contains("calendar.readonly", "gmail.readonly");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl connectors-api -Dtest=FamilyConnectionProviderTest#getAccessToken_returns_token_from_facade -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement `FamilyConnectionProvider` with basic token resolution**

```java
package io.casehub.connectors.lifecycle;

import io.casehub.platform.api.authn.ServiceAccessToken;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.casehub.platform.api.authn.ServiceConnectionStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FamilyConnectionProvider implements ServiceConnectionProvider {

    private final ProviderCredentialFacade facade;
    private final Map<String, FamilyManifest> families;

    public FamilyConnectionProvider(ProviderCredentialFacade facade,
                                     Map<String, FamilyManifest> families) {
        this.facade = facade;
        this.families = Map.copyOf(families);
    }

    @Override
    public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
        if (!families.containsKey(provider)) {
            throw new ServiceConnectionException("No family for provider: " + provider,
                    provider, actorId, Set.of(), Set.of(), Set.of());
        }
        var accessToken = facade.resolve(provider, "access-token")
                .orElseThrow(() -> new ServiceConnectionException(
                        "No access token for provider: " + provider,
                        provider, actorId, Set.of(), Set.of(), Set.of()));

        var expiresAt = facade.resolve(provider, "expires-at")
                .map(Instant::parse)
                .orElse(null);

        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return refreshAndReturn(provider, actorId);
        }

        var scopes = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
        return new ServiceAccessToken(accessToken, expiresAt, scopes);
    }

    @Override
    public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
        var hasToken = facade.resolve(provider, "access-token").isPresent();
        var scopes = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
        var status = hasToken ? ServiceConnectionStatus.CONNECTED : ServiceConnectionStatus.DISCONNECTED;
        return new ServiceConnection(actorId, provider, tenancyId, status, scopes, Set.of(), null);
    }

    @Override
    public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
        return families.keySet().stream()
                .map(provider -> getConnection(actorId, provider, tenancyId))
                .toList();
    }

    @Override
    public Set<String> missingScopes(String actorId, String provider, String tenancyId) {
        return getConnection(actorId, provider, tenancyId).missingScopes();
    }

    private ServiceAccessToken refreshAndReturn(String provider, String actorId) {
        var refreshToken = facade.resolve(provider, "refresh-token")
                .orElseThrow(() -> new ServiceConnectionException(
                        "No refresh token for provider: " + provider,
                        provider, actorId, Set.of(), Set.of(), Set.of()));

        var family = families.get(provider);
        var tokenUrl = family.family().oauth().tokenUrl();
        var clientId = facade.resolve(provider, "client-id").orElseThrow();
        var clientSecret = facade.resolve(provider, "client-secret").orElseThrow();

        var body = "grant_type=refresh_token"
                + "&refresh_token=" + encode(refreshToken)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);

        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = io.casehub.connectors.http.HttpHelper.CLIENT.send(
                    request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServiceConnectionException(
                        "Token refresh failed: HTTP " + response.statusCode(),
                        provider, actorId, Set.of(), Set.of(), Set.of());
            }

            try (var reader = jakarta.json.Json.createReader(new java.io.StringReader(response.body()))) {
                var json = reader.readObject();
                var newAccessToken = json.getString("access_token");
                var expiresIn = json.getInt("expires_in", 3600);
                var newRefreshToken = json.getString("refresh_token", null);
                var newExpiresAt = Instant.now().plusSeconds(expiresIn);

                facade.store(provider, "access-token", newAccessToken);
                facade.store(provider, "expires-at", newExpiresAt.toString());
                if (newRefreshToken != null) {
                    facade.store(provider, "refresh-token", newRefreshToken);
                }

                var scopes = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
                return new ServiceAccessToken(newAccessToken, newExpiresAt, scopes);
            }
        } catch (ServiceConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceConnectionException(
                    "Token refresh failed: " + e.getMessage(),
                    provider, actorId, Set.of(), Set.of(), Set.of());
        }
    }

    private static Set<String> parseScopes(String scopeStr) {
        if (scopeStr == null || scopeStr.isBlank()) return Set.of();
        return Arrays.stream(scopeStr.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Write failing test for token refresh when expired**

Use WireMock or a test double that intercepts the HTTP call. `FamilyConnectionProvider` should call the token URL and store refreshed tokens:

```java
@Test
void getAccessToken_refreshes_expired_token() {
    var facade = new MutableInMemoryFacade(Map.of(
            "google", Map.of(
                    "access-token", "expired-token",
                    "refresh-token", "test-refresh",
                    "client-id", "test-client-id",
                    "client-secret", "test-secret",
                    "expires-at", Instant.now().minusSeconds(60).toString())));

    var provider = new FamilyConnectionProvider(facade, Map.of("google", googleFamily())) {
        @Override
        ServiceAccessToken refreshAndReturn(String p, String a) {
            // Override for unit test — verify refresh is called
            facade.store(p, "access-token", "refreshed-token");
            facade.store(p, "expires-at", Instant.now().plusSeconds(3600).toString());
            return new ServiceAccessToken("refreshed-token",
                    Instant.now().plusSeconds(3600), Set.of());
        }
    };

    var token = provider.getAccessToken("user1", "google", "default");
    assertThat(token.accessToken()).isEqualTo("refreshed-token");
}
```

- [ ] **Step 6: Run test to verify it passes**

Expected: PASS (refresh logic already implemented in Step 3)

- [ ] **Step 7: Write failing test for `getAccessToken` with unknown provider**

```java
@Test
void getAccessToken_throws_for_non_family_provider() {
    var provider = new FamilyConnectionProvider(
            new InMemoryCredentialFacade(Map.of()), Map.of("google", googleFamily()));

    assertThatThrownBy(() -> provider.getAccessToken("user1", "github", "default"))
            .isInstanceOf(ServiceConnectionException.class)
            .hasMessageContaining("No family");
}
```

- [ ] **Step 8: Run all FamilyConnectionProviderTest tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl connectors-api -Dtest=FamilyConnectionProviderTest`
Expected: ALL PASS

- [ ] **Step 9: Create `FamilyConnectionProviderCreator` and register as synthetic bean**

Create `FamilyConnectionProviderCreator` following the pattern of `DependencyResolverCreator`:
```java
package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.FamilyConnectionProvider;
import io.casehub.connectors.lifecycle.FamilyManifestLoader;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

public class FamilyConnectionProviderCreator implements BeanCreator<FamilyConnectionProvider> {
    @Override
    public FamilyConnectionProvider create(SyntheticCreationalContext<FamilyConnectionProvider> context) {
        var facade = context.getInjectedReference(ProviderCredentialFacade.class);
        var families = FamilyManifestLoader.discover();
        return new FamilyConnectionProvider(facade, families);
    }
}
```

Add a build step to `ConnectorLifecycleProcessor`:
```java
@BuildStep
SyntheticBeanBuildItem registerFamilyConnectionProvider() {
    return SyntheticBeanBuildItem.configure(io.casehub.platform.api.authn.ServiceConnectionProvider.class)
            .scope(Singleton.class)
            .setRuntimeInit()
            .defaultBean()
            .creator(FamilyConnectionProviderCreator.class)
            .addInjectionPoint(ClassType.create(
                    DotName.createSimple(ProviderCredentialFacade.class.getName())))
            .done();
}
```

Using `defaultBean()` so the platform's `ServiceConnectionProviderCore` takes priority when present, while `FamilyConnectionProvider` acts as the default for standalone/dev deployments.

- [ ] **Step 10: Commit**

```bash
git add connectors-api/src/main/java/io/casehub/connectors/lifecycle/FamilyConnectionProvider.java connectors-api/src/test/java/io/casehub/connectors/lifecycle/FamilyConnectionProviderTest.java connector-lifecycle/src/main/java/io/casehub/connectors/lifecycle/deployment/FamilyConnectionProviderCreator.java connector-lifecycle/src/main/java/io/casehub/connectors/lifecycle/deployment/ConnectorLifecycleProcessor.java
git commit -m "feat(#375): FamilyConnectionProvider — token resolution and refresh

Implements ServiceConnectionProvider for family-based OAuth connectors.
Reads tokens from ProviderCredentialFacade, refreshes via family token URL.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Batch 3: Family-aware DependencyResolver and integration

### Task 4: DependencyResolver — auto-infer OAuth requirements from `auth.family`

**Files:**
- Modify: `connectors-api/src/main/java/io/casehub/connectors/lifecycle/DependencyResolver.java`
- Modify: `connectors-api/src/main/java/io/casehub/connectors/lifecycle/ResolutionOption.java`
- Modify: `connector-lifecycle/src/main/java/io/casehub/connectors/lifecycle/deployment/DependencyResolverCreator.java`
- Test: `connectors-api/src/test/java/io/casehub/connectors/lifecycle/DependencyResolverTest.java`

**Interfaces:**
- Consumes: `ConnectorManifest.AuthDeclaration.family()`, `ConnectorManifest.AuthDeclaration.type()`, `ProviderCredentialFacade.resolve()`
- Produces: `DependencyResolver.unmetRequirements()` returns family OAuth requirements for unconfigured family-based connectors; `ResolutionOption.FamilyOAuthOption(familyId, List<String> memberConnectorIds)` variant

- [ ] **Step 1: Write failing test for family OAuth as unmet requirement**

```java
@Test
void unmetRequirements_includes_family_oauth_when_unconfigured() {
    var manifest = new ConnectorManifest(new ConnectorDeclaration(
            "calendar-google", "calendar", "Google Calendar",
            new AuthDeclaration("oauth2", "google", null, List.of()),
            List.of(), List.of(), List.of(), null, null));

    var facade = new InMemoryCredentialFacade(Map.of());
    var registry = new ConnectorRegistry();
    registry.register(manifest, ConnectorStatus.UNCONFIGURED);

    var resolver = new DependencyResolver(List.of(manifest), facade);
    var unmet = resolver.unmetRequirements("calendar-google", registry);

    assertThat(unmet).hasSize(1);
    assertThat(unmet.get(0).capability()).isEqualTo("oauth2");
    assertThat(unmet.get(0).sub()).containsExactly("google");
    assertThat(unmet.get(0).options()).hasAtLeastOneElementOfType(ResolutionOption.FamilyOAuthOption.class);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl connectors-api -Dtest=DependencyResolverTest#unmetRequirements_includes_family_oauth_when_unconfigured -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — no family-aware logic

- [ ] **Step 3: Add `FamilyOAuthOption` to `ResolutionOption`**

```java
public sealed interface ResolutionOption {
    record ProviderOption(String connectorId, ConnectorStatus status, String setupPlaybook) implements ResolutionOption {}
    record ManualOption(String capability) implements ResolutionOption {}
    record FamilyOAuthOption(String familyId, List<String> memberConnectorIds) implements ResolutionOption {}
}
```

- [ ] **Step 4: Extend `DependencyResolver` constructor and `unmetRequirements()`**

Add `ProviderCredentialFacade` parameter to constructor. During manifest scan, track family-based connectors. In `unmetRequirements()`, check if the connector has `auth.type == "oauth2"` and `auth.family != null`, and if so, check whether `facade.resolve(familyId, "access-token")` is present. If absent, add an `UnmetRequirement` with capability `"oauth2"`, sub `[familyId]`, and a `FamilyOAuthOption`.

```java
public DependencyResolver(List<ConnectorManifest> manifests, ProviderCredentialFacade facade) {
    this.facade = facade;
    // ... existing init ...
    for (var m : manifests) {
        var auth = m.connector().auth();
        if (auth != null && "oauth2".equals(auth.type()) && auth.family() != null) {
            familyMembers.computeIfAbsent(auth.family(), k -> new ArrayList<>())
                    .add(m.connector().id());
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Expected: PASS

- [ ] **Step 6: Write test for satisfied family OAuth (tokens present)**

```java
@Test
void unmetRequirements_empty_when_family_oauth_configured() {
    var manifest = new ConnectorManifest(new ConnectorDeclaration(
            "calendar-google", "calendar", "Google Calendar",
            new AuthDeclaration("oauth2", "google", null, List.of()),
            List.of(), List.of(), List.of(), null, null));

    var facade = new InMemoryCredentialFacade(Map.of(
            "google", Map.of("access-token", "valid-token")));
    var registry = new ConnectorRegistry();
    registry.register(manifest, ConnectorStatus.READY);

    var resolver = new DependencyResolver(List.of(manifest), facade);
    var unmet = resolver.unmetRequirements("calendar-google", registry);

    assertThat(unmet).isEmpty();
}
```

- [ ] **Step 7: Run test to verify it passes**

Expected: PASS

- [ ] **Step 8: Update `DependencyResolverCreator` to pass facade**

Modify `DependencyResolverCreator.create()` to inject `ProviderCredentialFacade` from CDI and pass it to the constructor.

- [ ] **Step 9: Preserve backward compatibility for the no-facade constructor**

Keep the existing `DependencyResolver(List<ConnectorManifest>)` constructor that delegates to the new one with a null facade (skips family OAuth checks):

```java
public DependencyResolver(List<ConnectorManifest> manifests) {
    this(manifests, null);
}
```

- [ ] **Step 10: Run all DependencyResolverTest tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl connectors-api -Dtest=DependencyResolverTest`
Expected: ALL PASS

- [ ] **Step 11: Commit**

```bash
git add connectors-api/src/main/java/io/casehub/connectors/lifecycle/DependencyResolver.java connectors-api/src/main/java/io/casehub/connectors/lifecycle/ResolutionOption.java connectors-api/src/test/java/io/casehub/connectors/lifecycle/DependencyResolverTest.java connector-lifecycle/src/main/java/io/casehub/connectors/lifecycle/deployment/DependencyResolverCreator.java
git commit -m "feat(#375): family-aware DependencyResolver

Auto-infer OAuth requirements from auth.family on connector manifests.
Add FamilyOAuthOption resolution variant.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 5: Integration test — end-to-end family consent flow

**Files:**
- Create: `integration-tests/src/test/java/io/casehub/connectors/it/GoogleFamilyConsentIntegrationTest.java`
- Modify: `integration-tests/src/test/resources/META-INF/connector-manifest.yaml` (add Google family manifests for test classpath)

**Interfaces:**
- Consumes: `OAuthConsentService`, `OAuthCallbackEndpoint`, `FamilyConnectionProvider`, `ConnectorRegistry`, `DependencyResolver`
- Produces: End-to-end verification that a single Google consent activates all 4 family connectors

- [ ] **Step 1: Write integration test verifying the full flow**

The test should:
1. Set up `ProviderCredentialFacade` with client-id and client-secret for `"google"`
2. Call `OAuthConsentService.buildAuthorizationUrl("google")` and verify the URL contains all 8 scopes and `include_granted_scopes=true`
3. Simulate the callback by calling `exchangeCode()` with a stubbed token response (override `postTokenRequest()`)
4. Verify tokens stored in `ProviderCredentialFacade`: access-token, refresh-token, expires-at, granted-scopes
5. Create `FamilyConnectionProvider` and verify `getAccessToken("user1", "google", "default")` returns the stored token
6. Verify `ConnectorRegistry` shows all 4 Google connectors as READY after `updateStatusByCredentialKey("google")`
7. Verify `DependencyResolver.unmetRequirements()` returns empty for all 4 Google connectors

```java
@Test
void single_google_consent_activates_all_family_connectors() throws Exception {
    // Setup
    var facade = new PersistentCredentialFacade(tempDir.resolve("credentials.yaml"));
    facade.store("google", "client-id", "test-client-id");
    facade.store("google", "client-secret", "test-secret");

    var families = FamilyManifestLoader.discover();
    var manifests = ConnectorManifestLoader.discoverFromClasspath();

    var registry = new ConnectorRegistry(facade);
    for (var m : manifests) {
        if ("google".equals(m.connector().credentialKey())) {
            registry.register(m, ConnectorStatus.UNCONFIGURED);
        }
    }

    // Build auth URL
    var oauthConfigs = buildOAuthConfigs(manifests);
    var consentService = new TestableOAuthConsentService(
            oauthConfigs, facade, null, registry, "http://localhost:8080");

    var authRequest = consentService.buildAuthorizationUrl("google");
    assertThat(authRequest.url()).contains("include_granted_scopes=true");
    assertThat(authRequest.url()).contains("calendar.readonly");
    assertThat(authRequest.url()).contains("gmail.readonly");

    // Simulate callback
    consentService.exchangeCode("test-auth-code", authRequest.state());

    // Verify tokens stored
    assertThat(facade.resolve("google", "access-token")).hasValue("test-access-token");
    assertThat(facade.resolve("google", "refresh-token")).hasValue("test-refresh-token");
    assertThat(facade.resolve("google", "expires-at")).isPresent();

    // Verify FamilyConnectionProvider works
    var connectionProvider = new FamilyConnectionProvider(facade, families);
    var token = connectionProvider.getAccessToken("user1", "google", "default");
    assertThat(token.accessToken()).isEqualTo("test-access-token");

    // Verify all connectors READY
    registry.updateStatusByCredentialKey("google");
    var googleConnectors = registry.all().stream()
            .filter(r -> "google".equals(r.manifest().connector().credentialKey()))
            .toList();
    assertThat(googleConnectors).allSatisfy(r ->
            assertThat(r.status()).isEqualTo(ConnectorStatus.READY));

    // Verify no unmet requirements
    var resolver = new DependencyResolver(manifests, facade);
    for (var r : googleConnectors) {
        assertThat(resolver.unmetRequirements(r.manifest().connector().id(), registry)).isEmpty();
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl integration-tests -Dtest=GoogleFamilyConsentIntegrationTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add integration-tests/
git commit -m "test(#375): integration test — single Google consent activates all family connectors

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Run full build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit any fixes from full build**

---

## References

- [2026-11-27-google-oauth-family-design.md] — design spec this plan implements
- [decisions.md] — D1-D4 design decisions (revised after decision review)
- [connectors-api/src/main/resources/META-INF/families/google.yaml] — Google family manifest
- [graphql/src/main/java/io/casehub/connectors/graphql/OAuthConsentService.java] — consent flow
- [connectors-api/src/main/java/io/casehub/connectors/lifecycle/ConnectorManifestLoader.java:78-105] — `resolveFamilies()` and `enrichWithFamily()`
- [connectors-api/src/main/java/io/casehub/connectors/lifecycle/DependencyResolver.java] — backward-chaining resolution
- [connectors-api/src/main/java/io/casehub/connectors/lifecycle/ProviderCredentialFacade.java] — credential store interface
- [platform/platform-api/src/main/java/io/casehub/platform/api/authn/ServiceConnectionProvider.java] — runtime token interface
- [platform/platform-api/src/main/java/io/casehub/platform/api/authn/ServiceAccessToken.java] — token record
- [decision review](/Users/mdproctor/reviews/casehub-connectors/issue-375-decision-20261127-150122/) — R1-03, R1-04, R1-11, R1-16
- [GitHub #375] — focal issue
