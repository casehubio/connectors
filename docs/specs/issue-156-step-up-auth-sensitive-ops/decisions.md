## D1: Annotation placement — SPI interface vs provider implementation

**Choice:** Annotate the SPI interface methods directly
**Alternatives:**
- Provider implementations — annotation per provider, ref implementations undecorated; but every new provider must remember to annotate, and confirmation becomes provider-specific rather than operation-specific
**Rationale:** Certain operations are inherently sensitive regardless of which provider executes them. Deleting a contact is sensitive whether backed by Google or the ref implementation. SPI-level annotation ensures all implementations inherit confirmation automatically — impossible to miss.
**Trade-offs:** Ref implementations in dev/test also get confirmation decoration, but this is a configuration concern (provide a test-mode OperationConfirmationProvider or no provider bean) rather than a code concern.
**Sources:** platform#561 (OperationConfirmation SPI), connectors#156 issue body
**Exploration:** quick
**Status:** captured

## D2: Module structure — decorators generated in-place per SPI module

**Choice:** Processor runs per-module at build time; each SPI module owns its own generated decorator
**Alternatives:**
- Separate aggregation module — centralised but adds a cross-cutting module and new dependency
- Processor runs in downstream apps — connectors does nothing beyond annotating; consumers' builds generate decorators
**Rationale:** Simplest dependency graph, no new module. Each SPI already compiles independently; adding an annotation and letting the processor run in that module's build keeps concerns local.
**Trade-offs:** Four modules each run the processor independently rather than one aggregated run. Negligible build cost.
**Sources:** @SimulationEligible precedent (per-SPI-module, not aggregated)
**Exploration:** quick
**Status:** captured

## D3: Summary templates — parameter-rich with domain context

**Choice:** Include domain-specific details from request parameters in summary text
**Alternatives:**
- Generic descriptions ("Initiate payment", "Delete file") — simpler but the human confirmer has less context to make an informed decision
**Rationale:** The confirmation prompt is the last line of defence before a sensitive operation executes. The human needs specific context (amount, destination, filename) to make an informed approve/deny decision, especially in AI-agent scenarios where the agent initiated the action.
**Trade-offs:** Summary templates couple to request object field names; if fields are renamed, the template breaks silently (resolved value becomes empty/null).
**Sources:** connectors#156 issue body (AI firewall concern), @RequiresConfirmation summary attribute with ${param} support
**Exploration:** quick
**Status:** captured

## D4: Testing strategy — annotation presence and generation verification

**Choice:** Unit tests assert annotations exist on the right methods; build verifies processor generates decorator classes
**Alternatives:**
- End-to-end with mock OperationConfirmationProvider — more confidence but tests platform infrastructure, not connectors' annotation work
**Rationale:** Connectors' responsibility is placing the annotation. The confirmation flow (challenge, verify, deny) is platform infrastructure tested in the platform repo. Testing annotation presence and decorator generation is sufficient and stays within scope.
**Trade-offs:** No runtime confirmation flow coverage in this repo; relies on platform's own test coverage of the decorator mechanism.
**Sources:** connectors key rule ("pure delivery infrastructure"), platform#561 test coverage
**Exploration:** quick
**Status:** captured
