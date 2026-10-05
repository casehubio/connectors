# Simulation Integration Audit

**Issue:** casehubio/connectors#139
**Date:** 2026-10-05
**Depends on:** casehubio/connectors#138 (design: unify ref with simulation)

## Deliverable 1 — Per-SPI Assessment

Each capability is classified as **seed-and-go** (ref logic is authoritative — deterministic CRUD, lifecycle, enumeration) or **seed-plus-simulate** (ref provides a structurally valid fallback but the operation fundamentally requires interpretation — search relevance, fuzzy matching, routing, recommendations).

### ChatPlatform — fully seed-and-go

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| Messaging | Seed-and-go | Store/retrieve by channel + since-filter | — | messages-corpus.yaml |
| Discovery | Seed-and-go | Map lookup by name/id | — | channels-corpus.yaml |
| Members | Seed-and-go | List CRUD (CopyOnWriteArrayList) | — | members-corpus.yaml |
| Reactions | Seed-and-go | List CRUD | — | — |
| Presence | Seed-and-go | Map put/get | — | — |
| ChannelManagement | Seed-and-go | Map CRUD | — | — |
| MemberManagement | Seed-and-go | Map CRUD (same as Members) | — | — |
| MessageHistory | Seed-and-go | Timestamp filter on stored messages | — | — |
| Commands | Seed-and-go | CommandHandler SPI dispatch | — | — |

No seed YAML — chat-ref starts empty; all state is runtime-created. Corpus files exist for simulation variety but cover deterministic operations only.

### CalendarPlatform — fully seed-and-go

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| listCalendars | Seed-and-go | Returns hardcoded "Primary" calendar | — | calendars-corpus.yaml |
| listEvents | Seed-and-go | Filter by calendarId + time overlap | — | events-corpus.yaml |
| getEvent | Seed-and-go | Lookup by calendarId + eventId | — | events-corpus.yaml |
| createEvent | Seed-and-go | UUID + store | — | — |
| updateEvent | Seed-and-go | Find + replace in list | — | — |
| deleteEvent | Seed-and-go | Remove + version tracking | — | — |
| sync | Seed-and-go | Version-based filtering | — | — |

No seed YAML — starts with just a hardcoded primary calendar; events are runtime-created.

### BankPlatform — fully seed-and-go

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| listAccounts | Seed-and-go | Returns pre-loaded list | accounts.yaml | accounts-corpus.yaml |
| balance | Seed-and-go | Map lookup by accountId | balances.yaml | accounts-corpus.yaml |
| listTransactions | Seed-and-go | Filter by account + date range + paginate | transactions.yaml | transactions-corpus.yaml |
| getTransaction | Seed-and-go | Filter by accountId + transactionId | transactions.yaml | transactions-corpus.yaml |
| initiatePayment | Seed-and-go | Creates payment, idempotency check, state machine | — | payments-corpus.yaml |
| paymentStatus | Seed-and-go | Returns status + deterministic state progression | — | payments-corpus.yaml |

All deterministic. Corpus files provide alternative datasets for simulation variety, not because operations need interpretation.

### CommercePlatform — mixed

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| ProductSearch.searchByText | **Seed-plus-simulate** | Case-insensitive substring on name, brand, category | products.yaml | search-corpus.yaml |
| ProductSearch.searchByCategory | **Seed-plus-simulate** | Case-insensitive `contains()` on category | products.yaml | search-corpus.yaml |
| ProductSearch.searchByBrand | **Seed-plus-simulate** | `equalsIgnoreCase()` on brand (exact only) | products.yaml | search-corpus.yaml |
| ProductDetails.detail | **Seed-plus-simulate** | Direct map lookup | products.yaml | details-corpus.yaml |
| ProductDetails.reviews | **Seed-plus-simulate** | Direct map lookup | products.yaml | details-corpus.yaml |
| Cart | Seed-and-go | Stateful add/remove/clear/view | products.yaml | — |
| Checkout | Seed-and-go | Lifecycle transition | products.yaml | — |
| OrderTracking | Seed-and-go | Map lookups | products.yaml | — |

