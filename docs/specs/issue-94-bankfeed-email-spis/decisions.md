## D1: Simulation framework replaces hand-written sim/demo/ref modules

**Choice:** SPIs are annotated with `@SimulationEligible`. The platform simulation framework (simulation-api, simulation-core, simulation-generator) generates CDI `@Decorator` classes at build time that intercept every SPI method. Simulation behavior is configured at runtime via strategies and corpus data — no hand-written sim, demo, or ref modules needed. A `@DefaultBean` no-op fallback satisfies CDI injection when no real provider exists.
**Alternatives:**
- Single "sim" module per SPI — hand-written simulation class serving testing, dev, and production simulation; workable but creates maintenance burden (must be updated when SPI changes) and duplicates what the simulation framework generates automatically
- Demo SPI Convention pattern (ref + demo modules) — two hand-written modules per SPI following the convention; even more maintenance, and the convention predates the simulation framework
- Ref module only — plain in-memory impl for tests; doesn't cover scenario-driven simulation
**Rationale:** The simulation framework (`platform/simulation-*`) landed in platform with decorator generation, overlay stack, corpus seeding, strategy selection, invocation journal, and verification. The scenario engine in pages (`ScenarioOrchestrator`) integrates directly — scenarios push simulation overlays with scenario-specific corpus data. This eliminates the need for hand-written simulation code entirely. The Demo SPI Convention should evolve to reference the framework rather than prescribe hand-written demo modules.
**Trade-offs:** Depends on the simulation framework being stable (it just landed). The `@DefaultBean` no-op is ceremony — could be generated alongside the decorator in a future framework enhancement.
**Sources:** `platform/simulation-api/SimulationEligible.java`, `platform/simulation-generator/SimulationDecoratorProcessor.java` (generates decorators), `platform/simulation-core/Simulation.java` (test facade), `pages/scenario-runtime/ScenarioOrchestrator.java` (scenario integration via `activateSimulation()`)
**Exploration:** deep-analysis (evolved through sim naming discussion → framework discovery)
**Status:** captured

## D2: Categorisation is a consumer concern — Transaction model includes category field

**Choice:** BankFeedPlatform returns raw transactions. The `Transaction` model includes a `category` field that providers may populate natively — providers that don't categorise leave it null. Categorisation logic beyond what the provider offers lives in the consumer domain (life/AML).
**Alternatives:**
- Optional SPI capability — `Categorisation` sub-interface with `supports()` check; adds capability infrastructure for a feature most providers don't have natively
- Required SPI method — `categorise()` on the interface; forces all providers to implement it
**Rationale:** Open Banking APIs (TrueLayer, Yapily) don't provide categorisation. Plaid offers it via a separate enrichment endpoint, not as part of transaction retrieval. The SPI should model what data access providers return. The `category` field on `Transaction` satisfies the issue #94 acceptance criteria ("categorisation interfaces") without adding SPI complexity — providers that natively categorise populate the field, consumers that need domain-specific categorisation add their own logic.
**Trade-offs:** No SPI-level categorisation method. If a future provider's categorisation is sufficiently different from a model field (e.g., returns confidence scores, multiple candidate categories), a capability interface would be needed. Pre-release, this refactoring is cheap.
**Sources:** Issue #94 acceptance criteria, TrueLayer/Yapily API documentation (no native categorisation), Plaid API (categorisation via separate endpoint)
**Exploration:** quick
**Status:** revised (reinstated original decision; acceptance criteria satisfied via model field, not SPI method)

## D3: EmailPlatform complements existing email modules

**Choice:** EmailPlatform is a query/read SPI (list inbox, get message, search) that sits alongside EmailConnector (outbound) and EmailInboundConnector (push inbound).
**Alternatives:**
- Supersede existing — unified platform SPI covering send/receive/query; collapses three architectural layers into one
**Rationale:** Transport connectors and platform SPIs are architecturally distinct layers. EmailConnector is outbound transport, EmailInboundConnector is pull-based inbound transport, EmailPlatform is a domain abstraction at the platform SPI level. This is how ChatPlatform coexists with chat connectors and inbound connectors. The overlap between EmailPlatform queries and EmailInboundConnector push events is handled by D10.
**Trade-offs:** Three separate entry points for email (send, receive, query). Consumers using both query and push must be idempotent (D10).
**Sources:** `email/EmailConnector.java`, `email-inbound/EmailInboundConnector.java`, `chat-spi/ChatPlatform.java` (coexistence pattern)
**Exploration:** quick
**Status:** captured (unchanged)

## D4: Flat interface for both SPIs

