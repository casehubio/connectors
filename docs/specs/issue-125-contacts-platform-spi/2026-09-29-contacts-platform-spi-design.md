# ContactsPlatform SPI — Design Spec

**Issue:** casehubio/connectors#125
**Date:** 2026-09-29
**Branch:** issue-125-contacts-platform-spi

## Overview

Thin read-layer SPI for syncing structured identity and contact information from external providers into the platform. The system of record for relationship/activity data is neocortex — this SPI feeds it with directory-oriented contact data.

Primary use case: background sync of contacts from providers like Google People API, with incremental sync support via sync tokens.

## Modules

| Module | Purpose |
|--------|---------|
| `contacts-spi` | ContactsPlatform SPI + ContactsPlatformService + `@SimulationEligible` |
| `contacts-ref` | In-memory reference implementation with pre-loaded test data |
| `contacts-google` | Google People API provider (OAuth2, incremental sync) |

GraphQL/MCP additions in the existing `graphql` module: `ConnectorContactsApi` with `@McpDomain("connectors/contacts")`.

## SPI Design

### ContactsPlatform interface

Follows the capability sub-interface pattern (DocumentPlatform style) with user-scoped accessors (BankPlatform style).

```java
@SimulationEligible(name = "contacts-platform",
    capabilities = {"contactRead", "groupRead", "contactWrite"})
public interface ContactsPlatform {
    String id();
    boolean supports(Class<?> capability);
    ContactRead contactRead(String userId);
    GroupRead groupRead(String userId);
    ContactWrite contactWrite(String userId);
}
```

User-scoped because contacts are inherently per-user (each user has their own Google address book, their own HubSpot view). One platform instance resolves credentials per call via userId — scales to multi-tenant sync without N CDI instances.

### Capability sub-interfaces

Nested interfaces within ContactsPlatform:

```java
interface ContactRead {
    Page<Contact> list(PageRequest pagination);
    SyncResult<Contact> listSync(SyncRequest request);
    Contact get(String contactId);
    Page<Contact> search(String query, PageRequest pagination);
}

interface GroupRead {
    List<Group> list();
    Page<Contact> listContacts(String groupId, PageRequest pagination);
}

interface ContactWrite {
    Contact create(Contact contact);
    Contact update(String contactId, Contact contact);
    void delete(String contactId);
}
```

- `ContactRead.list()` — full paginated listing returning `Page<Contact>` with cursor/hasMore (no sync token)
- `ContactRead.listSync()` — incremental sync with sync token; returns changes since last sync including deletions
- `ContactRead.search()` — provider-side search by query string, returns `Page<Contact>`
- `GroupRead.list()` — all groups for this user (not paginated — group counts are small)
- `GroupRead.listContacts()` — contacts within a specific group, returns `Page<Contact>`
- `ContactWrite` — optional capability; providers declare via `supports(ContactWrite.class)`

### Sync types (in connectors-api)

```java
public record SyncRequest(String syncToken, int pageSize) {
    public static SyncRequest initial(int pageSize) {
        return new SyncRequest(null, pageSize);
    }
}

public record SyncResult<T>(
    List<T> items,
    List<String> deletedIds,
    String syncToken,
    boolean hasMore
) {}
```

`SyncResult` carries `deletedIds` (resources removed since last sync) and `syncToken` (opaque token for next incremental sync). When `syncToken` is null in the request, a full sync is performed.

**Token invalidation contract:** Providers MUST throw `SyncTokenExpiredException` when a sync token is invalid or expired (e.g., Google People API returns HTTP 410 Gone for tokens older than ~7 days). Callers MUST catch this and fall back to `SyncRequest.initial(pageSize)` for a full resync.

```java
public class SyncTokenExpiredException extends RuntimeException {
    private final String expiredToken;

    public SyncTokenExpiredException(String expiredToken) {
        super("Sync token expired — full resync required");
        this.expiredToken = expiredToken;
    }

    public String expiredToken() { return expiredToken; }
}
```

