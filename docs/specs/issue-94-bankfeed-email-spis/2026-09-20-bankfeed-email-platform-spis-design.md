# BankFeedPlatform and EmailPlatform SPIs — Design Spec

> **Issue:** casehubio/connectors#94
> **Epic:** casehubio/parent#408 (Cross-platform Scenario Engine)
> **Date:** 2026-09-20
> **Status:** Draft

## Overview

Two new connector SPIs for financial and email integration. Both follow the
CalendarPlatform structural pattern (flat interface, platform service registry)
and are the first connector SPIs to integrate with the platform simulation
framework via `@SimulationEligible` for scenario-driven simulation, testing,
and development without live APIs.

Existing connector SPIs (CalendarPlatform, ChatPlatform) predate the simulation
framework and do not use `@SimulationEligible`. These SPIs pioneer the pattern
for connectors; existing SPIs can adopt it later.

No demo or ref modules. Demo SPI implementations are demand-driven in consuming
applications, not shared infrastructure in the connectors repo (per connectors#93
closure — the demo-spi-convention describes a pattern for apps, not a module
mandate). The simulation framework's generated CDI decorators serve the test and
scenario paths. Two new Maven modules total: `bank-spi` and `email-spi`.

**Issue #94 AC update needed:** The acceptance criteria for #94 reference
demo impls and injection endpoints. These were written before #93 reinterpreted
the convention. The ACs should be updated to reflect the simulation-first
approach: `@SimulationEligible` annotation, simulation test fixtures, and
scenario corpus support replace the demo module pattern.

## Modules

| Module | Artifact | Package |
|--------|----------|---------|
| `bank-spi` | `casehub-connectors-bank-spi` | `io.casehub.connectors.bank.spi` (interface), `io.casehub.connectors.bank.model` (records), `io.casehub.connectors.bank` (service, beans) |
| `email-spi` | `casehub-connectors-email-spi` | `io.casehub.connectors.email.spi` (interface), `io.casehub.connectors.email.model` (records), `io.casehub.connectors.email` (service, beans) |

Both modules depend on `simulation-api` (for `@SimulationEligible`) and
`connectors-api` (for shared `Page<T>` and `PageRequest` pagination types).
Neither depends on any real provider library — they are pure SPI definitions.

## BankFeedPlatform SPI

### Interface

```java
@SimulationEligible(name = "bank-feed-platform")
public interface BankFeedPlatform {

    String id();

    List<AccountInfo> listAccounts();

    AccountBalance balance(String accountId);

    Page<Transaction> listTransactions(String accountId,
                                       Instant from, Instant to,
                                       PageRequest pagination);

    Transaction getTransaction(String accountId, String transactionId);
}
```

Flat interface following CalendarPlatform's structural pattern (single
interface, `id()` method, service registry). All methods are core to a bank
feed provider — no optional capabilities. The `@SimulationEligible`
annotation triggers decorator generation at build time — this is new for
connector SPIs (CalendarPlatform and ChatPlatform do not have it).

Pagination via `Page<Transaction>` and `PageRequest` is a deliberate
departure from CalendarPlatform's `List<CalendarEvent>` returns. Financial
data volumes require cursor-based pagination — accounts can have thousands
of transactions, unlike calendar events where the time-range filter
naturally bounds the result set.

**Error contract for lookup methods:** `balance()` and `getTransaction()`
throw `java.util.NoSuchElementException` when the target does not exist
(unknown account or unknown transaction). Transport errors surface as
unchecked provider-specific exceptions. Callers obtain valid IDs from
list operations (`listAccounts()`, `listTransactions()`) before calling
lookup methods. The NoOp fallback throws `UnsupportedOperationException`
("No bank feed provider configured") — semantically different, since the
NoOp represents "no provider" rather than "entity not found."

### Model records

