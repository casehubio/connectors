## D1: Generator enhancement for capability-based SPIs — recursive wrapper generation

**Choice:** Enhance `SimulationDecoratorProcessor` in platform to identify capability accessor methods via the `capabilities` attribute on `@SimulationEligible` (see D5) and generate wrapper classes for each. Wrappers follow the same intercept-or-delegate pattern as the top-level decorator, using dotted qualified names (`spi.capability.method`). Zero hand-written simulation code per SPI.
**Alternatives:**
- Hand-written SimulatedChatPlatform in connectors — works but adds per-SPI maintenance burden, contradicts D1 from issue #94 (zero hand-written sim code)
- Make capability sub-interfaces CDI beans with their own @SimulationEligible — architectural mismatch; capabilities aren't CDI-managed injection points, they're factory-returned objects
**Rationale:** The existing generator already produces the intercept-or-delegate pattern for every method. Extending it to recurse into capability-returning methods is a natural generalization. The `capabilities` attribute on `@SimulationEligible` (D5) tells the generator which methods return capability interfaces — no heuristic inspection needed. Works for any future capability-based SPI; adding a new capability requires one string in the annotation alongside the other required updates (Builder, DefaultRecord, degradation default, implementations).
**Trade-offs:** Requires a platform PR alongside the connectors work. Generator complexity increases — must handle nested class generation and dotted qualified name construction.
**Sources:** `platform/simulation-generator/SimulationDecoratorProcessor.java` (existing generator), `chat-spi/ChatPlatform.java` (capability-based SPI), ADR-0011 (simulation framework decision), issue #94 decisions.md D1 (zero hand-written sim code principle)
**Exploration:** deep-analysis
**Status:** captured

## D2: Separate platform issue for generator enhancement

**Choice:** File a new issue in casehubio/platform for the recursive wrapper generation enhancement. Issue #105 in connectors depends on it.
**Alternatives:**
- Single cross-repo issue — smaller process overhead but less traceable; the platform change is a framework capability, not a connector concern
**Rationale:** The generator enhancement is a reusable framework capability. Platform and connectors have separate release lifecycles. Tracking them as separate issues with a dependency makes the platform change independently reviewable and testable.
**Trade-offs:** Two issues to manage instead of one. Connectors work is blocked until the platform change lands.
**Sources:** Issue #105 scope discussion
**Exploration:** quick
**Status:** captured

## D3: Hybrid layering — ref modules stay, NoOps added, simulation composes on top

**Choice:** Keep calendar-ref and chat-ref modules. Add `@DefaultBean` NoOp implementations in the SPI modules as CDI fallback. The simulation decorator composes on top of whichever concrete implementation is present (ref if on classpath, NoOp if not). Three composable modes: scenario (overlay active, strategies intercept), test (ref seeded directly), interactive (ref seeded with corpus data, no overlay).
**Alternatives:**
- Replace ref modules with simulation-only — loses stateful CRUD behavior that corpus can't replicate
- Keep chat-ref, replace calendar-ref — inconsistent approach across SPIs
**Rationale:** Ref modules provide stateful in-memory behavior (create → list → verify). Simulation provides predictable corpus-driven interception. They compose naturally through CDI decorator precedence: `Caller → Decorator → [Ref | NoOp]`. The ref can also be seeded with corpus data for interactive/demo use, giving realistic starting state with real behavior.
**Trade-offs:** More modules on the classpath. Three modes to understand. But each mode serves a distinct use case that the others can't.
**Sources:** `chat-ref/InMemoryChatBackend.java` (stateful backend), `calendar-ref/InMemoryCalendarBackend.java`, `bank-spi/NoOpBankFeedPlatform.java` (NoOp pattern), `SimulationDecoratorProcessor.generateSimulatedMethod()` (intercept-or-delegate logic)
**Exploration:** quick (user-directed — clarified the hybrid intent)
**Depends on:** D1
**Status:** captured

## D4: Validation via integration test example