This exception lives in `connectors-api` alongside `SyncRequest`/`SyncResult` so all SPI implementations share the same invalidation signal.

These types live in `connectors-api` alongside `Page`/`PageRequest` as cross-SPI sync primitives. CalendarPlatform's sync retrofit (follow-up #1) will need them — placing them in connectors-api from the start avoids a move-and-republish cycle and prevents peer SPI cross-dependencies.

`SyncResult` is deliberately a separate type from `Page`, despite structural similarity. `Page.nextCursor` is ephemeral (used within a single browsing session); `SyncResult.syncToken` is long-lived (persisted and reused across API calls days or weeks apart). `SyncResult` also carries `deletedIds` — a concept that has no analogue in pagination. Conflating them via shared inheritance would obscure these semantic differences.

## Contact model

Provider-shaped, directory-oriented records. All records are Java records in `io.casehub.connectors.contacts.model`, following the convention used by all existing SPIs (`bank.model`, `calendar.model`, `email.model`, `document.model`). SPI interfaces remain in `io.casehub.connectors.contacts.spi`.

```java
public record Contact(
    String id,
    ContactName name,
    List<LabelledValue<String>> emails,
    List<LabelledValue<String>> phones,
    List<LabelledValue<Address>> addresses,
    String company,
    String jobTitle,
    String photoUrl,
    String notes,
    Map<String, String> metadata
) {}

public record ContactName(
    String displayName,
    String givenName,
    String familyName
) {}

public record Address(
    String street,
    String city,
    String region,
    String postalCode,
    String country
) {}

public record LabelledValue<T>(
    String label,
    T value,
    boolean primary
) {}

public record Group(
    String id,
    String name,
    GroupType groupType,
    int memberCount
) {}

public enum GroupType {
    SYSTEM,
    USER_CREATED
}
```

**`LabelledValue<T>`** captures the universal multi-valued-with-labels pattern across contact providers. `label` is provider-specific (e.g., "home", "work", "mobile"). `primary` marks the preferred value.

**`metadata`** on Contact is a simple extension point for provider-specific fields that don't map to the standard directory model. Weakly typed but bounded — not the primary data model.