```java
public record AccountInfo(String id, String name,
                          AccountType type, String currency) {}

public enum AccountType { CURRENT, SAVINGS, CREDIT_CARD, LOAN, MORTGAGE, OTHER }

public record AccountBalance(String accountId,
                             BigDecimal available, BigDecimal current,
                             String currency, Instant asOf) {}

public record Transaction(String id, String accountId,
                          BigDecimal amount, TransactionDirection direction,
                          String currency,
                          String description, String merchantName,
                          String category,
                          LocalDate date, TransactionStatus status) {}

public enum TransactionDirection { DEBIT, CREDIT }

public enum TransactionStatus { PENDING, BOOKED }
```

`AccountBalance.available` is the balance minus pending holds and
authorisations — what the account holder can spend. `AccountBalance.current`
is the book/ledger balance reflecting all settled transactions. These can
differ significantly (e.g., a £100 card authorisation reduces `available`
but not `current` until settlement).

`Transaction.amount` is always positive; `TransactionDirection` indicates
debit or credit. Provider APIs vary (some use signed amounts, others use
separate direction fields) — the SPI normalises to explicit direction,
consistent with Open Banking UK, PSD2 APIs, and most banking SDKs.

`BigDecimal` for monetary amounts — never floating point.

**Nullable fields — bank model records:**
- `Transaction.merchantName` — nullable; bank transfers, ATM withdrawals,
  and standing orders have no merchant
- `Transaction.category` — nullable; providers that natively categorise
  populate it, others leave it null. Categorisation beyond what the
  provider offers is a consumer concern (D2)
- All other fields on `AccountInfo`, `AccountBalance`, and `Transaction`
  are non-null

### Shared pagination types (in `connectors-api`)

```java
package io.casehub.connectors;

public record PageRequest(String cursor, int pageSize) {
    public static PageRequest first(int pageSize) {
        return new PageRequest(null, pageSize);
    }
}

public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {
    public static <T> Page<T> of(List<T> items) {
        return new Page<>(items, null, false);
    }
}
```

`Page` and `PageRequest` live in `connectors-api`
(`io.casehub.connectors`) alongside `InboundMessage`, `Attachment`, and
other shared connector primitives. Both `BankPlatform` and
`EmailPlatform` use them — placing them in a domain-specific package
would create a cross-SPI dependency.

The simulation framework's corpus returns all items in a single page —
pagination adds no complexity to the simulation path (D8).

### BankFeedPlatformService

```java
public class BankFeedPlatformService {

    private final Map<String, BankFeedPlatform> registry;

    public BankFeedPlatformService(List<BankFeedPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(BankFeedPlatform::id, identity(),
                        (a, b) -> { throw new IllegalStateException(
                                "Duplicate bank feed platform id: '" + a.id() + "'"); }));
    }

    public BankFeedPlatform platform(String id) { /* same as CalendarPlatformService */ }
    public boolean supports(String id) { return registry.containsKey(id); }
    public Set<String> ids() { return Set.copyOf(registry.keySet()); }
}
```

### DefaultBean fallback

```java
@DefaultBean
@ApplicationScoped
public class NoOpBankFeedPlatform implements BankFeedPlatform {
    @Override public String id() { return "none"; }
    @Override public List<AccountInfo> listAccounts() { return List.of(); }
    @Override public AccountBalance balance(String accountId) {
        throw new UnsupportedOperationException("No bank feed provider configured");
    }
    @Override public Page<Transaction> listTransactions(String accountId,
            Instant from, Instant to, PageRequest pagination) {
        return Page.of(List.of());
    }
    @Override public Transaction getTransaction(String accountId, String transactionId) {
        throw new UnsupportedOperationException("No bank feed provider configured");
    }
}
```

The no-op returns empty collections for list operations and throws for
single-item lookups. When simulation is active, the generated decorator
intercepts before the no-op is reached.

**Registry interaction in simulation mode:** The generated decorator
intercepts ALL methods including `id()` (verified in
`SimulationDecoratorProcessor.generateSimulatedMethod()` — no exclusion
of any non-synthetic method). In simulation-only deployments (no real
provider on the classpath), configure a strategy for `bank-feed-platform.id`
that returns the expected provider identifier (e.g., `"sim"`). The decorator
intercepts `id()` before the NoOp's `"none"` is returned, so the service
registry resolves `platform("sim")` to the decorated NoOp. Without this
strategy, the registry contains only `"none"` and `platform("truelayer")`
throws `IllegalArgumentException`.

