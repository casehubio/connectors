# @SimulationEligible on CalendarPlatform and ChatPlatform

Issue: casehubio/connectors#105
Date: 2026-09-20
Decisions: [decisions.md](decisions.md)

## Problem

CalendarPlatform and ChatPlatform predate the simulation framework. They use
hand-written ref modules (calendar-ref, chat-ref) for in-memory testing instead
of the `@SimulationEligible` decorator pattern adopted by BankFeedPlatform and
EmailPlatform.

CalendarPlatform is a flat SPI (direct methods) — straightforward to annotate.
ChatPlatform is capability-based (methods return sub-interfaces like Messaging,
Discovery, Members) — the simulation framework's decorator generator does not
yet support recursive interception of capability accessor methods.

## Platform prerequisite — Generator enhancement

**Separate issue in casehubio/platform — to be filed as first implementation
step.** Issue #105 depends on this landing first. The platform issue must cover:
the `capabilities` attribute on `@SimulationEligible`, recursive wrapper
generation in `SimulationDecoratorProcessor`, and the `supports()` override.

### @SimulationEligible annotation change

Add a `capabilities` attribute (D5):

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SimulationEligible {
    String name() default "";
    String[] capabilities() default {};
}
```

When `capabilities` is empty (default), all methods get standard flat
interception — fully backward compatible with BankFeedPlatform and
EmailPlatform.

### Generator enhancement

`SimulationDecoratorProcessor` gains recursive wrapper generation for methods
listed in `capabilities` (D1):

1. For each method name in `capabilities`, find the matching method on the SPI
   interface via Jandex.
2. Generate a wrapper class implementing the return type's interface. Example:
   `SimulatedChatPlatform_Messaging implements Messaging`.
3. Each wrapper method uses the standard intercept-or-delegate pattern with
   dotted qualified names: `chat-platform.messaging.send`.
4. The top-level decorator's capability method returns the wrapper, passing
   `delegate.capability()` as the inner delegate.
5. Generate QN constants for capability methods:
   `MESSAGING_SEND = "chat-platform.messaging.send"`.

### supports() override (D7)

The generator emits a `supports()` override on the top-level decorator. For each
capability class listed in `capabilities`, it checks whether any of that
capability's methods have active simulation strategies. If so, `supports()`
returns true for that capability class, unioned with whatever the delegate
reports natively. This ensures semantic coherence: if the simulation provides
Messaging behavior, `supports(Messaging.class)` reports it as available.

### Generated code structure

For `@SimulationEligible(name = "chat-platform", capabilities = {"messaging"})`:

```
SimulatedChatPlatform (CDI @Decorator)
├── id()           → standard flat interception (strategy or delegate)
├── supports()     → override: union delegate + simulation-active capabilities
├── messaging()    → returns SimulatedChatPlatform_Messaging wrapper
├── threading()    → standard flat interception (not in capabilities)
└── ...

SimulatedChatPlatform_Messaging (inner class, not CDI-managed)
├── send()         → intercept-or-delegate, QN = "chat-platform.messaging.send"

ChatPlatformQN (constants)
├── ID             = "chat-platform.id"
├── SUPPORTS       = "chat-platform.supports"
├── MESSAGING_SEND = "chat-platform.messaging.send"
├── THREADING_REPLY = "chat-platform.threading.reply"  (from interface scan)
└── ...
```

Methods NOT in `capabilities` (e.g. `threading()` if not listed) get standard
flat interception — the strategy lookup uses `chat-platform.threading` as the
QN. If a strategy is configured for that QN, it must return a `Threading`
object. If not, it delegates. This is the fallback for capabilities not yet
ready for recursive wrapping.

## CalendarPlatform — flat SPI (D8)

### Annotation

```java
@SimulationEligible(name = "calendar-platform")
public interface CalendarPlatform { ... }
```

No `capabilities` attribute — all 7 methods get flat interception.

### NoOp fallback

New `NoOpCalendarPlatform` in `calendar-spi`:

```java
@DefaultBean
@ApplicationScoped
public class NoOpCalendarPlatform implements CalendarPlatform {
    public String id() { return "none"; }
    public List<CalendarInfo> listCalendars() { return List.of(); }
    public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
        return List.of();
    }
    public CalendarEvent getEvent(String calendarId, String eventId) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }
    public CalendarEvent createEvent(String calendarId, EventDetails details) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }
    public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }
    public void deleteEvent(String calendarId, String eventId) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }
}
```

### pom.xml dependencies

Add to `calendar-spi/pom.xml`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-api</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
</dependency>
```