**`GroupType`** distinguishes system groups (Google's "myContacts", "starred") from user-created labels/tags.

## CDI wiring

Standard platform pattern:

```java
// ContactsPlatformService — registry
public class ContactsPlatformService {
    private final Map<String, ContactsPlatform> platforms;
    // platform(id), supports(id), ids()
}

// ContactsBeans — CDI producer
public class ContactsBeans {
    @Produces @ApplicationScoped
    ContactsPlatformService service(@All List<ContactsPlatform> platforms) { ... }
}

// NoOpContactsPlatform — @DefaultBean fallback
@DefaultBean @ApplicationScoped
public class NoOpContactsPlatform implements ContactsPlatform {
    // id() = "none", supports() = false
    // Capability accessors return singleton no-op instances
    // that throw UnsupportedCapabilityException
}
```

## contacts-ref — Reference implementation

In-memory implementation with pre-loaded test data for dev/test:

- `InMemoryContactsBackend` — thread-safe backing store
- Pre-loaded data: ~10 contacts across 3 groups (personal, work, starred)
- All capabilities supported including ContactWrite
- Sync support: tracks changes with monotonic version tokens
- Inner record classes implement each capability sub-interface
- userId parameter accepted but not used for credential resolution (all users see same data)

## contacts-google — Google People API provider

Google People API v1 implementation:

- **Client library:** Uses `google-api-services-people` (`com.google.api.services.people.v1.PeopleService`), following the pattern established by `GoogleCalendarPlatform` (`google-api-services-calendar`) and `GoogleEmailPlatform` (`google-api-services-gmail`). Client constructed with `GoogleNetHttpTransport.newTrustedTransport()`, `GsonFactory.getDefaultInstance()`, and `HttpCredentialsAdapter`.
- **Auth:** OAuth2 refresh tokens, per-user credential resolution at call time. Unlike calendar-google/email-google (CDI singleton with baked-in credentials), contacts-google resolves credentials per userId via `GoogleCredentialResolver`, building `UserCredentials` per call. Needs config-based default for dev (`casehub.contacts.google.credentials.<userId>.refresh-token`), pluggable for production.
- **ContactRead:** Maps Google `Person` resource to `Contact` record. Paginated via `pageToken`/`nextPageToken` on `people.connections.list`. Search via `people.searchContacts`.
- **Sync:** Uses Google's `syncToken` on `people.connections.list` for incremental sync. `requestSyncToken=true` on initial call, pass token on subsequent calls.
- **GroupRead:** Maps Google `ContactGroup` to `Group`. `groupType` from Google's `GROUP_TYPE` field.
- **ContactWrite:** `supports(ContactWrite.class) = true` — Google People API supports create/update/delete.
- **Pagination:** Partial results returned with WARNING per protocol PP-20260610-83747b.
- **Blocking:** All operations are blocking HTTP calls.

### Credential resolution

CDI SPI — injectable and replaceable by production deployments:

```java
public interface GoogleCredentialResolver {
    GoogleOAuthConfig resolve(String userId);
}

public record GoogleOAuthConfig(
    String refreshToken,
    String clientId,
    String clientSecret
) {}
```

`@DefaultBean` implementation reads from config properties:
```
casehub.contacts.google.credentials.{userId}.refresh-token=...
casehub.contacts.google.credentials.{userId}.client-id=...
casehub.contacts.google.credentials.{userId}.client-secret=...
```

Production deployments inject their own `GoogleCredentialResolver` backed by a credential store. The config-based default works for dev/test.

This is a new pattern in the connectors repo — calendar-google and email-google don't need it because they aren't user-scoped. If/when those SPIs adopt user-scoping, the resolver pattern can be extracted.

### CDI wiring

Unlike `CalendarGoogleBeans` and `EmailGoogleBeans` which inject `@ConfigProperty` credentials and bake them into the platform constructor (single-user singleton), contacts-google uses a structurally different pattern:

```java
@ApplicationScoped
public class ContactsGoogleBeans {
    @Produces @ApplicationScoped
    public GoogleContactsPlatform googleContactsPlatform(GoogleCredentialResolver resolver) {
        return new GoogleContactsPlatform(resolver);
    }
}
```

- The platform is a singleton `@ApplicationScoped` bean (one instance shared across all users)
- `GoogleCredentialResolver` is injected into the platform, not credentials themselves
- Per-call credential lookup happens inside capability methods via `resolver.resolve(userId)`, not at construction time
- No `@ConfigProperty` on the platform bean — credentials are the resolver's concern

## GraphQL/MCP integration

`ConnectorContactsApi` in the `graphql` module:

```java
@McpDomain(value = "connectors/contacts", app = "connectors",
    basePath = "/api/connectors/contacts",
    summary = "Contacts connector — contacts, groups, sync")
@ApplicationScoped
public class ConnectorContactsApi {
    @Inject ContactsPlatformService platformService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("List contacts from a provider")
    @RestPath("/contacts")
    public Page<Contact> listContacts(
            @QueryParam("platform") String platformId,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) { ... }

    @PlatformQuery("Incremental sync of contacts")
    @RestPath("/contacts/sync")
    public SyncResult<Contact> syncContacts(
            @QueryParam("platform") String platformId,
            @QueryParam("syncToken") String syncToken,
            @QueryParam("pageSize") Integer pageSize) { ... }

    @PlatformQuery("Get a specific contact by ID")
    @RestPath("/contacts/{contactId}")
    public Contact getContact(
            @QueryParam("platform") String platformId,
            @PathParam String contactId) { ... }

    @PlatformQuery("Search contacts by query")
    @RestPath("/contacts/search")
    public Page<Contact> searchContacts(
            @QueryParam("platform") String platformId,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) { ... }

    @PlatformQuery("List contact groups")
    @RestPath("/groups")
    public List<Group> listGroups(
            @QueryParam("platform") String platformId) { ... }

    @PlatformMutation("Create a new contact")
    @RestPath("/contacts")
    public Contact createContact(
            @QueryParam("platform") String platformId,
            Contact contact) { ... }

    @PlatformMutation("Update an existing contact")
    @RestPath("/contacts/{contactId}")
    public Contact updateContact(
            @QueryParam("platform") String platformId,
            @PathParam String contactId,
            Contact contact) { ... }

    @PlatformMutation("Delete a contact")
    @RestPath("/contacts/{contactId}/delete")
    public void deleteContact(
            @QueryParam("platform") String platformId,
            @PathParam String contactId) { ... }
}
```

`userId` is resolved from `SecurityIdentity` (the authenticated principal), never accepted as a caller-supplied parameter — following the pattern in `ConnectorBankApi`. The SPI's user-scoped accessors (`contactRead(userId)`, etc.) receive this internally-derived value.

No `@Blocking` annotation — `@PlatformQuery`/`@PlatformMutation` dispatch does not run on the Vert.x event loop. No existing API class in the graphql module uses `@Blocking`.

Capability-gated: methods call `requireCapability()` and throw `UnsupportedCapabilityException` with structured metadata when a provider doesn't support the requested operation.

### ConnectorOperationsImpl integration

`ConnectorOperationsImpl.connectorsReport()` must be updated to include contacts:

- Add `ContactsPlatformService` to the constructor injection
- Add `"contacts"` to `ALL_SCOPES`
- Add a contacts block that enumerates `ContactRead`, `GroupRead`, `ContactWrite` capabilities

### Generated MCP tools

`McpDomainJandexScanner` and `GraphQLModelScanner` auto-generate MCP tool definitions from `@McpDomain` + `@PlatformQuery`/`@PlatformMutation` annotations. No separate `@Tool` classes are needed — the `ConnectorContactsApi` above IS the MCP surface.

Generated tool names (derived from method names by the scanner):

| Generated tool | Source method |
|---------------|--------------|
| `list_contacts` | `listContacts()` |
| `sync_contacts` | `syncContacts()` |
| `get_contact` | `getContact()` |
| `search_contacts` | `searchContacts()` |
| `list_groups` | `listGroups()` |
| `create_contact` | `createContact()` |
| `update_contact` | `updateContact()` |
| `delete_contact` | `deleteContact()` |

## Protocols applied

| Protocol | Application |
|----------|-------------|
| PP-20260609-e3a2bd | `ContactsPlatform.id()` naming |
| PP-20260610-83747b | Partial results + WARNING on pagination failures |

## Out of scope

- Sync orchestration (scheduling, frequency, policies) — platform concern
- Neocortex mapping layer — neocortex's responsibility
- CardDAV, HubSpot, Apple Contacts, Microsoft People API providers — future issues
- Calendar sync retrofit — separate follow-up issue

## Follow-up issues to file

1. Retrofit CalendarPlatform with sync support AND user-scoping — CalendarPlatform currently has no `userId` parameter and `GoogleCalendarPlatform` bakes credentials into the constructor (single-user). Both sync tokens and user-scoped accessors are needed; doing them together avoids two breaking changes. EmailPlatform has the same single-user limitation and should be evaluated in the same pass.
2. CardDAV ContactsPlatform provider
3. HubSpot ContactsPlatform provider

## References

- `document-spi/` — nested capability sub-interface pattern
- `bank-spi/` — user-scoped capability accessor pattern
- `calendar-google/` — Google OAuth2 refresh token auth + client library pattern
- `email-google/` — Google API client library pattern
- `google-api-services-people` — Google People API v1 client library
- Google People API v1 — contact/group resources, sync tokens
- vCard (RFC 6350) — contact field set standard
- Protocol PP-20260609-e3a2bd — SPI id() naming
- Protocol PP-20260610-83747b — pagination partial results
- casehubio/connectors#125 — issue