ProductSearch is clearly interpretive — substring match has no relevance ranking or fuzzy matching. ProductDetails ref logic is technically a direct lookup (functionally deterministic), but a real provider adds recommendations, availability scoring, and dynamic pricing — the simulation layer provides this richer response.

### ContactsPlatform — mixed

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| ContactRead.search | **Seed-plus-simulate** | Case-insensitive substring on displayName, company, email | contacts.yaml | search-corpus.yaml |
| ContactRead (list/get/sync) | Seed-and-go | CRUD + monotonic versioning | contacts.yaml | — |
| GroupRead | Seed-and-go | Map lookups | groups.yaml | — |
| ContactWrite | Seed-and-go | CRUD + versioning | contacts.yaml | — |

Only search is interpretive — no fuzzy matching, no phonetic similarity, no ranking.

### DocumentPlatform — mixed

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| SearchOperations.search | **Seed-plus-simulate** | Case-insensitive substring on **filename only** — no content search | files.yaml | search-corpus.yaml |
| FileOperations | Seed-and-go | CRUD + versioning | files.yaml, folders.yaml | — |
| FolderOperations | Seed-and-go | List/create/move | folders.yaml | — |
| SharingOperations | Seed-and-go | Returns pre-set share URL | files.yaml | — |

The most notably naive fallback — real full-text document search requires content analysis, not filename substring matching.

### EmailPlatform — mixed (with gap)

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| listMailboxes | Seed-and-go | Returns pre-built list | messages.yaml | mailbox-corpus.yaml |
| listMessages | Seed-and-go | Filter by mailbox + date range + paginate | messages.yaml | mailbox-corpus.yaml |
| getMessage | Seed-and-go | Lookup by mailboxId + messageId | messages.yaml | messages-corpus.yaml |
| getAttachmentContent | Seed-and-go | Lookup by composite key | messages.yaml | messages-corpus.yaml |
| **search** | **Seed-plus-simulate** | **Method does not exist** | — | search-corpus.yaml (orphaned) |

**Gap:** The #138 design spec classifies email search as interpretive, and a `search-corpus.yaml` was pre-authored, but `EmailPlatform` has no search method. The corpus file references `email-platform.search` which doesn't map to any interface method. This is a future capability, not a current gap — the corpus file is pre-positioned for when the method is added.

### LocationPlatform — fully seed-plus-simulate

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| PlaceSearch.searchByText | **Seed-plus-simulate** | Substring on name, address, types | places.yaml | search-corpus.yaml |
| PlaceSearch.searchNearby | **Seed-plus-simulate** | Haversine distance filter (no relevance ranking) | places.yaml | search-corpus.yaml |
| PlaceSearch.searchByCategory | **Seed-plus-simulate** | Substring on types + distance filter | places.yaml | search-corpus.yaml |
| PlaceDetails.detail | **Seed-plus-simulate** | Direct map lookup | places.yaml | details-corpus.yaml |
| Geocoding.geocode | **Seed-plus-simulate** | Substring match on address | geocoding.yaml | geocoding-corpus.yaml |
| Geocoding.reverseGeocode | **Seed-plus-simulate** | Sorts by distance, returns nearest 3 | geocoding.yaml | geocoding-corpus.yaml |
| Directions.route | **Seed-plus-simulate** | Straight-line distance / constant speed per mode | places.yaml | directions-corpus.yaml |

The most simulation-heavy SPI — every operation requires interpretation. The ref fallbacks are particularly crude: directions uses straight-line distance (no actual routing, no waypoints, no traffic), geocoding does substring address matching (no address parsing), and search has no relevance ranking.

### ProjectPlatform — mixed

