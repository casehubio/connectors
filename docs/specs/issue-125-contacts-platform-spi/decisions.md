# Decisions — ContactsPlatform SPI (#125)

## D1: SPI variant — capability sub-interfaces (DocumentPlatform style)

**Choice:** Capability sub-interface pattern following DocumentPlatform style — nested capability interfaces, `supports(Class<?>)`, but with user-scoped accessors (per D2). Three sub-interfaces: ContactRead, GroupRead, ContactWrite.
**Alternatives:**
- Flat interface (like EmailPlatform/CalendarPlatform) — simpler but doesn't express optional capabilities (ContactWrite)
- ChatPlatform-style compile-time defaults — overkill for 3 capabilities
- BankPlatform style (top-level interface files, factory-per-call) — heavier, intended for regulated domains
**Rationale:** Three distinct capabilities with different optionality: ContactRead (required), GroupRead (required), ContactWrite (optional). `supports(Class<?>)` lets providers declare what they offer. Nested interfaces keep the capability surface co-located.
**Trade-offs:** Slightly more boilerplate than flat interface. Runtime `supports()` check rather than compile-time safety (ChatPlatform pattern), but 3 capabilities don't warrant the builder machinery.
**Sources:** document-spi nested capability pattern; bank-spi user-scoped pattern; chat-spi ChatPlatform defaults pattern (rejected for this SPI); issue #125 capability table
**Exploration:** quick
**Status:** revised (R1-05 — clarified which capability pattern; acknowledged ChatPlatform alternative)

## D2: User-scoped capability accessors

**Choice:** User-scoped — `contactRead(String userId)`, `groupRead(String userId)`, `contactWrite(String userId)`
**Alternatives:**
- Provider-scoped (no userId) — simpler API but doesn't match OAuth reality where each user has their own contacts
- Hybrid (optional userId) — ambiguous contract
**Rationale:** Contact providers like Google People API are per-user OAuth. Each user has their own address book. Same pattern as BankPlatform. Reviewed: Calendar/Email/Document SPIs are NOT user-scoped — they use CDI-per-user-instance instead. That pattern doesn't scale to multi-tenant sync with many users. BankPlatform's user-scoped approach (one instance, credential lookup per call) is architecturally better for contacts sync at scale.
**Trade-offs:** Every call requires userId; org-scoped providers (HubSpot CRM) will need to decide how to handle userId (ignore it, or use it for audit). Creates inconsistency with Calendar/Email/Document SPIs. Requires per-user credential resolution mechanism in Google provider (see D6).
**Sources:** BankPlatform user-scoped pattern; Calendar/Email/Document CDI-per-user patterns (rejected); Google People API docs
**Exploration:** quick
**Status:** revised (R1-03 — reviewed sibling SPI inconsistency, kept user-scoped for multi-tenant scalability)

## D3: Contact model — provider-shaped with rich types

**Choice:** Provider-shaped Contact record with typed directory fields. `LabelledValue<T>(label, value, primary)` for multi-valued fields (emails, phones, addresses). ~6-8 model records.
**Alternatives:**
- Flat record with multi-valued strings — loses label/primary metadata
- Map-heavy extensible — weakly typed, hard to use
**Rationale:** Directory-oriented data. Mirrors what providers expose (name, emails, phones, addresses, company, jobTitle, photoUrl, notes). Provider-agnostic. Neocortex handles relationship graph separately.
**Trade-offs:** More model classes than flat approach, but compile-time safety pays for itself across multiple providers
**Sources:** vCard (RFC 6350) field set; Google People API Person resource
**Exploration:** quick
**Status:** captured

## D4: Sync mechanism — SyncResult/SyncRequest in contacts-spi

**Choice:** `SyncResult<T>(items, deletedIds, syncToken, hasMore)` and `SyncRequest(syncToken, pageSize)` in contacts-spi. Sync-aware list methods on ContactRead and GroupRead. Extract to connectors-api when a second consumer (calendar) actually needs them.
**Alternatives:**
- Shared types in connectors-api now — speculative; calendar has no sync today and may never need the same model
- Full sync infrastructure (SyncState, SyncPolicy) — orchestration concerns, premature for the SPI layer
- Mechanism here, orchestration in platform — the split is correct, but premature to formalize orchestration types
**Rationale:** The SPI needs sync tokens for incremental sync (Google People API supports them natively). SyncResult extends the Page concept with `deletedIds` and `syncToken`. SyncState/SyncPolicy are orchestration — they belong wherever sync scheduling lives, not in the SPI.
**Trade-offs:** If calendar needs sync later, extraction will require moving types. But extract-when-needed is cheaper than designing shared infrastructure for one consumer.
**Sources:** Google People API syncToken; Google Calendar API syncToken (unused today)
**Exploration:** quick
**Status:** revised (R1-04 — trimmed to mechanism only, dropped speculative orchestration types)

## D5: ContactWrite included from the start

**Choice:** Include ContactWrite capability sub-interface in the SPI now (create, update, delete contacts)
**Alternatives:**
- Defer to future issue — smaller scope but requires SPI change later
**Rationale:** Low cost since it's just the SPI contract. contacts-ref implements it, contacts-google can declare `supports(ContactWrite.class)=false` initially if needed.
**Trade-offs:** None significant — optional capability means no provider is forced to implement it
**Sources:** Issue #125 capability table
**Exploration:** quick
**Status:** captured

## D6: Google provider — OAuth2 with per-user credential resolution

**Choice:** OAuth2 refresh tokens via per-user credential resolution at call time. Uses Google People API v1. Unlike calendar-google/email-google (which bake credentials at CDI construction time as singletons), contacts-google needs runtime credential lookup because of D2's user-scoped design.
**Alternatives:**
- CDI-per-user-instance pattern (calendar-google style) — doesn't scale to many users; contradicts D2's user-scoped design
**Rationale:** D2 requires per-user credential resolution at call time. contacts-google will need a credential resolver mechanism — either a simple config-based resolver or a store. Protocol PP-20260609-0c3e24 (credentials at call time) applies to shared clients, not platform implementations, but the user-scoped design naturally leads to the same pattern.
**Trade-offs:** More complex than calendar-google's singleton pattern. Needs credential resolution infrastructure that doesn't exist for Google OAuth yet (TrueLayer has ConsentService for PSD2 but that's bank-specific).
**Sources:** calendar-google, email-google CDI producer patterns; TrueLayerConsentService credential resolution; protocol PP-20260609-0c3e24
**Exploration:** quick
**Status:** revised (R1-06, R1-13 — acknowledged tension between user-scoped and singleton patterns, clarified credential resolution need)

## D7: Group model — flat

**Choice:** Flat groups with type metadata: `Group(id, name, groupType, memberCount)`. `groupType` distinguishes system groups (myContacts, starred) from user-created labels. List groups, list contacts in group.
**Alternatives:**
- Nested groups with parentId — most contact providers don't support nesting (unlike document folders)
- Minimal (no groupType) — loses distinction between system and user-created groups
**Rationale:** Google contact groups are flat. Most contact providers don't nest groups. `groupType` is essential — system groups (myContacts, starred) have special semantics and callers need to distinguish them from user labels.
**Trade-offs:** If a future provider has nested groups, they'd need to flatten
**Sources:** Google People API contactGroups resource (groupType: SYSTEM_CONTACT_GROUP vs USER_CONTACT_GROUP)
**Exploration:** quick
**Status:** revised (R1-09 — added groupType metadata)