**Choice:** Both BankFeedPlatform and EmailPlatform use flat interfaces with direct methods, like CalendarPlatform.
**Alternatives:**
- Capability-based for BankFeedPlatform — ChatPlatform-style sub-interfaces with `supports()` and degraded fallbacks; more extensible but heavier, and the simulation framework's decorator intercepts at the direct method level making flat interfaces the natural fit
- Capability-based for both — over-engineers EmailPlatform where provider variance is minimal
**Rationale:** These SPIs have a small, well-defined surface. All core operations (list accounts, get balance, list transactions; list mailboxes, list messages, get message) are fundamental to the platform's purpose — no meaningful subset that a provider would not support. The simulation framework generates per-method decorators that intercept at the SPI method level; flat interfaces map directly to qualified names (`bank-feed-platform.listTransactions`). Pre-release, flat→capability refactoring is cheap if provider variance proves more extreme than expected. Consent management (D7) is noted as a future concern, not a current capability interface.
**Trade-offs:** Less extensible if providers vary significantly in supported operations. But starting flat and adding capabilities when a real provider forces the issue is cheaper than building capability infrastructure speculatively.
**Sources:** `calendar-spi/CalendarPlatform.java` (flat, proven), `platform/simulation-generator/SimulationDecoratorProcessor.java` (per-method interception)
**Exploration:** quick
**Status:** revised (reinstated original flat-for-both decision; simulation framework reinforces flat as natural fit)

## D5: Push events via WebhookInboundConnector — simulation handles injection

**Choice:** Production push events use the existing WebhookInboundConnector SPI. Simulated push events are handled by the simulation framework's overlay system — no hand-written injection endpoints or methods needed. Platform SPI stays query-only.
**Alternatives:**
- Demo convention injection endpoints (`/scenario/inject`) — REST endpoints that fire CDI events; adds hand-written code the simulation framework makes unnecessary
- Method on concrete class — requires casting to concrete type; couples injection to impl
- Platform SPI event source — puts event subscription on the SPI; creates overlapping mechanisms
**Rationale:** The simulation framework's decorator intercepts all SPI calls. For push event simulation during scenarios, the scenario engine can fire CDI `InboundMessage` events directly (the existing mechanism) while the simulation overlay handles the query side. No special injection infrastructure needed on the SPI itself.
**Trade-offs:** Push event simulation relies on the existing InboundConnector CDI event mechanism rather than going through the simulation framework's corpus. This means push simulation and query simulation use different mechanisms — acceptable because they ARE different mechanisms in production too.
**Sources:** `email-inbound/EmailInboundConnector.java`, `platform/simulation-core/SimulationOverlay.java`, `pages/scenario-runtime/ScenarioOrchestrator.java`
**Exploration:** quick
**Status:** revised (simulation framework replaces hand-written injection)

## D6: Corpus seeding via simulation framework — YAML corpus files + Simulation.forTest()

**Choice:** Simulation data is loaded via the simulation framework's corpus seeding mechanisms: `CorpusSeed` API for programmatic seeding, YAML corpus files for scenario-driven seeding (loaded by `YamlCorpusLoader` via `ScenarioOrchestrator`), and `Simulation.forTest().stub()/seed()` for test fixtures.
**Alternatives:**
- REST bootstrap endpoint (`POST /scenario/bootstrap`) — adds per-SPI REST endpoints; the simulation framework's overlay system handles this at the infrastructure level instead
- Classpath JSON files — baked into the build; less flexible than runtime corpus loading
- Programmatic seed in code — hardcoded data; least flexible
**Rationale:** The simulation framework provides three seeding paths that cover all use cases: `CorpusSeed` for programmatic setup, YAML files for scenarios (loaded by `ScenarioOrchestrator.activateSimulation()` via `SimulationSpec.corpus()`), and the `Simulation.forTest()` builder for test fixtures. No per-SPI data loading mechanism needed.
**Trade-offs:** Depends on `YamlCorpusLoader` landing (referenced by `ScenarioOrchestrator` but not yet in platform). The YAML corpus format for bank transactions and email messages needs designing — the ergonomics of authoring realistic financial data in YAML will affect adoption.
**Sources:** `platform/simulation-api/CorpusSeed.java`, `platform/simulation-core/Simulation.java` (forTest builder), `pages/scenario/SimulationSpec.java` (corpus paths), `pages/scenario-runtime/ScenarioOrchestrator.java` (activateSimulation)
**Exploration:** quick
**Status:** revised (simulation framework replaces per-SPI bootstrap)

## D7: Consent management as a future concern — not built now