| Capability | Assessment | Ref Logic | Seed YAML | Corpus |
|---|---|---|---|---|
| Issues.search | **Seed-plus-simulate** | Case-insensitive substring on title and body | project-data.yaml | search-corpus.yaml |
| Issues (CRUD) | Seed-and-go | Create/get/list/update/close/reopen/labels | project-data.yaml | — |
| Labels | Seed-and-go | CRUD | project-data.yaml | — |
| Milestones | Seed-and-go | CRUD | project-data.yaml | — |
| Comments | Seed-and-go | Create/list/get | project-data.yaml | — |
| Boards | Seed-and-go | Pre-loaded project board data | project-data.yaml | — |

Only issue search is interpretive — the rest is pure state management.

## Deliverable 2 — Priority Ordering

Ranked by simulation integration value — how much the SPI's usefulness improves when interpretive operations get realistic responses.

| Priority | SPI | Interpretive Ratio | Impact |
|---|---|---|---|
| 1 | **LocationPlatform** | 7/7 (100%) | Every operation is interpretive. Without simulation, directions gives straight-line distance, geocoding does substring matching, search has no relevance. Essentially unusable for demos or agent evaluation. |
| 2 | **CommercePlatform** | 5/8 (63%) | Product search and details are core to the commerce experience. Substring search can't handle "noise-cancelling headphones under £300". Without simulation, product recommendations and availability are absent. |
| 3 | **DocumentPlatform** | 1/4 (25%) | Only search, but document search is filename-only — the most obviously naive fallback. Real document search requires content analysis. High impact for a single capability. |
| 4 | **ContactsPlatform** | 1/4 (25%) | Contact search is important for agent workflows (finding people by partial name/company). Substring matching misses phonetic similarity and fuzzy matching. |
| 5 | **ProjectPlatform** | 1/6 (17%) | Issue search matters for agent-driven project management, but the rest of the SPI is CRUD-complete. Lower ratio, moderate impact. |
| 6 | **EmailPlatform** | 0/4 (0% current) | No search method exists yet. When added, it will be high-impact (finding emails by content/sender/subject). Currently all operations are deterministic and complete. |
| 7 | **ChatPlatform** | 0/9 (0%) | Fully deterministic. Simulation adds dataset variety but not capability. |
| 8 | **CalendarPlatform** | 0/7 (0%) | Fully deterministic. CRUD + sync is complete and authoritative. |
| 9 | **BankPlatform** | 0/6 (0%) | Fully deterministic. Payment lifecycle and account queries are state machines — interpretation would be wrong. |

## Deliverable 3 — Common Patterns

Three extractable patterns emerged from the audit:

### Pattern 1 — Search Simulation

**Appears in:** CommercePlatform, ContactsPlatform, DocumentPlatform, LocationPlatform, ProjectPlatform (and future EmailPlatform)

Every ref search implementation follows the same structure:
1. Iterate over seed data
2. Case-insensitive substring match on 1-3 string fields
3. Return matches (sometimes paginated)

The simulation layer replaces this with corpus-driven responses that provide relevance ranking, fuzzy matching, and realistic result ordering. This is the single most impactful pattern — 5 of 6 mixed SPIs have search as their primary (often only) interpretive operation.

A shared `SubstringSearchFallback` could standardise the ref fallback logic across all 5 implementations, but the real value is in the simulation corpus — the ref fallback just needs to be structurally valid.

### Pattern 2 — Details Enrichment

**Appears in:** CommercePlatform (ProductDetails), LocationPlatform (PlaceDetails)

The ref returns a direct map lookup — structurally correct but static. A real provider adds dynamic data: recommendations, availability, pricing, opening hours, review freshness. The simulation layer bridges this gap with corpus entries that include richer responses.

This pattern is less extractable than search because the enrichment is domain-specific (product recommendations vs place opening hours), but the *interception point* is the same: intercept a by-ID lookup and return a richer response.

### Pattern 3 — Deterministic CRUD Ref