`jackson-annotations` is required for `@JsonTypeInfo`/`@JsonSubTypes` on
`EventTiming`. No version tag needed — managed by the Quarkus BOM. This is an
annotation-only artifact with no transitive runtime dependencies.

### Simulation config

`calendar-spi/src/main/resources/simulation/calendar/simulation.yaml`:

```yaml
default-tenancy-id: household

methods:
  calendar-platform.id:
    strategy: constant
    corpus:
      - input: null
        output: "sim"

  calendar-platform.listCalendars:
    strategy: seq
    corpus-files:
      - classpath:simulation/calendar/calendars-corpus.yaml

  calendar-platform.listEvents:
    strategy: seq
    exhaustion-policy: WRAP
    corpus-files:
      - classpath:simulation/calendar/events-corpus.yaml

  calendar-platform.getEvent:
    strategy: key
    key-extractor: "field:eventId"
    corpus-files:
      - classpath:simulation/calendar/events-corpus.yaml

  calendar-platform.deleteEvent:
    strategy: constant
    corpus:
      - input: null
        output: null
```

### Corpus YAML

Two files:

**calendars-corpus.yaml** — 2 calendars (work, personal):

```yaml
calendar-platform.listCalendars:
  - tenancy-id: household
    input: null
    output:
      - id: cal-work-001
        summary: "Work Calendar"
        description: "Team meetings and deadlines"
        primary: true
      - id: cal-personal-001
        summary: "Personal Calendar"
        description: "Family events and appointments"
        primary: false
```

**events-corpus.yaml** — 5 events across both calendars:

```yaml
calendar-platform.listEvents:
  - tenancy-id: household
    input: null
    output:
      - id: evt-standup-001
        calendarId: cal-work-001
        summary: "Daily Standup"
        description: "Team sync"
        location: "Room 3A"
        timing:
          "@type": "Timed"
          start: "2026-09-20T09:00:00Z"
          end: "2026-09-20T09:15:00Z"
          timeZone: "Europe/London"
        attendees: ["alice@example.com", "bob@example.com"]
        recurringEventId: "recur-standup"
      - id: evt-review-002
        calendarId: cal-work-001
        summary: "Project Review"
        description: "Q3 progress review"
        location: "Board Room"
        timing:
          "@type": "Timed"
          start: "2026-09-20T14:00:00Z"
          end: "2026-09-20T15:00:00Z"
          timeZone: "Europe/London"
        attendees: ["alice@example.com", "carol@example.com"]
        recurringEventId: null
      - id: evt-1on1-003
        calendarId: cal-work-001
        summary: "Weekly 1:1"
        description: "Manager sync"
        location: null
        timing:
          "@type": "Timed"
          start: "2026-09-20T11:00:00Z"
          end: "2026-09-20T11:30:00Z"
          timeZone: "Europe/London"
        attendees: ["alice@example.com"]
        recurringEventId: "recur-1on1"
      - id: evt-dentist-004
        calendarId: cal-personal-001
        summary: "Dentist Appointment"
        description: "Regular checkup"
        location: "123 High Street"
        timing:
          "@type": "Timed"
          start: "2026-09-21T10:00:00Z"
          end: "2026-09-21T10:30:00Z"
          timeZone: "Europe/London"
        attendees: []
        recurringEventId: null
      - id: evt-school-005
        calendarId: cal-personal-001
        summary: "School Sports Day"
        description: "Annual sports day"
        location: "School Field"
        timing:
          "@type": "AllDay"
          start: "2026-09-22"
          end: "2026-09-22"
        attendees: []
        recurringEventId: null

calendar-platform.getEvent:
  - tenancy-id: household
    key: evt-standup-001
    input: evt-standup-001
    output:
      id: evt-standup-001
      calendarId: cal-work-001
      summary: "Daily Standup"
      description: "Team sync"
      location: "Room 3A"
      timing:
        "@type": "Timed"
        start: "2026-09-20T09:00:00Z"
        end: "2026-09-20T09:15:00Z"
        timeZone: "Europe/London"
      attendees: ["alice@example.com", "bob@example.com"]
      recurringEventId: "recur-standup"
  - tenancy-id: household
    key: evt-review-002
    input: evt-review-002
    output:
      id: evt-review-002
      calendarId: cal-work-001
      summary: "Project Review"
      description: "Q3 progress review"
      location: "Board Room"
      timing:
        "@type": "Timed"
        start: "2026-09-20T14:00:00Z"
        end: "2026-09-20T15:00:00Z"
        timeZone: "Europe/London"
      attendees: ["alice@example.com", "carol@example.com"]
      recurringEventId: null
```

