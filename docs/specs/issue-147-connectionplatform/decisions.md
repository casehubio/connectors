# Decisions — ConnectionPlatform (#147)

## D1: Credential type scope

**Choice:** All credential types — OAuth2 tokens, bot tokens, and API keys
**Alternatives:**
- OAuth2 only — simpler but leaves bot tokens and API keys as ad-hoc config, no unified resolution
- OAuth2 + bot tokens — includes tokens with lifecycle but excludes API keys, inconsistent boundary
**Rationale:** ConnectionPlatform becomes the single entry point for all user-consented and user-scoped external credentials. Static credentials (API keys, bot tokens) are modelled as StaticConnection with no consent flow. `GoogleMapsKeyResolver.resolve(userId)` confirms per-user API keys are real — these aren't just config strings. Eliminates the scattered @ConfigProperty injection pattern for user-scoped credentials.
**Relationship to CredentialResolver:** ConnectionPlatform complements, not replaces, `io.casehub.platform.api.credentials.CredentialResolver`. CredentialResolver handles deploy-time endpoint credentials by logical reference name (Tier 1/1.5 in platform auth — static config, per-binding). ConnectionPlatform handles per-user connections with lifecycle (effectively Tier 3 — user-consented external credentials with OAuth2 flows, scope tracking, token refresh). Both remain necessary.
**Trade-offs:** The SPI must accommodate credentials with no lifecycle (API keys), adding a sealed hierarchy where a simpler type would suffice for OAuth2 alone.
**Sources:** Existing codebase: GoogleMapsKeyResolver (API key, per-user), SlackBotClient (bot token), GoogleCredentialResolver (OAuth2); platform-api CredentialResolver (deploy-time endpoint credentials — separate concern)
**Exploration:** quick
**Status:** revised — clarified relationship to platform-api CredentialResolver (from R1-01)

## D2: Incremental consent

**Choice:** Incremental consent — add scopes to existing connections on demand
**Alternatives:**
- Upfront scopes — all scopes at creation time; simpler but worse UX, requires delete+recreate for new scopes
- Separate connections per scope set — no scope merging but multiplies consent prompts
**Rationale:** Google and GitHub both support incremental consent natively. A single Google connection can start with profile-only scopes (social login) and gain Calendar, Drive, Gmail, Contacts scopes as needed. Aligns with platform #530's social-login-to-service-connection bootstrap.
**Trade-offs:** Scope merging adds complexity to the connection model (grantedScopes must be a mutable set across OAuth2 flows). Requires coordination with the OAuth2 engine to request only missing scopes.
**Sources:** Platform epic #525 issue #530 (social login → service connection), Google OAuth2 incremental authorization docs
**Exploration:** quick
**Status:** captured

## D3: SPI location — connectors (connection-spi)

**Choice:** ConnectionPlatform SPI in a new `connection-spi/` module in casehub-connectors
**Alternatives:**
- platform-api — original choice; would follow DestinationResolver precedent but contradicts all existing platform SPI placement
- connectors-api — existing zero-dependency shared module; viable but connection lifecycle types deserve their own module
- New standalone repo — maximum separation but adds cross-repo coordination overhead
**Rationale:** Every platform SPI lives in a `*-spi/` module within connectors: BankPlatform (bank-spi), CalendarPlatform (calendar-spi), ContactsPlatform (contacts-spi), EmailPlatform (email-spi), DocumentPlatform (document-spi), ProjectPlatform (project-spi), LocationPlatform (location-spi), CommercePlatform (commerce-spi), ChatPlatform (chat-spi). DestinationResolver is a delivery-routing SPI (user → destination per channel), not a connector platform SPI — the analogy was wrong. Placing ConnectionPlatform in `connection-spi/` follows the established pattern exactly. If other repos need the SPI, they can depend on `connection-spi/` (zero-dependency pure Java, same as `connectors-api`).
**Trade-offs:** Other repos must add a connectors dependency. This is consistent with how they already depend on `connectors-api` for shared types like `SyncRequest`.
**Sources:** All existing `*-spi/` modules in connectors, DestinationResolver (delivery routing — different concern)
**Exploration:** quick
**Status:** revised — moved from platform-api to connection-spi/ in connectors (from R1-03)

