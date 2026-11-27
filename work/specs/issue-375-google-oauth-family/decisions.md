# Decisions — #375 Google OAuth Family

## D1: Connector-side FamilyConnectionProvider (REVISED after decision review)

**Choice:** Create `FamilyConnectionProvider` in connectors, implementing `ServiceConnectionProvider` from platform-api, that reads tokens from `ProviderCredentialFacade` and handles refresh internally
**Alternatives:**
- OAuthConsentService writes to OAuthTokenStore (original D1) — rejected: per-actor data model mismatch (`OAuthTokenStore` stores per actorId; consent flow stores under `actorId="system"`; runtime calls with real userId); also adds unnecessary coupling to platform token store
- Bridge via ProviderCredentialFacade → OAuthTokenStore adapter — lossy mapping between flat key-value and rich OAuthTokenRecord
- Google modules read from ProviderCredentialFacade directly — breaks the `ServiceConnectionProvider` contract
**Rationale:** The consent flow writes tokens to `ProviderCredentialFacade` (already implemented). A `FamilyConnectionProvider` reads them from there, implementing the `ServiceConnectionProvider` interface that Google platform classes already consume. Token refresh is handled internally using the family manifest's token URL and stored client-id/client-secret — `OAuthConsentService.postTokenRequest()` already has the HTTP logic. This keeps all credential lifecycle within connectors — correct dependency direction (connectors → platform-api), no platform-side changes needed.
**Trade-offs:** Introduces a second `ServiceConnectionProvider` implementation alongside the platform's `ServiceConnectionProviderCore`. CDI resolution needs `@Alternative @Priority` or deployment-profile gating to pick the right one. In full-platform deployments, may need a composite that checks platform-level social login tokens first, falls back to facade-based family tokens.
**Sources:** OAuthConsentService.java (connectors/graphql), ProviderCredentialFacade.java (connectors-api), ServiceConnectionProvider.java (platform/platform-api), ServiceConnectionProviderCore.java (platform/authn-social-core), FamilyManifest.java (connectors-api)
**Exploration:** deep-analysis (revised after decision review R1-03, R1-04, R1-11)
**Status:** revised

## D2: Consent strategy — incremental with upfront default

**Choice:** Incremental consent mechanism with upfront (all family scopes) as default
**Alternatives:**
- Upfront only — simpler but no granular control; requests all permissions even if user wants only Calendar
- Incremental only — more consent screens; no single-auth-covers-all option
**Rationale:** Default path requests all 8 family scopes in one consent — user authenticates once and all 4 connectors become READY. Individual connectors can be enabled/disabled later via incremental consent (adding scopes via re-consent with `include_granted_scopes=true`). Read/write split maps naturally to per-module scope pairs.
**Trade-offs:** `include_granted_scopes=true` must be added to `OAuthConsentService.buildAuthorizationUrl()`. Disabling a connector is application-side (Google supports granular scope removal via account settings, so scope tracking is needed for degradation detection). `OAuthConsentService.buildAuthorizationUrl()` currently requests all family scopes — per-connector scope subsets require accepting an optional scope parameter.
**Sources:** google.yaml family manifest, Google OAuth incremental authorization docs, `@RequiresScopes` annotations on Google platform classes
**Exploration:** deep-analysis
**Status:** captured

## D3: Refresh handled connector-side by FamilyConnectionProvider (REVISED)

**Choice:** Token refresh is handled by `FamilyConnectionProvider` within connectors, not by platform-side `OAuthTokenManagerCore`
**Alternatives:**
- Platform-side refresh via OAuthTokenManagerCore fallback to ProviderCredentialFacade (original D3) — rejected: `ProviderCredentialFacade` is in connectors-api, platform code cannot depend on it (dependency direction violation)
- Require `AuthnConfig.social().google` to be configured for refresh — duplicates credentials
**Rationale:** `FamilyConnectionProvider` owns the full token lifecycle: resolve access-token from facade, check expiry, refresh using stored refresh-token + client-id/client-secret via the family manifest's token URL. `OAuthConsentService.postTokenRequest()` already has the HTTP call logic — extract it as a reusable method. All dependencies are within connectors.
**Depends on:** D1 (FamilyConnectionProvider is where refresh lives)
**Sources:** OAuthConsentService.postTokenRequest() (connectors/graphql), FamilyManifest.OAuthDeclaration.tokenUrl() (connectors-api)
**Exploration:** quick (revised after decision review R1-11, R1-13)
**Status:** revised

## D4: Family-aware DependencyResolver (REVISED after decision review)

**Choice:** `DependencyResolver` auto-infers OAuth requirements from `auth.family` during construction — no explicit `requires` declaration needed, but the unified dependency view is preserved
**Alternatives:**
- Explicit `requires: [{capability: oauth2, sub: [google]}]` on each manifest — redundant with `auth.family: google`, requires a virtual provider for resolution
- Skip dependency resolution for OAuth entirely (original D4) — creates blind spot in `DependencyResolver.unmetRequirements()`, agents can't discover that a Google connector needs OAuth consent
**Rationale:** `DependencyResolver` already receives connector manifests in its constructor. It can read `auth.type: oauth2` + `auth.family` and auto-generate an implicit requirement. Resolution checks `ProviderCredentialFacade.isConfigured(familyId, List.of("access-token"))` or similar. This preserves the unified `unmetRequirements()` API without redundant manifest declarations.
**Trade-offs:** `DependencyResolver` gains awareness of family OAuth — slightly more complex than pure `provides`/`requires` matching. But the complexity is contained in one place.
**Sources:** DependencyResolver.java (connectors-api), ConnectorManifest.AuthDeclaration (connectors-api)
**Exploration:** quick (revised after decision review R1-16, R1-17)
**Status:** revised