**Appears in:** All 9 SPIs (the seed-and-go operations)

Standardised across Phase 1 normalisation (#141):
- `InMemory*Backend` with `@DefaultBean @ApplicationScoped`
- YAML seed loading via `*SeedLoader`
- Monotonic versioning for sync-capable SPIs
- `Set.of()` for `supports()` declarations

This pattern is fully extracted and normalised — no further work needed.

## Deliverable 4 — Demo Impact Assessment

Ranked by how hard each SPI is to demo convincingly without a real provider connection, and how much simulation closes the gap.

### Hard to demo without real providers

| SPI | Demo Difficulty | Why | Simulation Closes Gap? |
|---|---|---|---|
| **LocationPlatform** | Very hard | Directions showing "5.2 km straight-line, 6 min driving" is visibly wrong. Geocoding returning substring-matched addresses is unconvincing. No map data, no routing, no place photos. | **Yes — fully.** Corpus entries provide realistic routes, accurate geocoding, and rich place details. |
| **CommercePlatform** | Hard | Searching "wireless headphones" returns anything with those substrings in any field. No relevance ranking, no "similar products", no dynamic pricing or availability. | **Yes — substantially.** Search corpus provides ranked results; details corpus adds recommendations. Cart/checkout/orders work without simulation. |
| **DocumentPlatform** | Moderate | Document search only matches filenames — searching for content is impossible. But CRUD operations (upload, download, share) work correctly. | **Yes — for search.** Corpus provides content-aware search results. CRUD demo is already convincing. |

### Adequate for demos without simulation

| SPI | Demo Quality | Why |
|---|---|---|
| **ContactsPlatform** | Adequate | CRUD and group operations are convincing. Search is naive but contacts datasets are small enough that substring matching is plausible. |
| **ProjectPlatform** | Adequate | Full issue lifecycle (create, label, milestone, close) is convincing. Issue search is naive but project datasets are small enough. Board management works. |
| **EmailPlatform** | Adequate | Mailbox listing, message retrieval, and attachments work correctly. No search exists yet. Realistic seed messages with UK financial context look authentic. |
| **ChatPlatform** | Good | Full messaging lifecycle works. Channels, threads, reactions, presence all function correctly. Runtime-created state means the demo builds naturally. |
| **CalendarPlatform** | Good | Full CRUD + sync works correctly. Events with realistic timing and attendees look authentic. |
| **BankPlatform** | Good | Accounts with realistic UK sort codes and IBANs. Transaction history with plausible merchant names. Payment lifecycle with state machine progression. Pre-loaded seed data looks real. |

### Summary

Without simulation, 3 of 9 SPIs are hard to demo convincingly — LocationPlatform (very hard), CommercePlatform (hard), and DocumentPlatform (moderate for search). The simulation framework closes the gap for all three. The remaining 6 SPIs produce adequate-to-good demos from seed data alone, with simulation adding variety rather than capability.

## Gap: EmailPlatform.search

The design spec (#138 §Interpretive Capability Classification) lists email search as interpretive, and a `search-corpus.yaml` was shipped in #143. However, `EmailPlatform` has no `search()` method — the corpus file references `email-platform.search` which doesn't exist in the interface.

**Recommendation:** File a separate issue to add `EmailPlatform.search()`. The corpus file is correctly pre-positioned. Until the method exists, the corpus file is harmless but orphaned.

## Classification Nuance: Details Operations

ProductDetails (commerce) and PlaceDetails (location) are classified as interpretive, but their ref implementations are direct map lookups — functionally identical to deterministic CRUD. The "interpretive" label captures the fact that a real provider returns richer data (recommendations, availability scoring, opening hours) that the ref's static seed data can't represent.

For DataRealism tagging, these operations should receive `STRUCTURALLY_VALID` on ref fallthrough — the data is structurally correct but lacks the dynamic enrichment a real provider adds. This aligns with the design spec's tagging table (§DataRealism Response Metadata).
