# Decisions — #138 Unify Ref Implementations with Simulation Framework

## D1: Primary consumer

**Choice:** All three equally — service virtualisation for tests, demos, and agent development
**Alternatives:**
- Agent development only — too narrow, misses test controllability
- Integration tests only — misses demo and agent use cases
**Rationale:** The design must work for deterministic tests, visually credible demos, and realistic agent tool responses without being tuned for any single consumer
**Trade-offs:** Broader scope means more careful precedence modelling
**Sources:** Issue #138 body, feedback memory (simulation-framework-purpose)
**Exploration:** quick
**Status:** captured

## D2: Seed data model

**Choice:** Shared seed format, separate runtime models — both ref and simulation load from the same domain-typed seed files (YAML/CSV), but ref populates its InMemory*Backend state while simulation populates InvocationRecord entries
**Alternatives:**
- Unified corpus (original D2) — ref seed data IS corpus data, one runtime model. Rejected: SimulationCorpus stores immutable InvocationRecord<I,O> pairs; can't represent mutable state sequences (addToCart → addToCart → viewCart). Impedance mismatch harms both layers.
- Separate stores — ref keeps own model, simulation corpus independent. Eliminates format coupling but creates dual-format maintenance burden.
**Rationale:** One source of truth for seed data eliminates dual-format maintenance. Two runtime representations (domain state vs InvocationRecords) are each optimized for their purpose. Refs keep their stateful InMemory*Backend models; simulation keeps its strategy-based resolution.
**Trade-offs:** Two loaders consume the same files — must ensure consistency. Seed file schema becomes a cross-cutting concern.
**Sources:** R1-03 (decision review), SimulationCorpus/InvocationRecord API, InMemory*Backend implementations
**Exploration:** quick → revised after decision review
**Status:** revised

## D3: Delegation model

**Choice:** Existing decorator interception, unchanged — simulation wraps ref transparently via generated @Decorator. SimulationStrategy.canResolve() determines hit/miss per capability. Ref never knows simulation exists. "Opt-in delegation" is configuration guidance (which capabilities should have strategies registered), not a mechanism change.
**Alternatives:**
- Ref-controlled delegation — ref explicitly marks capabilities for simulation. Requires fundamental changes to SimulationDecoratorProcessor and changes the interception model from transparent to participatory.
- Per-method annotation — fine-grained but verbose, doesn't map to capability sub-interfaces.
**Rationale:** The existing framework already supports capability-level interception and strategy-based resolution. The presence or absence of a SimulationStrategy for a given capability IS the opt-in. No new mechanism needed.
**Trade-offs:** Ref implementations have no compile-time visibility into which capabilities are simulated — this is by design (transparent interception) but means the ref can't adjust its behavior based on simulation presence.
**Sources:** R1-04 (decision review), SimulationDecoratorProcessor (platform simulation-generator), existing decorator model
**Exploration:** quick → revised after decision review
**Status:** revised

## D4: Spec scope

**Choice:** Both repos (platform + connectors) in one spec, implementation splits into separate issues per repo
**Alternatives:**
- Connectors only — defers platform changes, risks designing against assumptions about the simulation framework API
- Platform first — sequential, slower, but eliminates cross-repo coordination risk
**Rationale:** One coherent design prevents the two sides from diverging. Implementation order is platform first (prerequisite), then connectors. Cross-repo coordination: connectors uses SNAPSHOT versions of platform; platform changes land and publish SNAPSHOTs before connectors implementation begins.
**Trade-offs:** Spec is larger and spans two codebases; requires coordination for implementation
**Sources:** Issue #138 scope section, R1-10 (dependency coordination)
**Exploration:** quick
**Status:** captured

## D5: Ref normalisation — two-phase approach

**Choice:** Phase 1: normalise all 9 refs (CDI wiring, supports() patterns, paginate() extraction, DocumentPlatform capability annotation). Phase 2: simulation integration against normalised refs. Each phase is a separate reviewable change.
**Alternatives:**
- Single pass — both normalisation and simulation integration in one change. Mixes structural cleanup with behavioural changes; harder to review.
- Unification only — touch only what's needed for simulation delegation. Less risk but leaves inconsistencies.
**Rationale:** Two-phase gives a known-good baseline before adding simulation complexity. Phase 1 can be reviewed and merged independently. Reviewer can tell whether a change is cleanup or simulation integration.
**Trade-offs:** Two passes over 9 modules instead of one; slightly more total effort but significantly cleaner diffs
**Sources:** R1-08 (decision review), R1-12 (DocumentPlatform annotation inconsistency)
**Exploration:** quick → revised after decision review
**Status:** revised

