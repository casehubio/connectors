# 0011 — Simulation Framework for Connector SPI Testing and Scenarios

Date: 2026-09-20
Status: Accepted

## Context and Problem Statement

New connector SPIs (BankFeedPlatform, EmailPlatform) need simulation capability for testing, development without live APIs, and scenario-driven demos. The platform's Demo SPI Convention prescribed hand-written demo modules per SPI, but the platform simulation framework has since landed with build-time decorator generation, making per-SPI simulation modules unnecessary.

## Decision Drivers

* No live bank feed or email providers exist yet — simulation is the only runtime path
* The platform simulation framework generates CDI decorators from `@SimulationEligible` at build time
* The scenario engine (pages) integrates directly via `SimulationOverlay` push/pop
* Maintenance burden of hand-written sim classes that must track SPI changes

## Considered Options

* **Option A** — Single "sim" module per SPI (hand-written simulation class)
* **Option B** — Demo SPI Convention (ref + demo modules per SPI)
* **Option C** — Simulation framework via `@SimulationEligible` (no sim modules)

## Decision Outcome

Chosen option: **Option C**, because the simulation framework generates decorators automatically, eliminating hand-written simulation code and its maintenance burden. SPIs annotated with `@SimulationEligible` get full simulation support (strategy selection, corpus seeding, overlay stack, invocation journal) with zero per-SPI boilerplate.

### Positive Consequences

* No sim/demo/ref modules to maintain — decorator auto-generated at build time
* Simulation behavior is configurable at runtime via strategies and corpus data
* Scenario engine integration works automatically via overlay push/pop
* Test fixtures use `Simulation.forTest()` — clean, type-safe setup
* Same mechanism works for all future SPIs

### Negative Consequences / Tradeoffs

* Depends on the simulation framework being stable (recently landed)
* `@DefaultBean` no-op fallback is required as CDI delegate target — minor ceremony
* YAML parity gaps exist for key extractors and exhaustion policy (tracked in platform#370)

## Pros and Cons of the Options

### Option A — Single sim module per SPI

* ✅ Simple, self-contained, no framework dependency
* ✅ Full control over simulation behavior
* ❌ Must be manually updated when SPI changes
* ❌ Duplicates what the simulation framework generates

### Option B — Demo SPI Convention (ref + demo modules)

* ✅ Follows established platform convention
* ✅ Separates testing (ref) from scenarios (demo)
* ❌ Two modules per SPI — maximum maintenance overhead
* ❌ Convention predates the simulation framework and is partially superseded

### Option C — Simulation framework via @SimulationEligible

* ✅ Zero hand-written simulation code
* ✅ Auto-generated decorators track SPI changes automatically
* ✅ Per-method strategy granularity (sequential, key-lookup, random, replay)
* ✅ Overlay stack supports layered simulation contexts
* ❌ Framework dependency (recently landed, may evolve)
* ❌ YAML parity gaps for some configuration (platform#370)

## Links

* casehubio/connectors#94 — BankFeedPlatform and EmailPlatform SPIs
* casehubio/platform#370 — Simulation framework YAML parity gaps
* `platform/simulation-api/SimulationEligible.java` — annotation
* `platform/simulation-generator/SimulationDecoratorProcessor.java` — code generator
* `docs/specs/issue-94-bankfeed-email-spis/decisions.md` — D1 (full decision rationale)