This applies equally to `EmailPlatformService` with `email-platform.id`.

## EmailPlatform SPI

### Interface

```java
@SimulationEligible(name = "email-platform")
public interface EmailPlatform {

    String id();

    List<Mailbox> listMailboxes();

    Page<EmailSummary> listMessages(String mailboxId,
                                    Instant from, Instant to,
                                    PageRequest pagination);

    EmailMessage getMessage(String mailboxId, String messageId);

    byte[] getAttachmentContent(String mailboxId, String messageId,
                                String attachmentId);
}
```

Flat interface complementing the existing EmailConnector (outbound) and
EmailInboundConnector (push inbound). EmailPlatform is query/read only (D3).

Attachment content is retrieved separately via `getAttachmentContent()`
rather than inline in `EmailMessage`. This avoids fetching potentially
large attachment content on every `getMessage()` call — consumers request
content on demand. The `byte[]` return type is consistent with the
platform's `Attachment` record in `connectors-api`.

**Error contract for lookup methods:** `getMessage()` and
`getAttachmentContent()` throw `java.util.NoSuchElementException` when
the target does not exist (unknown mailbox, unknown message, or unknown
attachment id). This applies to all single-entity lookup methods on
both SPIs — see §BankFeedPlatform SPI for the equivalent contract on
`balance()` and `getTransaction()`. Callers obtain valid IDs from list
operations before calling lookup methods. Transport errors surface as
unchecked provider-specific exceptions, distinct from the not-found
`NoSuchElementException`.