## D6: Ref completeness and simulation role

**Choice:** Ref stays complete standalone; simulation is an enhancement layer, not a replacement
**Alternatives:**
- Ref as thin stateful layer requiring simulation — cleaner architecture but breaks zero-config utility and adds simulation-setup tax to every consumer
- Simulation replaces ref for pure-query SPIs (e.g. LocationPlatform) — logical but inconsistent model across SPIs
**Rationale:** Zero-config utility is a real feature (QuarkusTest CDI wiring, quick verification). Ref's naive implementations of interpretive operations (substring search, first-N recommendations) are structurally correct for code verification. Simulation upgrades them when realism matters. Precedence is clean: simulation intercepts first, falls through to ref when no strategy exists.
**Trade-offs:** Ref implementations of interpretive operations are deliberately simple — they work but don't pretend to be smart. Consumers wanting realistic responses must configure simulation.
**Sources:** Issue #137 (QuarkusTest CDI wiring), first-principles analysis of ref vs simulation responsibilities
**Exploration:** deep-analysis
**Status:** captured
**Depends on:** D3 (existing decorator model)

## D7: Fallback behavior with DataRealism metadata

**Choice:** Naive fallback with DataRealism response metadata — ref always provides a working implementation. Ref responses for interpretive operations carry DataRealism.SYNTHETIC; simulation responses carry DataRealism.REALISTIC. No log noise, clear signal for consumers that need to distinguish.
**Alternatives:**
- Silent naive fallback (original D7) — no signal when simulation config is missing. Undermines agent development use case where naive responses create a quality trap.
- Explicit failure — throw UnsupportedCapabilityException when no strategy exists. Forces simulation config but breaks zero-config utility.
- WARNING log — works but adds noise in test environments where naive is intentional.
**Rationale:** DataRealism already exists in platform-api (C23). It was designed for exactly this purpose. Response metadata travels with the response — tests can ignore it, agent frameworks can check it, demos can filter on it.
**Trade-offs:** Ref implementations must tag interpretive responses with DataRealism.SYNTHETIC; requires a response wrapper or metadata carrier mechanism
**Sources:** R1-05 (decision review), DataRealism enum (platform-api), R1-11 (response-level distinguishability)
**Exploration:** quick → revised after decision review
**Status:** revised
**Depends on:** D6 (ref completeness)

## D8: Corpus loading mechanisms

**Choice:** Both file-based (YAML/CSV) and programmatic builders — domain-typed seed files (not InvocationRecord format) loaded by per-SPI seed loaders for ref, and separate InvocationRecord-format files for simulation corpus
**Alternatives:**
- File-based only — simple, declarative. But harder to construct test-specific scenarios programmatically.
- Programmatic only — maximum flexibility but no declarative format for non-developers.
- Single format for both (original D8) — InvocationRecord format can't represent domain seed data naturally (see D2 revision).
**Rationale:** Domain-typed seed files (e.g. bank-seed.yaml with accounts/transactions sections) are natural for ref seeding. InvocationRecord-format files are natural for simulation strategies. Each format is optimized for its consumer.
**Trade-offs:** Two file formats to maintain; seed file consistency between ref and simulation must be ensured
**Sources:** R1-09 (format mismatch), CorpusLoader (platform repo), D2 revision
**Exploration:** quick → revised after decision review
**Status:** revised
**Depends on:** D2 (shared seed format)

## D9: Interpretive capability classification

**Choice:** No annotation metadata — strategy configuration determines what's simulated. Document which capabilities are interpretive as guidance for strategy authors in the spec and SPI javadoc, but don't encode it in @SimulationEligible.
**Alternatives:**
- SPI-level attribute (original D9) — interpretiveCapabilities on @SimulationEligible. Unenforceable subset constraint; typos silently break classification; whether something is interpretive depends on deployment context, not SPI contract.
- Ref-level annotation — requires SPI and ref to agree on classification.
- Runtime config — SimulationConfig declares interpretive capabilities. Maximum flexibility but no compile-time visibility.
**Rationale:** Strategy configuration already IS the classification. If a SimulationStrategy exists for a capability, simulation handles it; if not, ref handles it. Annotating this redundantly in the SPI adds no runtime information. Whether geocoding is interpretive depends on the backend (real API vs fixed lookup), not the interface contract.
**Trade-offs:** No compile-time documentation of which capabilities benefit from simulation — this lives in spec/javadoc instead
**Sources:** R1-06 (decision review), existing SimulationStrategy resolution model
**Exploration:** quick → revised after decision review
**Status:** revised
**Depends on:** D3 (existing decorator model), D6 (ref completeness)
