# Design: Unify Ref Implementations with Simulation Framework

**Issue:** casehubio/connectors#138
**Date:** 2026-10-04
**Scope:** casehubio/connectors + casehubio/platform

## Problem Statement

Ref implementations and the simulation framework solve overlapping problems with different trade-offs. Ref implementations are live stateful services with hardcoded seed data. The simulation framework provides corpus-driven, strategy-controlled responses via generated `@Decorator` beans. Neither alone covers all use cases well.

Ref operations fall into two categories:

- **Deterministic** — seed data is sufficient because operations are state management (CRUD, lifecycle transitions). The ref logic is trivially correct.
- **Interpretive** — seed data isn't enough because operations require judgment the ref can't credibly provide (search relevance, recommendations, availability scoring). These need simulation-driven responses to be useful beyond basic code verification.

## Architecture

### Two-Layer Model

```
Layer 2 (Simulation): Strategy-driven enhancement. Optional. Configurable.
    ↓ fallthrough when no strategy resolves
Layer 1 (Ref): Stateful mutation + naive reads. Always present. Zero config.
```

- **Layer 1 (Ref):** Complete, standalone, in-memory implementation. Handles all operations — deterministic operations are authoritative, interpretive operations are deliberately naive (substring match, first-N items). Always works with zero configuration.
- **Layer 2 (Simulation):** Generated `@Decorator` intercepts per-capability via `SimulationDecoratorProcessor`. When a `SimulationStrategy` can resolve, it provides a realistic response. When it can't, it falls through to the ref.

### Data Flow

```
Seed files (YAML/CSV, domain-typed)
    ├── Ref seed loader → InMemory*Backend state (domain maps, lists)
    └── Simulation corpus loader → InvocationRecord entries (strategy input)

Runtime call:
    Consumer → SimulatedXxxPlatform (@Decorator)
        → strategy.canResolve()?
            yes → strategy.resolve() [DataRealism per strategy type]
            no  → RefXxxPlatform
                    interpretive op → [DataRealism.STRUCTURALLY_VALID]
                    deterministic op → [unmarked]
```

### Key Constraints

- **Minimal extension to the simulation framework.** The existing `SimulationDecoratorProcessor` generates capability-aware decorators and already tracks `boolean simulated` in `JournalEntry`. The extension: enhance the decorator generator to set `DataRealism` levels on the existing journal path — strategy-resolved responses get a level based on strategy type, ref fallthrough for interpretive operations gets `STRUCTURALLY_VALID`. The interception model itself is unchanged.
- **Ref stays complete standalone.** A consumer injecting a platform SPI with no simulation configuration gets a fully working ref with structurally correct responses for every operation.
- **Shared seed format, separate runtime models.** Both layers load from the same domain-typed seed files, but ref populates its `InMemory*Backend` state while simulation populates `InvocationRecord` entries. One source of truth, two runtime representations optimised for their purpose.

## Seed Data Model

### Current State

Each `InMemory*Backend` initialises data through hardcoded Java code — not configurable, not reusable across ref and simulation. The initialisation mechanism varies: 3 have explicit `seed()` methods called from the constructor (commerce, contacts, location), 1 uses a static factory (`ProjectBackend.withTestData()`), and 5 use other patterns (static field initialisation, constructor inline, or `@PostConstruct`).

### New Model

Domain-typed seed files in YAML, shipped alongside each ref module:

```
commerce-ref/src/main/resources/seed/
    products.yaml
    categories.yaml
```

```yaml
# products.yaml
- id: "PROD-001"
  name: "Sony WH-1000XM5 Headphones"
  brand: "Sony"
  category: "electronics"
  price: { amount: 299.99, currency: "GBP" }
  description: "Premium noise-cancelling wireless headphones"
  rating: 4.7
  reviewCount: 1284
  inStock: true
```

Each ref module provides a **seed loader** that reads YAML files and populates the `InMemory*Backend`, replacing the hardcoded `seed()` method. Programmatic builders remain available for test-specific scenarios.

Simulation corpus files are separate — InvocationRecord-format files that populate strategies. These can be auto-generated from domain seed files as a build step, or authored independently when simulation needs responses the seed data doesn't cover.

### Per-Module Changes

1. Extract hardcoded seed data from `InMemory*Backend.seed()` into YAML files
2. Add a seed loader class per ref module
3. Modify `*RefBeans` producer to use the loader instead of calling `seed()` directly

## DataRealism Response Metadata

### Existing Mechanisms

The `DataRealism` enum in `simulation-api` defines a 5-level quality spectrum:

```java
public enum DataRealism {
    GARBAGE, PLACEHOLDER, STRUCTURALLY_VALID, DOMAIN_PLAUSIBLE, RECORDED_REAL
}
```

The generated decorators already track `boolean simulated` in `JournalEntry` — `true` for strategy-resolved, `false` for delegate fallthrough. This is the foundation to build on.

### Extension