**Choice:** Include a scenario-style integration test that proves the full hybrid stack works end-to-end. The test validates: flat SPI corpus simulation (CalendarPlatform), capability-based corpus simulation (ChatPlatform capabilities), hybrid delegation (strategy miss → ref fallback), journal recording for both levels, and correct QN constant generation.
**Alternatives:**
- Unit tests only — doesn't validate CDI wiring, decorator precedence, or corpus loading
- Extend existing household-finance scenario — couples unrelated SPIs in one test
**Rationale:** The generator enhancement is a new framework capability. Without an integration test exercising the full CDI stack (decorator generation → strategy resolution → corpus loading → journal recording → ref delegation), failures would only surface in consuming apps. The example can live in graphql's test resources alongside household-finance.
**Trade-offs:** Heavier test setup. But the household-finance pattern is already established — this follows the same structure.
**Sources:** `graphql/src/test/resources/scenarios/household-finance/` (existing scenario pattern), simulation integration test from #104
**Depends on:** D1, D3
**Exploration:** quick (user-directed)
**Status:** captured

## D5: Explicit capability declaration for recursive wrapper generation

**Choice:** Add a `capabilities` attribute to `@SimulationEligible` listing the names of capability accessor methods: `@SimulationEligible(name = "chat-platform", capabilities = {"messaging", "threading", "discovery", "reactions", "presence", "members", "channelManagement", "memberManagement", "messageHistory"})`. The generator reads this attribute to identify which methods need recursive wrapper generation. Methods not listed get standard flat interception. When `capabilities` is empty (default), all methods get flat interception — backward compatible with existing flat SPIs like BankFeedPlatform.
**Alternatives:**
- Auto-detection heuristic (original D5) — inspects return types and uses an exclusion list (java.*, jakarta.*, List/Set/Map/Optional/Page). Fragile: couples domain knowledge (which methods return capabilities) to the framework (platform). Every new framework type (Smallrye Mutiny Uni/Multi, Vert.x Future) or sealed-interface data type would require updating the exclusion list in a different repo with a different release cycle.
- Method-level `@SimulationCapability` annotation on each accessor — more ceremony; multiple annotations per SPI instead of one attribute
**Rationale:** Domain knowledge (which methods return capabilities) stays in the domain code (the SPI annotation in connectors), not encoded as heuristic rules in the framework (platform). Correct by construction — no false positives, no exclusion list maintenance. Adding a capability to an SPI already requires updating Builder, DefaultRecord, degradation defaults, and every implementation — one more string in the annotation is negligible marginal cost. The generator becomes simpler: no type inspection logic, no exclusion list, just read the annotation attribute and match method names.
**Trade-offs:** Annotation must be updated when a new capability is added to the SPI. Negligible given the other required updates.
**Sources:** R1-01 and R1-07 review challenges, `@SimulationEligible` annotation, ChatPlatform Builder pattern
**Depends on:** D1
**Exploration:** quick → revised (R1-01, R1-07 fresh perspective)
**Status:** revised (was: auto-detection heuristic; now: explicit capability declaration. Changed because the heuristic encodes domain knowledge in the framework and creates a maintenance coupling across repos — explicit declaration is architecturally cleaner and correct by construction.)

## D6: Corpus scope — core 3 capabilities for ChatPlatform MVP

**Choice:** Write corpus YAML for Messaging, Discovery, and Members capabilities initially. The remaining 6 (Threading, Reactions, Presence, ChannelManagement, MemberManagement, MessageHistory) delegate to the ref/NoOp — no corpus configured, so the decorator passes through.
**Alternatives:**
- All 9 capabilities — complete but more upfront corpus authoring work; diminishing returns for validation
- Messaging + Discovery only — validates send and list but misses the members list pattern
**Rationale:** These three cover the most common scenario paths: send a message, list channels, list members. They exercise all the strategy types (seq for lists, key for lookups). The decorator handles unconfigured capabilities via delegation regardless — adding corpus for the remaining 6 is incremental.
**Trade-offs:** Scenarios needing reactions, threading, or presence will use ref/NoOp behavior until corpus is added. Pre-release, this is acceptable.
**Depends on:** D1, D4
**Exploration:** quick
**Status:** captured

## D7: supports() interception under simulation — reflect effective capability set

