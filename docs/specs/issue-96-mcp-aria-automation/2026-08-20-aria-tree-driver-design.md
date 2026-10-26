# ARIA Tree Driver — Design Spec

**Issue:** casehubio/casehub-pages#324
**Branch:** issue-96-mcp-aria-automation
**Date:** 2026-08-20

## Problem

The scenario executor (#323) dispatches `delivery: graphql` steps via `GraphQLDispatcher` but `delivery: aria` steps are no-ops — `ScenarioExecutor.executeStep()` returns `ExecutionResult.ok(as.name(), Map.of())` without executing anything. The browser-side infrastructure is ready: `scenario-handler.ts` listens on `scenario:exec`, dispatches to ARIA executor functions (click, fill, select, expand, collapse, assert, wait, navigate), and returns `CommandResult` via the push wire WebSocket. The server-side dispatcher that sends commands and correlates responses is missing.

A secondary gap: `PushRequest.CommandResult` messages from the browser are parsed correctly by `PushRequest.parse()` but never routed to any Java consumer in production. `AriaCommandBridge.handleResult()` exists but is only called from tests.

## Solution

Three changes:

1. **CommandResult routing** — add a CDI event mechanism to the `push` module so `PushRequest.CommandResult` messages from the WebSocket are routed to Java consumers.
2. **AriaDispatcher** — new class in `scenario-runtime` that sends ARIA commands to the browser via `EventBroadcaster`, correlates responses via CDI-observed `CommandResult` events, handles the navigate/page-load protocol, and supports batching.
3. **ScenarioExecutor wiring** — replace the AriaStep no-op with delegation to `AriaDispatcher`.

## CommandResult Routing

### Problem

The push wire is architecturally unidirectional for events: `EventBroadcaster` sends server→browser. The browser sends responses (e.g., `{ op: "command-result", id, ok, error, result }`) back over the same WebSocket. The server-side WebSocket handler in the hosting app parses these via `PushRequest.parse()`, but `command-result` ops are not routed anywhere.

### Design

Add a `CommandResultHandler` functional interface to the `push` module:

```java
package io.casehub.pages.push;

@FunctionalInterface
public interface CommandResultHandler {
    void handle(PushRequest.CommandResult result);
}
```

In `push-runtime`, provide a default CDI implementation that fires a CDI event:

```java
@ApplicationScoped
@DefaultBean
public class CdiCommandResultHandler implements CommandResultHandler {

    @Inject Event<PushRequest.CommandResult> event;

    @Override
    public void handle(PushRequest.CommandResult result) {
        event.fire(result);
    }
}
```

The hosting app's WebSocket handler injects `CommandResultHandler` and calls `handle()` when it parses a `command-result` op. This is a one-line addition to the existing message dispatch switch.

### Why CDI event

- Follows the push module's design philosophy: functional interfaces for app-provided infrastructure (same pattern as `SessionSender`)
- Multiple consumers can observe without coordination (AriaDispatcher, AriaCommandBridge, future consumers)
- `@DefaultBean` means it works out of the box — apps can override if needed

### Hosting app change

The hosting app's WebSocket message handler needs:

```java
case PushRequest.CommandResult cr -> commandResultHandler.handle(cr);
```

This is a cross-repo concern. The pages library defines the interface and default implementation; the hosting app wires it into its WebSocket handler.

## AriaDispatcher

### Class: `AriaDispatcher`

**Module:** `backend/scenario-runtime`
**Package:** `io.casehub.pages.scenario.runtime`

```java
@ApplicationScoped
public class AriaDispatcher {

    private final EventBroadcaster broadcaster;
    private final Map<String, CompletableFuture<PushRequest.CommandResult>> pending
            = new ConcurrentHashMap<>();

    private static final String TOPIC = "scenario:exec";
    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final long READY_PROBE_INTERVAL_MS = 500;
}
```

### Dependencies

| Dependency | Purpose |
|-----------|---------|
| `EventBroadcaster` | Send commands to browser via push wire |
| CDI `@Observes PushRequest.CommandResult` | Receive correlated responses |

### CommandPayload record

AriaDispatcher defines its own payload record (cannot depend on `AriaCommandBridge.CommandPayload` in `backend/mcp`):

```java
record CommandPayload(String id, String action, Object target,
                      String value, Map<String, Object> state,
                      Integer timeout) {}
```

The browser expects `{ id, action, target?, value?, state?, timeout? }` — the Java type is irrelevant as long as `JsonWriter` serializes it to matching JSON.

### Core operations

**`send(AriaStep)`** — sends a single ARIA command and waits for the response:

1. Generate a UUID correlation ID
2. Create a `CompletableFuture<CommandResult>` and register in `pending` map
3. Construct a `CommandPayload(id, action, target, value, state, timeout)` from the `AriaStep`
4. Broadcast to `scenario:exec` via `EventBroadcaster`
5. Wait on the future with timeout (step timeout or default 30s)
6. On response: return `CommandResult`
7. On timeout: throw `AriaCommandException`
8. Always: remove from `pending` map in `finally`

**`sendBatch(List<AriaStep>)`** — sends multiple commands as a single push wire batch:

1. Generate one correlation ID for the batch
2. Construct `CommandPayload` for each step
3. Build individual event JSON strings via `PushMessage.event(TOPIC, payload)`
4. Send via `PushMessage.batch(msg1, msg2, ...)` through `EventBroadcaster`
5. Wait for one `CommandResult` (batch returns one result for all steps)
6. Return the result

**`onCommandResult(@Observes PushRequest.CommandResult result)`** — CDI observer:

1. Look up `result.id()` in `pending` map
2. If found, complete the future
3. If not found, ignore (result for a different consumer)

### Navigate protocol

When `send()` receives an `AriaStep` with `action = "navigate"`:

1. Send the navigate command normally — browser responds `ok` immediately (before the page transition)
2. After receiving `ok`, enter **page-load wait**:
   a. Send a `ready` probe: `CommandPayload(newId, "ready", null, null, null, null)`
   b. Wait for response with 500ms timeout
   c. If response received: new page is ready — return success
   d. If timeout: probe was sent into the void (browser is reloading) — repeat from (a)
   e. If total elapsed time exceeds step timeout (default 30s): throw `AriaCommandException` with page-load timeout
3. The outer loop (probe → wait → retry) continues until a response arrives or the overall timeout expires

No browser-side changes needed — `scenario-handler.ts` already handles `{ action: "ready" }` as a no-op that returns `{ ok: true }`.

### Batching rules

The ScenarioExecutor (not AriaDispatcher) decides what to batch. AriaDispatcher exposes both `send()` and `sendBatch()`. The executor groups consecutive AriaSteps into a batch when ALL of the following hold:

- Step has no explicit `name` (unnamed — no variable interpolation reference)
- Step action is NOT `navigate` (navigate triggers page transitions)
- Step action is NOT `wait` (wait is async — must return individually)
- Step action is NOT `assert` (assert failure must identify the specific step)

Consecutive steps meeting these criteria are grouped and dispatched via `sendBatch()`. All other steps use `send()` individually.

### Error handling

- `AriaCommandException` wraps all dispatch failures (timeout, browser error)
- `CommandResult.ok() == false` → throw `AriaCommandException` with the browser's error message
- Timeout → throw `AriaCommandException` with timeout details
- In `ScenarioExecutor`, the existing fail-fast model catches exceptions and returns `ExecutionResult.fail()`

### Test constructor

```java
AriaDispatcher(EventBroadcaster broadcaster, long timeoutMs)
```

Tests use the subclassing pattern from `GE-20260818-0dd695` (EventBroadcaster subclass for test interception) and call `onCommandResult()` directly to simulate browser responses. No CDI container needed for unit tests.

## ScenarioExecutor changes

Replace the AriaStep no-op:

```java
case ScenarioStep.AriaStep as -> executeAria(as, context);
```

The `executeAria` method:

1. Resolve variable interpolation in `target.name`, `value`, and `state` fields via `VariableContext`
2. Check if this step is part of a batchable sequence (look ahead in the step list)
3. If batchable: collect consecutive batchable steps, call `ariaDispatcher.sendBatch(steps)`, advance the step index
4. If not batchable: call `ariaDispatcher.send(step)`
5. If the step has a name and the `CommandResult` has a non-null `result` map: store in `VariableContext` for subsequent step interpolation
6. Return `ExecutionResult.ok(name, result)` or `ExecutionResult.fail(name, error)`

### Constructor change

`ScenarioExecutor` gains an `AriaDispatcher` parameter:

```java
public ScenarioExecutor(GraphQLDispatcher graphQLDispatcher,
                        AriaDispatcher ariaDispatcher)
```

### Batch lookahead

The executor's `execute()` method changes from a simple for-each to an indexed loop that can look ahead and consume consecutive batchable AriaSteps:

```java
int i = 0;
while (i < steps.size()) {
    ScenarioStep step = steps.get(i);
    if (step instanceof AriaStep as && isBatchable(as)) {
        List<AriaStep> batch = collectBatch(steps, i);
        // dispatch batch, advance i by batch.size()
    } else {
        // dispatch single step, advance i by 1
    }
}
```

## Module dependencies

### `push` module (new file)

- `CommandResultHandler.java` — functional interface

### `push-runtime` module (new file)

- `CdiCommandResultHandler.java` — `@DefaultBean` CDI implementation

### `scenario-runtime` module (new + modified files)

| File | Change |
|------|--------|
| `AriaDispatcher.java` | New — ARIA command dispatch with correlation |
| `AriaCommandException.java` | New — dispatch failure exception |
| `ScenarioExecutor.java` | Modified — AriaStep dispatch + batching |
| `pom.xml` | Modified — add `casehub-pages-push-runtime` dependency |

### `scenario-runtime/pom.xml` new dependency

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-pages-push-runtime</artifactId>
    <version>${project.version}</version>
</dependency>
```

The `push` module is already a transitive dependency via `push-runtime`.

## Testing Strategy

### Unit tests

**`AriaDispatcherTest`** — tests the dispatcher in isolation:
- `sendBroadcastsOnScenarioExecTopic` — verifies command broadcast on `scenario:exec`
- `sendCorrelatesResponseById` — send command, simulate response, verify return
- `sendTimesOutWhenNoResponse` — verify `AriaCommandException` after timeout
- `sendPropagatesBrowserError` — `CommandResult(id, false, "Element not found")` → exception
- `navigateSendsReadyProbes` — after navigate ok, verify ready probes sent at intervals
- `navigateCompletesWhenReadyResponseArrives` — simulate probe response after delay
- `navigateTimesOutOnPageLoadFailure` — no probe response → timeout exception
- `sendBatchSendsAllStepsAsOneBroadcast` — verify batch message structure
- `sendBatchReturnsOneResult` — one `CommandResult` for the whole batch
- `onCommandResultIgnoresUnknownId` — no exception for unknown IDs

**`ScenarioExecutorAriaTest`** — tests executor integration with AriaDispatcher:
- `ariaStepDelegatesToDispatcher` — verify non-no-op execution
- `ariaStepResultStoredInVariableContext` — named step with result → available for interpolation
- `consecutiveUnnamedStepsBatched` — verify batching logic
- `navigateStepNotBatched` — navigate breaks batch grouping
- `namedStepNotBatched` — named steps dispatched individually
- `variableInterpolationInTarget` — `${step-name.field}` resolved before dispatch

**`CdiCommandResultHandlerTest`** — verifies the CDI event bridge:
- `handleFiresCdiEvent` — verify event is fired with the correct payload

### Edge cases

- Empty scenario (no steps) → no-op success (existing behaviour)
- AriaStep with null target (e.g., navigate has value but no target) → dispatcher handles gracefully
- Batch of size 1 → dispatched as single command, not batch
- All steps in a scenario are batchable → one batch dispatch
- Variable interpolation references a failed ARIA step → runtime error (existing fail-fast model)

## Protocols Checked

| Protocol | Status |
|----------|--------|
| `aria-interaction-contract` | Compliant — all targeting uses `{role, name}` via AriaTarget |
| `pages-event-contract` | Compliant — uses `scenario:exec` topic with colon delimiter |
| `push` wire protocol | Extended — `CommandResultHandler` added to push module |

## References

- `backend/mcp/src/main/java/io/casehub/pages/mcp/AriaCommandBridge.java` — prior art for correlation pattern
- `packages/pages-aria/src/server/scenario-handler.ts` — browser-side command handler (already supports `scenario:exec`, `ready` action)
- `backend/scenario-runtime/src/main/java/io/casehub/pages/scenario/runtime/ScenarioExecutor.java` — AriaStep no-op to replace
- `backend/push/src/main/java/io/casehub/pages/push/PushRequest.java` — `CommandResult` record with `result` map
- `backend/push/src/main/java/io/casehub/pages/push/EventBroadcaster.java` — server→browser broadcast
- `backend/push/src/main/java/io/casehub/pages/push/PushMessage.java` — `event()` and `batch()` builders
- `backend/push-runtime/src/main/java/io/casehub/pages/push/runtime/PushProducers.java` — `@DefaultBean` pattern precedent
- `GE-20260818-0dd695` — EventBroadcaster subclassing for tests
- `GE-20260817-2dcaae` — async/sync handler gotcha (already fixed in scenario-handler.ts)
- `PP-20260817-a11y01` — ARIA interaction contract (targeting by role+name only)
- `PP-20260705-bac842` — pages-event contract (colon-delimited topics)
- #323 spec `2026-08-20-scenario-executor-design.md` — §Navigate and Page Transitions, §Wire Protocol
- casehubio/casehub-pages#324 — issue