### EventTiming sealed interface — Jackson prerequisite

`EventTiming` is a sealed interface with two variants: `Timed(Instant start,
Instant end, ZoneId timeZone)` and `AllDay(LocalDate start, LocalDate end)`.
It currently has no Jackson annotations. The corpus YAML uses `@type` as a
type discriminator (shown above).

**Prerequisite:** Add Jackson type annotations to `EventTiming`:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EventTiming.Timed.class, name = "Timed"),
    @JsonSubTypes.Type(value = EventTiming.AllDay.class, name = "AllDay")
})
public sealed interface EventTiming { ... }
```

This is a general correctness improvement — any Jackson serialization of
`CalendarEvent` (REST responses, event payloads) would also need it. The
simulation framework's YAML corpus loader uses Jackson for deserialization,
so the annotations are required for corpus loading to produce typed objects.

### Parameter-agnostic strategy note

The `seq` strategy for `listEvents` returns corpus entries in order regardless
of `calendarId`, `from`, and `to` parameters. This is intentional: corpus-driven
simulation provides predictable data, not parameter-faithful filtering. Scenarios
that need calendar-scoped or time-filtered results should delegate to the ref
module instead. The `key` strategy for `getEvent` uses `field:eventId` which
selects the `eventId` parameter by name via Jandex parameter metadata — the same
mechanism used by bank-feed's `field:transactionId` for `getTransaction(String
accountId, String transactionId)`.

### CRUD coherence note

CalendarPlatform is a CRUD SPI. The default simulation config only configures
**read methods** (`listCalendars`, `listEvents`, `getEvent`) and the identity
method (`id`). Write methods (`createEvent`, `updateEvent`) are NOT configured —
they delegate to the ref module or NoOp. `deleteEvent` uses `constant` with
`output: null` because it's a void method where delegation to NoOp would throw
`UnsupportedOperationException`.

Scenarios needing create→list state coherence should NOT configure corpus for the
list methods — let them delegate to calendar-ref. Scenario authors choose which
methods to override per scenario.

### calendar-ref stays

The ref module provides stateful in-memory CRUD via `InMemoryCalendarBackend`.
It remains as the implementation for test and interactive modes. The simulation
decorator composes on top: `Caller → Decorator → [Ref | NoOp]`.

## ChatPlatform — capability-based SPI

### Annotation

```java
@SimulationEligible(name = "chat-platform",
    capabilities = {"messaging", "threading", "discovery", "reactions",
                    "presence", "members", "channelManagement",
                    "memberManagement", "messageHistory"})