**Choice:** PSD2 consent management is documented as a future concern in the spec. The flat SPI (D4) does not include consent interfaces. When a real PSD2 provider (TrueLayer/Yapily) is implemented, consent management will be added — either as a SPI extension or a separate concern. The `Transaction` model does not encode consent state.
**Alternatives:**
- Build consent capability now — optional capability interface with `ConsentManagement` sub-interface; speculative complexity before a real provider exists
- Ignore entirely — risks designing an SPI that can't express consent semantics when PSD2 providers arrive
**Rationale:** No real bank feed provider exists yet. Consent lifecycle is complex (redirect URLs, callback handling, token storage, expiry). Building it speculatively risks getting the abstraction wrong. Documenting it as a known future concern ensures the SPI design is aware of it without committing to a premature abstraction. Pre-release, adding consent later is a non-breaking change (new methods on the interface or a new capability).
**Trade-offs:** First PSD2 provider integration will need to design consent in addition to implementing the SPI. But designing it with a real provider in hand will produce a better abstraction.
**Sources:** PSD2 regulation, TrueLayer/Yapily API documentation (SCA redirect flows)
**Exploration:** surfaced by review (R1-09)
**Status:** revised (deferred from "build now" to "document as future concern")

## D8: Pagination in method signatures for BankFeedPlatform

**Choice:** `BankFeedPlatform.listTransactions()` accepts pagination parameters and returns a paginated result type. `EmailPlatform.listMessages()` also supports pagination. Adding pagination to the method signature now prevents a breaking change when real providers with large datasets arrive.
**Alternatives:**
- Internal pagination only — impl paginates internally, returns flat list; risks OOM on bank transaction histories spanning years
- Add pagination later — breaks all callers when the flat `List<T>` return type changes
**Rationale:** Bank transaction histories can span years with millions of records. The method signature is the API contract — changing it later breaks every caller. The simulation framework's corpus will serve small datasets that fit in a single page, so pagination adds no complexity to the simulation path.
**Trade-offs:** Callers must handle paginated results even when the simulation corpus returns everything in one page. Minor API friction for future-proofing.
**Sources:** `slack-bot/SlackBotClient.java` (cursor pagination), PP-20260610-83747b (paginating client fail-soft protocol)
**Exploration:** surfaced by review (R1-10)
**Status:** captured (accepted from review)

## D9: Multi-provider routing via platform service pattern

**Choice:** `BankFeedPlatformService` and `EmailPlatformService` follow the established pattern: discover CDI beans via `@All List<T>`, route by `id()`. Cross-provider aggregation is a consumer concern.
**Alternatives:**
- Single aggregating impl — god-class with cross-provider concerns
**Rationale:** Follows `ChatPlatformService` and `CalendarPlatformService` exactly.
**Trade-offs:** Consumers wanting a unified cross-provider view must aggregate themselves.
**Sources:** `chat-spi/ChatPlatformService.java`, `calendar-spi/CalendarPlatformService.java`
**Exploration:** surfaced by review (R1-11)
**Status:** captured (unchanged)

## D10: EmailPlatform/EmailInboundConnector overlap — consumers must be idempotent

**Choice:** EmailPlatform queries and EmailInboundConnector push events may surface the same message. No deduplication at the SPI level. Consumers observing both paths must be idempotent.
**Alternatives:**
- SPI-level deduplication — couples EmailPlatform to EmailInboundConnector's state
- Exclusive paths — breaks the query model with gaps in inbox listings
**Rationale:** EmailInboundConnector's delivery guarantee is at-least-once. Observers must already be idempotent. Adding deduplication would couple two architecturally separate SPIs.
**Trade-offs:** Consumers using both paths handle duplicates. Already a documented requirement.
**Sources:** `email-inbound/EmailInboundConnector.java` (at-least-once), ARC42STORIES.MD §8
**Exploration:** surfaced by review (R1-04)
**Status:** captured (unchanged)

## D11: Two modules total — bank-spi and email-spi

**Choice:** Two new Maven modules: `bank-spi` and `email-spi`. Each contains the SPI interface, model records, platform service, and `@DefaultBean` no-op fallback. No separate sim, demo, or ref modules. Example corpus YAML files ship as test resources.
**Alternatives:**
- Four modules (spi + sim per SPI) — original plan before simulation framework discovery; unnecessary now
- Six modules (spi + ref + demo per SPI) — Demo SPI Convention pattern; maximum maintenance overhead, superseded by framework
**Rationale:** The simulation framework generates decorators at build time from `@SimulationEligible`. The scenario engine loads corpus data at runtime. No hand-written simulation code exists in the SPI modules — they contain only the interface, models, service registry, and CDI fallback. This is the simplest module structure that satisfies all requirements.
**Trade-offs:** Example corpus YAML files are test resources, not main resources — they're reference data for scenario authors, not runtime dependencies. If corpus files grow complex enough to warrant a separate module, that's a future concern.
**Sources:** D1 (framework replaces hand-written simulation), `platform/simulation-generator/SimulationDecoratorProcessor.java`
**Exploration:** quick
**Depends on:** D1
**Status:** captured
