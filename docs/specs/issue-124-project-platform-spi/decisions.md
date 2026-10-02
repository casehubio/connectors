## D1: Capability sub-interface partitioning

**Choice:** 5 capability interfaces — Issues, Labels, Milestones, Comments, Boards
**Alternatives:**
- 4 interfaces (Labels merged into Issues) — simpler but `supports(Issues.class)` would imply label CRUD, which may not hold for all providers
- 3 interfaces (Comments also merged into Issues) — fewer interfaces but Issues becomes 12+ methods
**Rationale:** Each capability maps to a distinct GitHub API resource. Independent `supports()` introspection lets providers declare exactly what they implement. Matches the granularity of other SPIs (DocumentPlatform has 4 capability interfaces).
**Trade-offs:** More interfaces to implement per provider, but each is small and focused.
**Sources:** DocumentPlatform (4 capabilities), ContactsPlatform (3 capabilities), GitHub REST API resource structure
**Exploration:** quick
**Status:** captured

## D2: Capability accessor scoping

**Choice:** User-scoped — `capability(String userId)`
**Alternatives:**
- Non-scoped `capability()` — simpler but doesn't model per-user GitHub token access
**Rationale:** GitHub tokens are per-user; different users have different repo permissions. Matches BankPlatform/ContactsPlatform pattern for multi-tenant external APIs.
**Trade-offs:** Credential resolution required (GoogleCredentialResolver-style pattern).
**Sources:** BankPlatform, ContactsPlatform scoping pattern
**Exploration:** quick
**Status:** captured

## D3: Repo context passing

**Choice:** Per-method `owner`/`repo` parameter (likely an `OwnerRepo` record)
**Alternatives:**
- Constructor-bound to single owner/repo — multiple repos need multiple instances
- Context object passed to capability accessor alongside userId — less repetition but non-standard
**Rationale:** One platform instance serves any repo the user has access to. Flexible and stateless.
**Trade-offs:** Every method signature includes the repo parameter, which is repetitive.
**Sources:** GitHub REST API structure (all endpoints are `/repos/{owner}/{repo}/...`)
**Exploration:** quick
**Status:** captured

## D4: Label scope

**Choice:** Full label management — repo-level CRUD plus per-issue assignment
**Alternatives:**
- Assignment only (add/remove labels on issues) — simpler but limits agent autonomy
**Rationale:** Agents managing projects need to create and organize labels, not just assign existing ones.
**Trade-offs:** More API surface to implement and test.
**Sources:** GitHub REST API `/repos/{owner}/{repo}/labels` endpoints
**Exploration:** quick
**Status:** captured

## D5: Boards implementation approach

**Choice:** GraphQL POST via HttpHelper.CLIENT for GitHub Projects v2
**Alternatives:**
- None considered — classic Projects REST was sunset April 2025, GraphQL is the only option
**Rationale:** GraphQL is just HTTP POST with JSON body. No special client library needed — HttpHelper.CLIENT handles it like any other outbound call.
**Trade-offs:** GraphQL queries are more complex to construct than REST calls; need to handle GraphQL error responses differently.
**Sources:** GitHub Projects v2 API documentation, shared-http-client protocol
**Exploration:** quick
**Status:** captured

## D6: Domain model records

**Choice:** Plain Java records in `io.casehub.connectors.project.model` — `Issue`, `Milestone`, `Comment`, `Label`, `Project`, `ProjectColumn`, `OwnerRepo`
**Alternatives:**
- Builder pattern (ChatPlatform style) — rejected; newer SPIs all use plain records
**Rationale:** Matches calendar, contacts, document model patterns. Plain constructors, `requireNonNull` on required fields, `List.copyOf` for defensive copies. `OwnerRepo(owner, repo)` lives in project-spi since repo-scoping is unique to this domain.
**Trade-offs:** None significant — this is the established convention.
**Sources:** Contact, CalendarEvent, DocumentSummary record patterns
**Exploration:** quick
**Status:** captured

## D7: GraphQL/MCP API surface

**Choice:** `ConnectorProjectApi` class with `@McpDomain("connectors/project")`, following ConnectorContactsApi pattern — `@PlatformQuery`/`@PlatformMutation` methods with `@RestPath`, owner/repo as `@QueryParam`s alongside platformId
**Alternatives:**
- Separate MCP tool classes per capability — diverges from established pattern
**Rationale:** Matches every other domain's API class. Owner/repo as query params keeps the RESTful pattern. `requireCapability` helper checks `supports()` before dispatch.
**Trade-offs:** owner/repo params on every method make signatures longer than other APIs.
**Sources:** ConnectorContactsApi, ConnectorCalendarApi, ConnectorDocumentApi patterns
**Exploration:** quick
**Status:** captured

## D8: Credential resolution

**Choice:** GitHubCredentialResolver CDI SPI — `resolveToken(String userId)` returns a GitHub token per user
**Alternatives:**
- Config-based single token (`casehub.connectors.github.token`) — simpler but doesn't support multi-user
**Rationale:** Follows GoogleCredentialResolver pattern from calendar-google/contacts-google. Per credential-config-ownership protocol, tokens are resolved by callers, not stored in shared clients.
**Trade-offs:** Consumers must provide a resolver implementation.
**Sources:** GoogleCredentialResolver in calendar-google, credential-config-ownership protocol
**Exploration:** quick
**Status:** captured