Replace the binary `boolean simulated` with `DataRealism` levels in the journal path:

| Source | Capability Type | DataRealism Level |
|--------|----------------|-------------------|
| Strategy resolved (recorded replay) | Any | `RECORDED_REAL` |
| Strategy resolved (nearest match, key lookup) | Any | `DOMAIN_PLAUSIBLE` |
| Strategy resolved (random, sequential) | Any | `STRUCTURALLY_VALID` |
| Ref fallthrough | Interpretive | `STRUCTURALLY_VALID` |
| Ref fallthrough | Deterministic | Unmarked (authoritative) |

The mapping from strategy type to DataRealism level is a property of the strategy — each `SimulationStrategy` implementation declares its realism level. The decorator reads this from the strategy that resolved the call.

### Tagging Mechanism

The existing `JournalEntry` already receives a simulated/not-simulated signal from the decorator. Extending this to carry `DataRealism` instead of `boolean` is a `JournalEntry` field change + decorator generator change. `DataRealism` stays in `simulation-api` (where it's defined); consumers that need to query realism levels already depend on `simulation-api` through the decorator.

For callers that need DataRealism at call time (not just in the journal), the decorator can set a request-scoped `DataRealism` context that callers inspect. This is a small CDI producer — not a change to SPI return types.

### Consumer Signals

- **Tests:** Ignore DataRealism — naive responses are structurally correct for code verification
- **Agent frameworks:** Check DataRealism — distinguish `STRUCTURALLY_VALID` (ref naive) from `DOMAIN_PLAUSIBLE`/`RECORDED_REAL` (simulation)
- **Demos:** Filter on DataRealism — ensure `DOMAIN_PLAUSIBLE` or better for interpretive operations

## Ref Normalisation

Before simulation integration, normalise all 9 refs to a consistent pattern (Phase 1).

### CDI Wiring

Two patterns exist:
- **CDI-managed backend (5/9):** `InMemory*Backend` is `@DefaultBean @ApplicationScoped`, injected into `*RefBeans` producer. Used by: chat, bank, email, document, calendar.
- **Direct construction (3/9):** `*RefBeans` creates backend with `new InMemory*Backend()`. Used by: commerce, contacts, location. These 3 are also the only ones with `seed()` methods called from the constructor — the construction pattern may be related (constructor seeding happens before CDI injection).
- **project-ref (1/9):** Uses `ProjectRefBeans` (not `ProjectRefBeans`), calls `ProjectBackend.withTestData()` static factory.

Standardise on the CDI-managed backend pattern (majority). Migrate the 3 direct-construction backends to `@DefaultBean @ApplicationScoped` with seed loading moved from constructor to `@PostConstruct` or producer logic. Rename `ProjectRefBeans` to `ProjectRefBeans` for consistency.

### `supports()` Pattern

Standardise on `Set.of()` constant:

```java
private static final Set<Class<?>> SUPPORTED = Set.of(
    ProductSearch.class, ProductDetails.class,
    Cart.class, Checkout.class, OrderTracking.class
);

@Override
public boolean supports(Class<?> capability) {
    return SUPPORTED.contains(capability);
}
```

Replace chained `==` comparisons in commerce-ref, location-ref, and others.

### `paginate()` Extraction

The duplicated cursor-based pagination utility across commerce-ref, contacts-ref, location-ref, and project-ref moves to `connectors-api` as a shared utility. The existing signature uses `Page<T>` and `PageRequest` (cursor-based), not offset/limit:

```java
public final class PaginationHelper {
    public static <T> Page<T> paginate(List<T> all, PageRequest pagination) { ... }
}
```

Note: platform's `graphql/` module has a separate `PaginationHelper` with offset-based semantics (`PageInput`/`PageResult`). The connectors pagination is cursor-based — a different contract. These are intentionally separate.

### DocumentPlatform `@SimulationEligible`

Add the `capabilities` attribute listing its 4 sub-interfaces:

```java
@SimulationEligible(
    name = "document-platform",
    capabilities = {"fileOperations", "folderOperations", "searchOperations", "sharingOperations"}
)
```

This matches every other capability-bearing SPI and enables capability-aware decorator generation.

### Calendar and Email SPIs

Leave as flat interfaces — they don't have optional capability sub-interfaces. Simulation interception stays method-level, which is correct for their shape.

## Interpretive Capability Classification

This classification guides strategy authoring and DataRealism tagging. It is documentation and spec guidance — not runtime metadata or annotation attributes. Whether an operation is interpretive is determined by strategy presence at runtime.

| SPI | Capability | Classification | Rationale |
|-----|-----------|---------------|-----------|
| **ChatPlatform** | All 9 capabilities | Deterministic | All state management |
| **CommercePlatform** | ProductSearch | Interpretive | Relevance ranking, fuzzy matching |
| | ProductDetails | Interpretive | Recommendations, availability scoring |
| | Cart, Checkout, OrderTracking | Deterministic | Stateful lifecycle |
| **ContactsPlatform** | ContactRead.search | Interpretive | Fuzzy name/field matching |
| | ContactRead (list/get/sync), GroupRead, ContactWrite | Deterministic | CRUD, enumeration |
| **DocumentPlatform** | SearchOperations | Interpretive | Full-text search relevance |
| | FileOperations, FolderOperations, SharingOperations | Deterministic | CRUD, state management |
| **LocationPlatform** | PlaceSearch, PlaceDetails, Geocoding, Directions | Interpretive | All operations require interpretation |
| **BankPlatform** | AccountInformation, PaymentInitiation | Deterministic | State management, lifecycle |
| **EmailPlatform** | Search | Interpretive | Query relevance |
| | List, get | Deterministic | Enumeration, retrieval |
| **CalendarPlatform** | All (CRUD, sync) | Deterministic | State management |
| **ProjectPlatform** | Issues.search | Interpretive | Query relevance |
| | Issues (CRUD), Labels, Milestones, Comments, Boards | Deterministic | State management |

LocationPlatform is the most simulation-heavy — all 4 capabilities are interpretive. ChatPlatform, BankPlatform, and CalendarPlatform are fully deterministic. The rest are mixed.

## Implementation Ordering

### Phase 1 — Ref Normalisation (connectors repo)

No platform dependencies. Can be implemented and merged independently.

1. Extract `paginate()` to `connectors-api` shared utility
2. Normalise `supports()` to `Set.of()` pattern across all 9 refs
3. Normalise CDI wiring — migrate chat-ref to producer pattern
4. Fix DocumentPlatform `@SimulationEligible` capabilities attribute
5. Add tests verifying each normalised ref still works

### Phase 2a — Platform Changes (platform repo)

Prerequisites for Phase 2b. Publish as SNAPSHOT before connectors work begins.

1. Extend `JournalEntry` to carry `DataRealism` instead of `boolean simulated`
2. Add `DataRealism dataRealism()` method to `SimulationStrategy` interface (each strategy declares its realism level)
3. Update `SimulationDecoratorProcessor` to set `DataRealism` in journal entries — strategy-resolved uses the strategy's declared level, fallthrough uses `STRUCTURALLY_VALID` for interpretive or omits for deterministic
4. Add request-scoped `DataRealism` CDI producer for callers that need realism level at call time
5. Seed file loading infrastructure if needed

### Phase 2b — Simulation Integration (connectors repo)

Depends on Phase 2a SNAPSHOTs being available.

1. Extract hardcoded seed data from each `InMemory*Backend` (regardless of initialisation mechanism) into YAML files
2. Add seed loader per ref module
3. Wire loaders into `*RefBeans` producers
4. Ship default simulation corpus files for interpretive capabilities
5. Verify DataRealism tagging end-to-end for a mixed SPI (e.g., CommercePlatform)

### Issue Decomposition

- **#138** — parent/design issue (this spec)
- **Phase 1** — one issue on casehubio/connectors
- **Phase 2a** — one issue on casehubio/platform
- **Phase 2b** — one issue on casehubio/connectors (blocked by Phase 2a)

## What Changes and What Doesn't

**Changes (platform):**
- `JournalEntry` — `boolean simulated` → `DataRealism` level
- `SimulationStrategy` — adds `DataRealism dataRealism()` declaration
- `SimulationDecoratorProcessor` — sets DataRealism in journal entries (interception model itself unchanged)
- New request-scoped `DataRealism` CDI producer

**Unchanged:**
- `SimulationDecoratorProcessor` interception model (capability-aware decoration, strategy resolution, fallthrough)
- `SimulationCorpus` API
- `@SimulationEligible` annotation attributes (except DocumentPlatform capabilities fix)
- The `*Backend` interface layer — survives as the ref's internal operational contract
- Real provider implementations (chat-slack, calendar-google, etc.) — unaffected

## References

- casehubio/connectors#138 — parent issue
- casehubio/connectors#137 — QuarkusTest CDI wiring test (validates ref standalone utility)
- casehubio/platform#375 — SimulationEligible recursive wrapper generation
- `platform/simulation-api/.../SimulationEligible.java` — annotation definition
- `platform/simulation-generator/.../SimulationDecoratorProcessor.java` — decorator generator
- `platform/simulation-core/.../SimulationRuntime.java` — strategy resolution
- `platform/simulation-api/.../SimulationStrategy.java` — strategy SPI
- `platform/simulation-api/.../SimulationCorpus.java` — corpus API
- `simulation-api` — DataRealism enum, JournalEntry (boolean simulated → DataRealism)
- All 9 ref modules: chat-ref, commerce-ref, calendar-ref, contacts-ref, document-ref, email-ref, bank-ref, location-ref, project-ref
- Memory: feedback_simulation_framework.md — SimulationEligible is general service virtualisation, not LLM-specific