**Choice:** The simulation decorator overrides `supports()` to return true for any capability whose methods have registered simulation strategies, in addition to capabilities the delegate natively supports. This ensures semantic coherence: if the simulation provides Messaging behavior via corpus, `supports(Messaging.class)` reports Messaging as available.
**Alternatives:**
- Delegate `supports()` unchanged — creates semantic incoherence where simulation provides a capability but `supports()` reports it as absent. Callers that check `supports()` before using a capability would skip simulation-provided behavior.
- NoOp always reports all capabilities as supported — changes NoOp semantics for non-simulation use
- Redefine `supports()` contract under simulation — documented but surprising for callers
**Rationale:** `supports()` is a meta-query about the platform's effective capability set. Under simulation, the decorator IS the platform. If the decorator intercepts capability method calls with corpus data, it effectively provides those capabilities and should report them as supported. The `capabilities` attribute from D5 (revised) provides the class-to-method mapping at generation time, enabling the generator to emit a `supports()` override that checks `simulation.strategyFor()` for each capability's methods. The sole production caller (`ConnectorOperationsImpl.connectorStatus()`) iterates all 9 capability classes — incorrect results would be visible to LLM agents and UI consumers.
**Trade-offs:** Requires the generator to emit a capability-class-to-method-name mapping as part of the `supports()` override. Minor additional generated code.
**Sources:** R1-03 review challenge, `ConnectorOperationsImpl.connectorStatus()`, `DefaultChatPlatform.supports()`, `ChatPlatform.Builder.nativeCapabilities`
**Exploration:** implicit decision surfaced by review
**Depends on:** D1, D5
**Status:** captured

## D8: CalendarPlatform CRUD simulation mode — ref delegate for state coherence

**Choice:** CalendarPlatform uses D3's hybrid layering with the ref module as delegate. The decorator intercepts each method directly (flat SPI — no recursive wrappers needed). Corpus overlay for read methods (`listCalendars`, `listEvents`, `getEvent`) returns predictable scenario data. Write methods (`createEvent`, `updateEvent`, `deleteEvent`) either delegate to the ref for state coherence, or use a void/echo strategy when state coherence is not needed. Scenarios requiring create→list coherence should NOT configure corpus for the list methods, letting them delegate through to the ref.
**Alternatives:**
- CalendarPlatform simulation-only with NoOp delegate — breaks create→list state coherence; a scenario that calls `createEvent()` then `listEvents()` gets empty list regardless
- CalendarPlatform excluded from `@SimulationEligible` — loses corpus-driven scenario capabilities for reads; forces all scenarios to use the ref directly
- Read-only simulation (writes always delegate, reads always use corpus) — too rigid; some scenarios need predictable write responses while others need state coherence
**Rationale:** CalendarPlatform is a CRUD SPI, not read-only like BankFeedPlatform. Corpus-driven simulation works for reads but cannot maintain state coherence across writes and subsequent reads. D3's hybrid layering already provides the right composition: `Caller → Decorator → Ref`, with corpus overlay intercepting specific methods per strategy configuration. The same three composable modes apply: scenario (overlay active), test (ref seeded directly), interactive (ref seeded with corpus data, no overlay). Issue #105's "straightforward" label for CalendarPlatform applies to the flat SPI structure (no recursive wrappers), not to the simulation mode — CRUD coherence requires the same hybrid approach as ChatPlatform.
**Trade-offs:** Scenario authors must understand the coherence boundary — configuring corpus for both writes and their corresponding reads breaks state coherence. This is a scenario authoring constraint, documented in the scenario guide, not a framework limitation.
**Sources:** R1-02 review challenge, `CalendarPlatform` interface (6 methods: 3 reads, 3 writes), `RefCalendarPlatform` (stateful delegate via `CalendarBackend`), D3 hybrid layering
**Exploration:** implicit decision surfaced by review
**Depends on:** D1, D3
**Status:** captured

SETTLED: Ref modules are needed for CRUD SPIs that require stateful write-then-read coherence. Read-only SPIs (BankFeedPlatform) use simulation-only with a `@DefaultBean` NoOp delegate. CRUD SPIs (CalendarPlatform, ChatPlatform) keep ref modules as stateful delegates alongside the simulation decorator. This refines ADR-0011's "no sim/demo/ref modules" — the simulation framework replaces hand-written sim/demo modules; ref modules persist where statefulness is required. (from R1-05)