Note: The NoOp fallback throws `UnsupportedOperationException` ("No
provider configured"), which is semantically different — the NoOp
represents "no provider" rather than "entity not found."

### Model records

```java
public record Mailbox(String id, String name, int unreadCount) {}

public record EmailSummary(String id, String mailboxId,
                           String messageId,
                           String from, String subject,
                           Instant receivedAt, boolean read) {}

public record EmailMessage(String id, String mailboxId,
                           String messageId,
                           String from, List<String> to, List<String> cc,
                           String subject, String bodyText, String bodyHtml,
                           Instant receivedAt, boolean read,
                           List<EmailAttachment> attachments) {}

public record EmailAttachment(String id, String filename,
                              String contentType, long size) {}
```

`EmailMessage.messageId` is the RFC 2822 `Message-ID` header value — the
globally unique identifier assigned by the originating mail system. This
is the correlation identity between `EmailPlatform` queries and
`EmailInboundConnector` push events (see §Consistency model).

`EmailAttachment.id` is a provider-assigned unique part identifier
(IMAP section number, Gmail attachment ID, JMAP blob ID). It is always
non-null and unique within a message. `getAttachmentContent()` uses this
`id` — not `filename` — as the lookup key, because filenames can be
null (MIME does not require one) and duplicated (common with inline
images like `image001.png`).

`EmailAttachment` carries metadata only — content is retrieved via
`EmailPlatform.getAttachmentContent(mailboxId, messageId, attachmentId)`.

**Nullable fields — email model records:**
- `EmailMessage.bodyHtml` — nullable; plain-text-only emails have no
  HTML body
- `EmailMessage.bodyText` — nullable; HTML-only emails may have no
  plain-text body (at least one of `bodyText`/`bodyHtml` is non-null)
- `EmailAttachment.filename` — nullable; MIME does not require a filename
  (consistent with `Attachment` in `connectors-api`)
- `EmailSummary.messageId` and `EmailMessage.messageId` — nullable; some
  provider APIs may not expose the RFC 2822 Message-ID in all responses
- `EmailAttachment.id` — non-null; provider-assigned unique part identifier
- All other fields on `Mailbox`, `EmailSummary`, `EmailMessage`,
  `EmailAttachment` are non-null

### EmailPlatformService

Same pattern as `BankPlatformService` and `CalendarPlatformService`.

### DefaultBean fallback

Same pattern as `NoOpBankPlatform` — empty lists for list operations,
throws for single-item lookups.

## Simulation integration

### How it works

1. `@SimulationEligible` on the SPI interface triggers
   `SimulationDecoratorProcessor` at build time
2. The processor generates `SimulatedBankFeedPlatform` and
   `SimulatedEmailPlatform` CDI `@Decorator` classes
3. Each decorator method checks `SimulationRuntime.strategyFor(qualifiedName)`:
   - If a strategy is configured and can resolve → return simulated data
   - Otherwise → delegate to the real impl (or `@DefaultBean` no-op)
4. Optionally captures invocations to the `InvocationJournal` for verification

### Qualified names

The `@SimulationEligible(name = "...")` value determines the prefix.
Method names are appended automatically by the generator.

| Qualified name | Method |
|---------------|--------|
| `bank-feed-platform.id` | `BankFeedPlatform.id()` |
| `bank-feed-platform.listAccounts` | `BankFeedPlatform.listAccounts()` |
| `bank-feed-platform.balance` | `BankFeedPlatform.balance(accountId)` |
| `bank-feed-platform.listTransactions` | `BankFeedPlatform.listTransactions(...)` |
| `bank-feed-platform.getTransaction` | `BankFeedPlatform.getTransaction(...)` |
| `email-platform.id` | `EmailPlatform.id()` |
| `email-platform.listMailboxes` | `EmailPlatform.listMailboxes()` |
| `email-platform.listMessages` | `EmailPlatform.listMessages(...)` |
| `email-platform.getMessage` | `EmailPlatform.getMessage(...)` |
| `email-platform.getAttachmentContent` | `EmailPlatform.getAttachmentContent(...)` |

These qualified names are what scenario authors and test writers use in
YAML configuration and `Simulation.forTest()` calls.

### Test fixture example (works now — Java API)

```java
var sim = Simulation.forTest()
    .stub("bank-feed-platform.id", null, "sim")
    .stub("bank-feed-platform.listAccounts", null,
          List.of(new AccountInfo("acc-001", "Current Account",
                                  AccountType.CURRENT, "GBP")))
    .stub("bank-feed-platform.balance", "acc-001",
          new AccountBalance("acc-001", new BigDecimal("1250.00"),
                             new BigDecimal("1250.00"), "GBP", Instant.now()))
    .seed("bank-feed-platform.listTransactions", null,
          Page.of(List.of(sampleTransaction)))
    .build();
```

The `bank-feed-platform.id` stub ensures the decorated NoOp registers in
the service registry as `"sim"` rather than the NoOp's default `"none"`.
See §DefaultBean fallback for the registry interaction explanation.

### Overlay lifecycle

| Context | Overlay management |
|---------|-------------------|
| Scenario | `ScenarioOrchestrator.activateSimulation()` pushes overlay at start, pops at end |
| Test | `Simulation.forTest().build()` creates runtime; overlay pushed/popped per test |
| Dev (no real API) | Base simulation config in `application.properties` — always active |
| Production | No overlay — decorator passes through to real provider |

### Scenario YAML example (requires platform#370)

The following YAML configuration requires features tracked in
casehubio/platform#370: declarative key extractors, exhaustion policy,
and `YamlCorpusLoader`. **These features have not shipped yet.** Until
platform#370 lands, simulation configuration is Java-only (see test
fixture example above).

```yaml
simulation:
  strategies:
    bank-feed-platform.id: constant
    bank-feed-platform.listAccounts: sequential
    bank-feed-platform.balance: key-lookup
    bank-feed-platform.listTransactions: sequential
    bank-feed-platform.getTransaction: key-lookup
  constants:
    bank-feed-platform.id: "sim"
  key-extractor:
    bank-feed-platform.balance: accountId
    bank-feed-platform.getTransaction: transactionId
  exhaustion:
    bank-feed-platform.listTransactions: wrap
  corpus:
    - scenarios/bank-feed/household-finance.yaml
```

## Push events

Production push events (transaction notifications, inbound emails) use
the existing `WebhookInboundConnector` SPI. Platform SPIs stay query-only.