public interface ChatPlatform { ... }
```

All 9 capability accessor methods are listed. The generator produces recursive
wrappers for each.

### NoOp fallback

New `NoOpChatPlatform` in `chat-spi`:

```java
@DefaultBean
@ApplicationScoped
public class NoOpChatPlatform implements ChatPlatform {
    public String id() { return "none"; }
    public Messaging messaging() {
        return (channel, content) -> SendResult.failure("No chat provider configured");
    }
    public Threading threading() {
        return new ChannelFallbackThreading(messaging());
    }
    public Discovery discovery() { return new EmptyDiscovery(); }
    public Reactions reactions() { return new NoOpReactions(); }
    public Presence presence() { return new UnknownPresence(); }
    public Members members() { return new EmptyMembers(); }
    public ChannelManagement channelManagement() {
        return new NoOpChannelManagement();
    }
    public MemberManagement memberManagement() {
        return new NoOpMemberManagement();
    }
    public MessageHistory messageHistory() { return new EmptyMessageHistory(); }
    public boolean supports(Class<?> capability) { return false; }
}
```

### pom.xml dependency

Add to `chat-spi/pom.xml`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Simulation config

`chat-spi/src/main/resources/simulation/chat/simulation.yaml`:

```yaml
default-tenancy-id: household

methods:
  chat-platform.id:
    strategy: constant
    corpus:
      - input: null
        output: "sim"

  chat-platform.messaging.send:
    strategy: seq
    corpus-files:
      - classpath:simulation/chat/messages-corpus.yaml

  chat-platform.discovery.listChannels:
    strategy: seq
    corpus-files:
      - classpath:simulation/chat/channels-corpus.yaml

  chat-platform.members.list:
    strategy: seq
    corpus-files:
      - classpath:simulation/chat/members-corpus.yaml
```

Only the 3 MVP capabilities (Messaging, Discovery, Members) have strategies.
The remaining 6 capabilities' wrappers delegate to the underlying
implementation (ref or NoOp) for all their methods.

### Corpus YAML

Three files:

**channels-corpus.yaml** — 3 channels:

```yaml
chat-platform.discovery.listChannels:
  - tenancy-id: household
    input: null
    output:
      - ref:
          id: ch-general
        name: "#general"
        topic: "General discussion"
        description: "General discussion channel"
        isPrivate: false
        memberCount: 12
      - ref:
          id: ch-support
        name: "#support"
        topic: "Customer support queue"
        description: "Customer support channel"
        isPrivate: false
        memberCount: 5
      - ref:
          id: ch-engineering
        name: "#engineering"
        topic: "Engineering team"
        description: "Engineering team channel"
        isPrivate: true
        memberCount: 8
```

**members-corpus.yaml** — 4 members:

```yaml
chat-platform.members.list:
  - tenancy-id: household
    input: null
    output:
      - ref:
          id: user-001
        displayName: "alice"
      - ref:
          id: user-002
        displayName: "bob"
      - ref:
          id: user-003
        displayName: "carol"
      - ref:
          id: user-004
        displayName: "dan"
```

**messages-corpus.yaml** — send results:

```yaml
chat-platform.messaging.send:
  - tenancy-id: household
    input: null
    output:
      ok: true
      messageRef:
        channel:
          id: "ch-general"
        messageId: "msg-sim-001"
      timestamp: "2026-09-20T10:00:00Z"
      error: null
  - tenancy-id: household
    input: null
    output:
      ok: true
      messageRef:
        channel:
          id: "ch-support"
        messageId: "msg-sim-002"
      timestamp: "2026-09-20T10:01:00Z"
      error: null
```

### chat-ref stays

Same as CalendarPlatform — the ref module provides stateful in-memory behavior
via `InMemoryChatBackend`. The simulation decorator composes on top.

## Hybrid layering (D3)

The CDI decorator stack for both SPIs:

```
Caller → SimulatedDecorator (generated @Decorator, priority APPLICATION+200)
       → [Ref (if on classpath) | NoOp (@DefaultBean, if not)]