## D4: OAuth2 engine — build in connectors, generalizing TrueLayerConsentService

**Choice:** Build the OAuth2 flow engine in connectors (#149), generalizing from TrueLayerConsentService
**Alternatives:**
- Reuse platform's OAuth2 engine — original choice; platform #528/#530 exist (in casehubio/platform, not casehubio/parent) but are for social login (identity authentication), not service connections (access token management)
- Defer OAuth2 entirely — build only SPI and ref impl; blocks real providers indefinitely
**Rationale:** TrueLayerConsentService in `bank-truelayer` is a complete, working OAuth2 flow engine: auth link generation with CSRF state, authorization code → token exchange, token refresh with per-user concurrency locks, consent status tracking, consent revocation, and ConsentTokenStore SPI with pluggable persistence (JPA + InMemory). This is exactly the infrastructure ConnectionPlatform needs. The generalized engine extracts provider-agnostic flow management from TrueLayer's implementation. Provider-specific adapters (Google, GitHub, Slack, Discord) supply config (endpoints, scopes, client credentials). This is connectors-owned work (#149), not a cross-repo dependency.
**Trade-offs:** TrueLayerConsentService has PSD2-specific semantics (90-day consent expiry, regulatory revocation) that don't generalize directly. The extraction must separate generic OAuth2 flow from TrueLayer-specific lifecycle.
**Sources:** TrueLayerConsentService (bank-truelayer), ConsentTokenStore SPI, connectors epic #147 child #149
**Exploration:** quick
**Status:** revised — build in connectors generalizing from TrueLayerConsentService, not reuse platform's social login engine (from R1-02). Factual correction from R2-01: platform #528/#530 exist in casehubio/platform (not casehubio/parent as originally checked); #530 explicitly depends on connectors' ConnectionPlatform SPI, validating this direction.

## D5: ConnectionStore — pluggable SPI

**Choice:** ConnectionStore as an SPI interface with pluggable backends
**Alternatives:**
- Config-property based — no database needed but refresh tokens can't be updated at runtime
- Encrypted file store — simple but doesn't work in clustered/k8s deployments
**Rationale:** Follows existing platform patterns (NotificationStore, SubscriptionStore). In-memory impl for dev/test (connection-ref), JPA impl for production. Token lifecycle (refresh, expiry, revocation) requires mutable persistent storage.
**Trade-offs:** Requires a persistence backend in production. More infrastructure than config properties.
**Sources:** platform NotificationStore, platform SubscriptionStore patterns
**Exploration:** quick
**Status:** captured

## D6: Migration strategy — immediate replacement

**Choice:** Delete existing CredentialResolver interfaces, refactor consumers to inject ConnectionPlatform directly
**Alternatives:**
- Bridge adapters — ship ConnectionPlatform-backed CredentialResolver implementations, deprecate later. Non-breaking but doubles surface area temporarily.
- Parallel operation — both systems coexist indefinitely. Creates confusion about which is canonical.
**Rationale:** The existing resolvers are duplicated across modules with no shared interface. There's no external consumer to protect — these are internal SPIs within connectors. Clean cut is feasible and avoids maintaining deprecated bridge code.
**Trade-offs:** Big-bang migration across GoogleCalendarPlatform, GoogleContactsPlatform, GoogleEmailPlatform, GoogleDocumentPlatform, GitHubProjectPlatform, and TrueLayerBankPlatform in a single epic.
**Sources:** GoogleCredentialResolver (calendar-google, contacts-google), GitHubCredentialResolver (project-github), GoogleMapsKeyResolver (location-google)
**Exploration:** quick
**Status:** captured

## D7: Connection model — sealed hierarchy

**Choice:** Sealed interface Connection with OAuthConnection and StaticConnection permits
**Alternatives:**
- Single type with credential variants — one Connection record with a sealed Credential field. Simpler storage but less type-safe at the connection level.
- Single flat type — all fields, nulls for non-applicable. Simplest but leaks OAuth concepts into static connections.
**Rationale:** OAuth connections and static connections have fundamentally different fields (tokens/scopes/expiry vs. a single credential string). Sealed hierarchy gives pattern matching, no null fields, and clear type safety. Java 21 sealed types are first-class.
**Trade-offs:** ConnectionStore implementations must handle polymorphic persistence (discriminator column or separate tables).
**Sources:** Java 21 sealed types, existing BankPlatform capability sub-interface pattern
**Exploration:** quick
**Status:** captured

## D8: SPI shape — user-scoped accessors

**Choice:** User-scoped accessor — `forUser(userId)` returns a `UserConnections` intermediate object with connection management methods
**Alternatives:**
- Flat methods with userId as parameter — original choice; every method takes userId explicitly
**Rationale:** The architectural trajectory has moved decisively toward user-scoped accessors. BankPlatform (`accountInformation(userId)`, `paymentInitiation(userId)`), ContactsPlatform (`contactRead(userId)`, `groupRead(userId)`, `contactWrite(userId)`), and ProjectPlatform (`issues(userId)`, `labels(userId)`, etc.) all use this pattern. CalendarPlatform and EmailPlatform (the flat methods SPIs) don't take userId at all — they are stateless query interfaces, not user-scoped. ConnectionPlatform is inherently per-user (connections ARE per-user-per-provider), making it the strongest candidate for user-scoped accessors. Additionally, D1 includes multiple credential types, D2 adds scope management, and D9 adds scope declaration — these are distinct capability concerns that compose naturally under a user-scoped accessor. User-scoped accessors also enable per-provider capability introspection (`supports()`) consistent with BankPlatform, ContactsPlatform, ProjectPlatform, and DocumentPlatform.
**Trade-offs:** Adds one level of indirection. This is consistent with all other user-aware platform SPIs.
**Sources:** BankPlatform (user-scoped), ContactsPlatform (user-scoped), ProjectPlatform (user-scoped) — chose the established modern pattern
**Exploration:** quick
**Status:** revised — changed to user-scoped accessors following architectural trajectory (from R1-04)

## D9: Scope declaration — annotation-based

**Choice:** @RequiresScopes annotation on platform consumers to declare required provider scopes
**Alternatives:**
- Programmatic registration — ScopeRegistry.register() at startup. More flexible but requires init-order awareness.
- Config-driven — scope requirements in application.properties. Decoupled but disconnected from actual consumers.
**Rationale:** Annotations are declarative, discoverable at build time, and can be processed by a CDI extension to build the scope registry automatically. Consumers declare exactly what they need where they need it. Follows Quarkus's annotation-first patterns.
**Trade-offs:** Requires a CDI extension or build-time processor to scan annotations. Less flexible than programmatic registration for dynamic scope requirements.
**Sources:** Quarkus CDI extensions, existing @SimulationEligible annotation pattern
**Exploration:** quick
**Status:** captured

## D10: Missing scopes — exception with recovery metadata

**Choice:** Throw InsufficientScopesException with missing scopes list when connection lacks required scopes
**Alternatives:**
- Return a result object (ConnectionResult with status) — no exception but every call site needs status check
- Auto-trigger consent — automatically initiate incremental consent flow. Only works in interactive contexts.
**Rationale:** Follows the existing UnsupportedCapabilityException pattern — structured error metadata (missing scopes, provider, required vs. granted) enables LLM self-correction and MCP tool error handling. Callers that don't catch get a clear failure; callers that catch can prompt for consent.
**Trade-offs:** Exception-based control flow for a non-exceptional condition (user hasn't consented yet). Acceptable because the operation genuinely cannot proceed without the scopes.
**Sources:** UnsupportedCapabilityException in connectors-api
**Exploration:** quick
**Status:** captured

## D11: Connection cardinality — one per provider per user

**Choice:** One connection per provider per user (keyed by userId + providerId)
**Alternatives:**
- Multiple connections per provider per user — supports multiple Google accounts (personal + work), multiple GitHub accounts (personal + org). Requires a connection label/alias and complicates resolution (which Google connection do I use?).
- Connection groups — logical grouping where multiple accounts for the same provider are linked. Adds complexity for a scenario whose prevalence is unknown.
**Rationale:** Starting with one-connection-per-provider simplifies the data model, storage, resolution, and scope merging. Multi-account is a genuine future scenario (multiple Google accounts, multiple GitHub orgs) but introducing it now adds a disambiguation layer (connection labels, selection UI, default resolution) before the core model is proven. The sealed hierarchy and ConnectionStore design don't preclude adding multi-account later — the primary key extends from (userId, providerId) to (userId, providerId, label).
**Trade-offs:** Users with multiple accounts for the same provider must choose which one to connect. This is a UX constraint, not an architectural one.
**Sources:** Existing platform SPIs all assume single-provider-per-user
**Exploration:** quick — surfaced as implicit decision during review
**Status:** captured

## D12: connection-ref follows established reference implementation pattern

**Choice:** `connection-ref` provides an in-memory reference ConnectionPlatform implementation, following the established `*-ref` pattern
**Alternatives:** None considered — the pattern is established and uncontroversial
**Rationale:** Every platform SPI has a reference implementation: `bank-ref` (RefBankPlatform), `calendar-ref` (RefCalendarPlatform), `contacts-ref` (RefContactsPlatform), `document-ref` (RefDocumentPlatform), `project-ref` (RefProjectPlatform), `location-ref` (RefLocationPlatform), `commerce-ref` (RefCommercePlatform), `chat-ref` (RefChatPlatform). `connection-ref` provides pre-loaded test connections, in-memory ConnectionStore, and a `@DefaultBean` for dev/test — exactly as the other refs do.
**Sources:** All `*-ref` modules in connectors (connectors #155)
**Exploration:** quick — surfaced as implicit decision during review
**Status:** captured

## D13: @SimulationEligible annotation for ConnectionPlatform

**Choice:** ConnectionPlatform annotated with `@SimulationEligible` for simulation framework integration
**Alternatives:** None — all platform SPIs use this annotation; omitting it would be inconsistent
**Rationale:** Every platform SPI uses `@SimulationEligible`: BankPlatform, CalendarPlatform, ContactsPlatform, EmailPlatform, DocumentPlatform, ProjectPlatform. ConnectionPlatform capabilities are `{"connectionManagement", "scopeManagement"}` — reflecting the two core concerns (connection CRUD and scope tracking/validation).
**Sources:** @SimulationEligible on all existing platform SPIs
**Exploration:** quick — surfaced as implicit decision during review
**Status:** captured

## D14: TrueLayer migration path — gradual delegation

**Choice:** TrueLayerBankPlatform gradually delegates to ConnectionPlatform for credential resolution, keeping PSD2-specific consent semantics
**Alternatives:**
- Full replacement — TrueLayerConsentService deleted, all functionality moved to ConnectionPlatform. Loses PSD2-specific semantics (90-day regulatory consent expiry, mandatory revocation).
- No migration — TrueLayer continues with its own ConsentTokenStore indefinitely. Inconsistent with unified model.
**Rationale:** TrueLayer has domain-specific consent semantics that don't map cleanly to the generic ConnectionPlatform model: PSD2 mandates 90-day consent expiry (not OAuth2-standard), regulatory revocation requirements, and scope-per-banking-capability mapping. TrueLayerBankPlatform should delegate token storage and refresh to ConnectionPlatform (via an OAuthConnection) but retain its own consent lifecycle layer for PSD2 compliance. This means TrueLayerConsentService becomes a thin PSD2 adapter over ConnectionPlatform, not a parallel system.
**Sources:** TrueLayerConsentService, ConsentTokenStore, PSD2 SCA/consent requirements
**Exploration:** quick — surfaced as implicit decision during review
**Status:** captured