During scenarios, push events are simulated via CDI `Event<InboundMessage>`
fired by the scenario engine — the same mechanism real inbound connectors
use. The simulation overlay handles the query side; push events are
orthogonal (D5).

## Consistency model (EmailPlatform + EmailInboundConnector)

EmailPlatform queries and EmailInboundConnector push events may surface
the same message. This is by design — the two paths serve different
consumer patterns (poll vs push). No deduplication at the SPI level.
Consumers observing both must be idempotent (D10).

**Correlation identity:** The RFC 2822 `Message-ID` header is the shared
identity across both paths:
- `EmailMessage.messageId` — populated by `EmailPlatform.getMessage()`
- `InboundMessage.metadata["message-id"]` — already populated by
  `EmailInboundConnector.buildMetadata()` (see line 260 of
  `EmailInboundConnector.java`)

Consumers correlate by matching these values. The Message-ID is globally
unique per RFC 2822 §3.6.4 — assigned by the originating mail system.

**Limitation:** Correlation requires non-null `messageId` on both sides.
`EmailMessage.messageId` and `EmailSummary.messageId` are nullable (some
providers may not expose the RFC 2822 Message-ID). Messages with null
`messageId` cannot be deduplicated across poll and push paths — consumers
observing both must handle these as potentially duplicate and fall back
to heuristic matching (e.g., sender + subject + timestamp).

## Future concerns

### PSD2 consent management

Tracked in casehubio/connectors#TBD (to be filed: "PSD2 consent management
for BankFeedPlatform").

PSD2 mandates explicit user consent before accessing bank account data.
Consent has a lifecycle (initiated → active → expired → renewed/revoked).
This is not modelled in the current flat SPI — it will be added when a
real PSD2 provider (TrueLayer, Yapily) is implemented. Pre-release, adding
new methods or a consent interface is non-breaking (D7).

### Corpus data formats

The simulation framework's corpus loader is format-agnostic — `SimulationCorpus.seed()`
takes `List<InvocationRecord>` regardless of source. YAML is the current
format via `YamlCorpusLoader`. CSV (natural for financial data — bank exports,
accounting software) and recorded API responses (via `RecordedReplayStrategy`)
are future loader additions. The `DataRealism` progression
(PLACEHOLDER → DOMAIN_PLAUSIBLE → RECORDED_REAL) models this evolution.

### Simulation framework — capability-based SPI support

The current decorator generator intercepts at the direct SPI method level.
Capability-based SPIs (e.g., `ChatPlatform.messaging().send()`) would need
either recursive decorator generation or dynamic proxies for returned
interfaces. This is documented in casehubio/platform#370 as a future concern.
For our flat SPIs, the current generator works perfectly.

## Deliverables beyond code

### ARC42STORIES.MD update

Implementation includes updating `ARC42STORIES.MD` with:
- Two new layers: L12 (BankFeed Platform SPI) and L13 (Email Platform SPI)
- Stakeholder table additions: bank feed consumers (life, aml), email
  consumers (life, clinical)
- Module structure additions: `bank-spi`, `email-spi`
- New journey or chapter entries as appropriate

## References

- `calendar-spi/CalendarPlatform.java` — flat SPI pattern
- `calendar-spi/CalendarPlatformService.java` — registry pattern
- `platform/simulation-api/SimulationEligible.java` — decorator trigger
- `platform/simulation-generator/SimulationDecoratorProcessor.java` — code generator
- `platform/simulation-core/Simulation.java` — test facade
- `platform/simulation-core/SimulationRuntime.java` — overlay stack, strategy resolution
- `pages/scenario-runtime/ScenarioOrchestrator.java` — scenario integration
- `pages/scenario/SimulationSpec.java` — scenario YAML model
- `email/EmailConnector.java` — existing outbound (not replaced)
- `email-inbound/EmailInboundConnector.java` — existing push inbound (not replaced)
- casehubio/platform#370 — YAML parity gaps (key extractors, exhaustion, corpus loader)
- PP-20260609-e3a2bd — SPI id() naming convention
- PP-20260607-9794cb — shared HTTP client (future live impls)
- PP-20260609-0c3e24 — credential config ownership (future live impls)