```

Three composable modes:

| Mode | Overlay | Delegate | Use case |
|------|---------|----------|----------|
| Scenario | Active — strategies intercept | Ref or NoOp | Predictable corpus data for scenario paths |
| Test | Inactive | Ref seeded via `Simulation.forTest()` | Stateful integration testing |
| Interactive | Inactive | Ref seeded with corpus data | Realistic starting state, real CRUD behavior |

The decorator always records to the simulation journal, even when delegating.
This gives observability across all modes.

## Validation example (D4)

### Scenario: team-collaboration

Location: `graphql/src/test/resources/scenarios/team-collaboration/`

A scenario exercising both CalendarPlatform and ChatPlatform simulation.
Narrative: a team scheduling a meeting via chat and adding it to the calendar.

### Integration test

`graphql/src/test/java/.../TeamCollaborationSimulationTest.java`

`@QuarkusTest` with simulation test profile. Assertions:

1. **Flat SPI corpus** — `calendarPlatform.listCalendars()` returns corpus
   calendars, `calendarPlatform.getEvent("cal-work-001", "evt-standup-001")`
   returns the corpus event
2. **Capability corpus** — `chatPlatform.messaging().send(channel, content)`
   returns corpus `SendResult` with `result.ok() == true` and
   `result.messageRef().messageId() == "msg-sim-001"`,
   `chatPlatform.discovery().listChannels()` returns 3 corpus channels,
   `chatPlatform.members().list(channel)` returns 4 corpus members
3. **Hybrid delegation** — `chatPlatform.reactions().add(msg, ":+1:")` has no
   strategy → delegates to ref/NoOp. No exception (NoOp is no-op). Journal
   entry exists for `chat-platform.reactions.add` with `simulated=false`
   (delegation recorded, not simulated).
4. **Journal** — journal entries exist for `calendar-platform.listCalendars`
   (flat), `chat-platform.messaging.send` (dotted capability), both marked
   `simulated=true`
5. **QN constants** — `CalendarPlatformQN.LISTCALENDARS` equals
   `"calendar-platform.listCalendars"`,
   `ChatPlatformQN.MESSAGING_SEND` equals `"chat-platform.messaging.send"`
6. **supports() coherence** — `chatPlatform.supports(Messaging.class)` returns
   `true` (simulation provides it), `chatPlatform.supports(Reactions.class)`
   returns `false` (no simulation strategy, NoOp doesn't natively support it)

## Acceptance criteria

- [ ] Platform issue filed and landed: `capabilities` attribute on
  `@SimulationEligible`, recursive wrapper generation in
  `SimulationDecoratorProcessor`, `supports()` override
- [ ] Jackson `@JsonTypeInfo`/`@JsonSubTypes` on `EventTiming` sealed interface
- [ ] `@SimulationEligible` on CalendarPlatform (flat)
- [ ] `@SimulationEligible` on ChatPlatform (capability-based, all 9 listed)
- [ ] `NoOpCalendarPlatform` (`@DefaultBean`)
- [ ] `NoOpChatPlatform` (`@DefaultBean`, uses existing degraded impls)
- [ ] `simulation-api` dependency in both SPI pom.xml files
- [ ] Corpus YAML for CalendarPlatform (calendars, events)
- [ ] Corpus YAML for ChatPlatform MVP (channels, members, messages)
- [ ] Simulation config for both SPIs
- [ ] Integration test validating flat, capability, hybrid, journal, QN, supports
- [ ] ARC42STORIES.MD §5 module table updated: `calendar-spi` and `chat-spi`
  dependencies include `simulation-api`
- [ ] Document findings on capability-based SPI simulation (this spec + ADR update)

## References

- `bank-spi/spi/BankFeedPlatform.java:13` — reference `@SimulationEligible` usage
- `bank-spi/NoOpBankFeedPlatform.java` — reference NoOp pattern
- `bank-spi/src/main/resources/simulation/bank-feed/simulation.yaml` — reference config
- `platform/simulation-generator/SimulationDecoratorProcessor.java` — generator source
- `platform/simulation-api/SimulationEligible.java` — annotation source
- `docs/adr/0011-simulation-framework-for-connector-spis.md` — ADR
- `docs/specs/issue-94-bankfeed-email-spis/decisions.md` — D1 (zero hand-written sim code)
- `chat-spi/spi/ChatPlatform.java` — capability-based SPI
- `chat-spi/spi/DefaultChatPlatform.java` — record impl with supports()
- `chat-spi/degraded/` — degraded capability implementations
- `graphql/src/test/resources/scenarios/household-finance/` — existing scenario pattern
