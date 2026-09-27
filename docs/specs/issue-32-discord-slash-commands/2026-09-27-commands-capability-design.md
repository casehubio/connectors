# Commands Capability — Design Spec

> **Issue:** casehubio/connectors#32
> **Date:** 2026-09-27
> **Status:** Draft
> **Decisions:** [decisions.md](decisions.md)

## Overview

New `Commands` capability on the ChatPlatform SPI for slash command
registration, invocation dispatch, and response delivery. Cross-platform
abstraction — works across Discord, Slack, and any future chat platform
implementation.

Consumers implement a `CommandHandler` interface (one bean per command).
The framework discovers handlers at startup, registers their definitions
with the platform, and dispatches invocations to the matching handler
when users invoke commands. Supports both immediate and deferred
responses to handle the 3-second platform timeout constraint.

Batch 1 scope: slash commands with typed parameters. Batch 2 (future):
message components, modals, autocomplete.

## Architecture

### Module structure

No new modules. All new types land in existing modules (D7).

```
chat-spi/
  src/main/java/io/casehub/connectors/chat/spi/
    Commands.java                 — capability interface (registration)
  src/main/java/io/casehub/connectors/chat/command/
    CommandHandler.java           — handler SPI (consumers implement this)
    CommandDefinition.java        — command name, description, parameters
    CommandParameter.java         — parameter name, description, type, required
    CommandParameterType.java     — STRING, INTEGER, BOOLEAN, NUMBER
    CommandInvocation.java        — what the handler receives
    CommandResponse.java          — sealed: Immediate | Deferred
    DeferredReply.java            — callback for deferred responses
    CommandService.java           — discovery + dispatch coordinator
  src/main/java/io/casehub/connectors/chat/degraded/
    NoOpCommands.java             — degraded fallback

chat-discord/
  src/main/java/io/casehub/connectors/chat/discord/
    DiscordCommands.java          — Commands impl (registration via REST)
    DiscordInteractionEndpoint.java — JAX-RS endpoint for interactions

chat-slack/
  src/main/java/io/casehub/connectors/chat/slack/
    SlackCommands.java            — Commands impl (no-op registration)
    SlackCommandEndpoint.java     — JAX-RS endpoint for slash commands

chat-ref/
  src/main/java/io/casehub/connectors/chat/ref/
    RefCommands.java              — in-memory Commands impl

discord/
  src/main/java/io/casehub/connectors/discord/
    DiscordClient.java            — new methods for command registration
```

### Component diagram

```
┌──────────────────────────────────────────────────────────┐
│  chat-spi                                                │
│                                                          │
│  CommandService (@ApplicationScoped)                     │
│    ├── discovers CommandHandler beans via @All injection  │
│    ├── on startup: calls Commands.registerAll()           │
│    └── dispatch(name, invocation) → matching handler      │
│                                                          │
│  Commands (capability interface)                         │
│    └── registerAll(List<CommandDefinition>)               │
│                                                          │
│  CommandHandler (SPI — consumers implement)               │
│    ├── definition() → CommandDefinition                   │
│    └── handle(CommandInvocation) → CommandResponse        │
└──────────┬───────────────────────────────────────────────┘
           │ implements
           ▼
┌──────────────────────────────────────────────────────────┐
│  chat-discord                                            │
│                                                          │
│  DiscordCommands (Commands impl)                         │
│    └── registerAll() → PUT /applications/{id}/commands   │
│                                                          │
│  DiscordInteractionEndpoint (@Path("/interactions/.."))   │
│    ├── validates Ed25519 signature                        │
│    ├── handles PING → PONG                               │
│    ├── APPLICATION_COMMAND → CommandService.dispatch()    │
│    └── deferred: type 5 ack → webhook follow-up          │
└──────────────────────────────────────────────────────────┘
```

## SPI types (`chat-spi`)

### CommandDefinition

```java
package io.casehub.connectors.chat.command;

import java.util.List;

public record CommandDefinition(
        String name,
        String description,
        List<CommandParameter> parameters) {

    public CommandDefinition {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Command name must not be blank");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Command description must not be blank");
        if (parameters == null)
            parameters = List.of();
    }
}
```

Command names follow Discord's constraints (1-32 lowercase chars,
no spaces, hyphens and underscores allowed). Slack is more permissive
but the Discord constraints are a safe cross-platform subset.

### CommandParameter

```java
package io.casehub.connectors.chat.command;

public record CommandParameter(
        String name,
        String description,
        CommandParameterType type,
        boolean required) {

    public CommandParameter {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Parameter name must not be blank");
        if (type == null)
            type = CommandParameterType.STRING;
    }
}
```

### CommandParameterType

```java
package io.casehub.connectors.chat.command;

public enum CommandParameterType {
    STRING,
    INTEGER,
    BOOLEAN,
    NUMBER
}
```

Pure cross-platform abstraction — no platform-specific methods.
Platform type mapping lives in the platform module (e.g.,
`DiscordCommands` maps STRING→3, INTEGER→4, BOOLEAN→5, NUMBER→10
when serializing for the Discord API). Batch 2 adds USER, CHANNEL,
ROLE.

### CommandInvocation

```java
package io.casehub.connectors.chat.command;

import java.util.Map;

public record CommandInvocation(
        String commandName,
        Map<String, String> arguments,
        String userId,
        String channelId,
        String platformId,
        Map<String, String> metadata) {

    public CommandInvocation {
        if (arguments == null) arguments = Map.of();
        if (metadata == null) metadata = Map.of();
    }

    public String argument(String name) {
        return arguments.get(name);
    }

    public String requireArgument(String name) {
        String value = arguments.get(name);
        if (value == null)
            throw new IllegalArgumentException(
                    "Missing required argument: " + name);
        return value;
    }
}
```

`metadata` carries platform-specific context that doesn't map to the
cross-platform model. Discord: `guild_id`, `interaction_id`,
`interaction_token`. Slack: `team_id`, `team_domain`, `response_url`.

### CommandResponse

```java
package io.casehub.connectors.chat.command;

public sealed interface CommandResponse {

    record Immediate(String text, boolean ephemeral)
            implements CommandResponse {

        public Immediate(String text) {
            this(text, false);
        }
    }

    record Deferred(
            boolean ephemeral,
            java.util.function.Consumer<DeferredReply> callback)
            implements CommandResponse {}
}
```

`Immediate` — handler returns the response synchronously. The endpoint
serializes it as the HTTP response body (within the 3-second window).

`Deferred` — handler cannot produce a response within 3 seconds. The
endpoint sends an acknowledgement to the platform immediately, then
invokes the callback asynchronously. The callback receives a
`DeferredReply` that it uses to send the actual response.

`ephemeral` — if true, only the invoking user sees the response.
Discord: `flags: 64`. Slack: `response_type: "ephemeral"`.

### DeferredReply

```java
package io.casehub.connectors.chat.command;

public interface DeferredReply {
    void send(String text, boolean ephemeral);

    default void send(String text) {
        send(text, false);
    }
}
```

Platform implementations provide the `DeferredReply`:
- Discord: `POST /webhooks/{application_id}/{interaction_token}`
- Slack: `POST {response_url}`

### CommandHandler

```java
package io.casehub.connectors.chat.command;

public interface CommandHandler {
    CommandDefinition definition();
    CommandResponse handle(CommandInvocation invocation);
}
```

Consumers implement this interface and register as CDI beans.
One handler per command. Example:

```java
@ApplicationScoped
public class StatusCommandHandler implements CommandHandler {

    @Override
    public CommandDefinition definition() {
        return new CommandDefinition(
                "status",
                "Check system status",
                List.of());
    }

    @Override
    public CommandResponse handle(CommandInvocation invocation) {
        return new CommandResponse.Immediate("All systems operational");
    }
}
```

### Commands (capability interface)

```java
package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.command.CommandDefinition;
import java.util.List;

public interface Commands {
    void registerAll(List<CommandDefinition> commands);
}
```

Added to `ChatPlatform` as the 10th capability:

```java
// In ChatPlatform.java:
Commands commands();
```

With `NoOpCommands` as the degraded fallback (logs a WARNING on
`registerAll()`). `supports(Commands.class)` returns true only for
platforms that natively support slash commands.

The `@SimulationEligible` annotation on `ChatPlatform` gains
`"commands"` in its capabilities array.

### NoOpCommands

```java
package io.casehub.connectors.chat.degraded;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import java.util.List;
import java.util.logging.Logger;

public class NoOpCommands implements Commands {
    private static final Logger LOG =
            Logger.getLogger(NoOpCommands.class.getName());

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        if (!commands.isEmpty()) {
            LOG.warning("Commands capability not supported — "
                    + commands.size() + " commands not registered");
        }
    }
}
```

### CommandService

```java
package io.casehub.connectors.chat.command;

import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Commands;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@Startup
public class CommandService {

    private static final Logger LOG =
            Logger.getLogger(CommandService.class.getName());

    private final Map<String, CommandHandler> handlers;
    private final List<CommandDefinition> definitions;

    @Inject
    public CommandService(
            @All List<CommandHandler> handlers,
            @All List<ChatPlatform> platforms) {

        this.handlers = handlers.stream()
                .collect(Collectors.toMap(
                        h -> h.definition().name(),
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate command name: '"
                                    + a.definition().name() + "'");
                        }));

        this.definitions = handlers.stream()
                .map(CommandHandler::definition)
                .toList();

        if (!definitions.isEmpty()) {
            for (ChatPlatform platform : platforms) {
                if (platform.supports(Commands.class)) {
                    try {
                        platform.commands().registerAll(definitions);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING,
                                "Command registration failed on "
                                + platform.id(), e);
                    }
                }
            }
        }
    }

    public CommandResponse dispatch(
            String commandName,
            CommandInvocation invocation) {
        CommandHandler handler = handlers.get(commandName);
        if (handler == null) {
            LOG.warning("No handler for command: " + commandName);
            return new CommandResponse.Immediate(
                    "Unknown command: " + commandName, true);
        }
        return handler.handle(invocation);
    }

    public List<CommandDefinition> definitions() {
        return definitions;
    }
}
```

`@Startup` ensures handler discovery and command registration happen
eagerly at boot, not lazily on first injection.

### ChatPlatform changes

Add `commands()` method with `NoOpCommands` degraded default:

```java
// ChatPlatform.java — new method:
Commands commands();

// Builder — new field + setter:
private Commands commands;
public Builder commands(Commands c) {
    this.commands = c;
    nativeCapabilities.add(Commands.class);
    return this;
}

// build() — default to NoOpCommands:
commands != null ? commands : new NoOpCommands(),
```

`DefaultChatPlatform` record gains the `commands` field.

`NoOpChatPlatform` returns `NoOpCommands`.

All existing direct `ChatPlatform` implementors (Discord, Slack, IRC,
Signal, Ref) need an explicit `commands()` method — none of them use
the Builder. Implementations that don't support commands (IRC, Signal)
return `new NoOpCommands()`. The Builder's default also falls back to
`NoOpCommands` for any Builder-based construction.

## Discord implementation (`chat-discord`)

### DiscordCommands

```java
package io.casehub.connectors.chat.discord;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import io.casehub.connectors.discord.DiscordClient;
import java.util.List;
import java.util.logging.Logger;

public class DiscordCommands implements Commands {

    private static final Logger LOG =
            Logger.getLogger(DiscordCommands.class.getName());

    private final DiscordClient client;
    private final String token;
    private final String applicationId;

    public DiscordCommands(
            DiscordClient client,
            String token,
            String applicationId) {
        this.client = client;
        this.token = token;
        this.applicationId = applicationId;
    }

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        if (applicationId.isBlank()) {
            LOG.warning("discord: application-id not configured, "
                    + "skipping command registration");
            return;
        }
        client.bulkOverwriteGlobalCommands(
                token, applicationId, commands);
    }
}
```

Uses Discord's bulk overwrite endpoint
(`PUT /applications/{id}/commands`). This is idempotent — on every
startup, the full set of commands is registered, replacing any
previously registered commands. This keeps registered commands in sync
with the running application without requiring manual cleanup.

### DiscordClient additions

New methods on `DiscordClient`:

```java
public void bulkOverwriteGlobalCommands(
        String token,
        String applicationId,
        List<CommandDefinition> commands) {
    // PUT /applications/{applicationId}/commands
    // Body: JSON array of command objects
    // Each command: { name, description, type: 1, options: [...] }
    // options: [{ name, description, type (from CommandParameterType),
    //             required }]
}

public void respondToInteraction(
        String interactionId,
        String interactionToken,
        int responseType,
        String content,
        boolean ephemeral) {
    // POST /interactions/{interactionId}/{interactionToken}/callback
    // Body: { type: responseType, data: { content, flags? } }
}

public void sendFollowupMessage(
        String applicationId,
        String interactionToken,
        String content,
        boolean ephemeral) {
    // POST /webhooks/{applicationId}/{interactionToken}
    // Body: { content, flags? }
}
```

### DiscordInteractionEndpoint

```java
package io.casehub.connectors.chat.discord;

import io.casehub.connectors.chat.command.*;
import io.casehub.connectors.discord.DiscordClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.*;
import java.security.spec.*;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

@Path("/interactions/discord")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DiscordInteractionEndpoint {

    private final CommandService commandService;
    private final DiscordClient client;
    private final String applicationId;
    private final PublicKey publicKey;
    private final ExecutorService executor;

    // Injected via CDI; publicKey parsed from config
    // casehub.discord.public-key (hex-encoded Ed25519 public key)
}
```

Request flow:

1. **Signature verification** — reconstruct the signed payload
   (`timestamp + body`), verify against the Ed25519 public key using
   `java.security.Signature.getInstance("Ed25519")`. Return 401 if
   invalid.

2. **PING (type 1)** — return `{"type": 1}`. Required by Discord for
   endpoint URL verification during setup.

3. **APPLICATION_COMMAND (type 2)** — extract command name, options,
   user ID, channel ID from the interaction payload. Build
   `CommandInvocation` with `platformId = "discord"` and metadata
   containing `guild_id`, `interaction_id`, `interaction_token`. Call
   `commandService.dispatch()`.

4. **Immediate response** — return HTTP 200 with:
   ```json
   {
     "type": 4,
     "data": {
       "content": "response text",
       "flags": 64
     }
   }
   ```
   `type 4` = CHANNEL_MESSAGE_WITH_SOURCE. `flags: 64` only if ephemeral.

5. **Deferred response** — return HTTP 200 with `{"type": 5}` (or
   `{"type": 5, "data": {"flags": 64}}` if ephemeral). Submit the
   deferred callback to an executor, providing a `DeferredReply` that
   calls `client.sendFollowupMessage()`.

### Ed25519 signature verification

Uses Java's built-in `EdDSA` support (Java 15+). No external dependency.

```java
private boolean verifySignature(
        String signature, String timestamp, String body) {
    byte[] signatureBytes = HexFormat.of().parseHex(signature);
    byte[] message = (timestamp + body).getBytes(StandardCharsets.UTF_8);

    Signature verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(publicKey);
    verifier.update(message);
    return verifier.verify(signatureBytes);
}
```

The public key is parsed at startup from the hex-encoded config property:

```java
KeyFactory kf = KeyFactory.getInstance("EdDSA");
byte[] keyBytes = HexFormat.of().parseHex(publicKeyHex);
publicKey = kf.generatePublic(
        new EdECPublicKeySpec(
                NamedParameterSpec.ED25519,
                // decode the 32-byte raw key into EdECPoint
                ...));
```

### Config properties

| Property | Required | Description |
|----------|----------|-------------|
| `casehub.discord.application-id` | For commands | Discord application ID (snowflake) |
| `casehub.discord.public-key` | For commands | Ed25519 public key (hex), from Discord app settings |

Both default to `""`. If blank, `DiscordCommands.registerAll()` is a
no-op, and `DiscordInteractionEndpoint` returns 503 for all requests.

### ChatDiscordBeans changes

`DiscordChatPlatform` construction gains `DiscordCommands`:

```java
@Produces
@ApplicationScoped
public DiscordChatPlatform discordChatPlatform(
        DiscordClient client,
        DiscordGatewayPresenceCache presenceCache,
        @ConfigProperty(name = "casehub.discord.token",
                         defaultValue = "") String token,
        @ConfigProperty(name = "casehub.discord.application-id",
                         defaultValue = "") String applicationId) {
    return new DiscordChatPlatform(
            client, presenceCache, token, applicationId);
}
```

`DiscordChatPlatform.init()` creates `DiscordCommands` when
`applicationId` is not blank, otherwise `NoOpCommands`.

## Slack implementation (`chat-slack`)

### SlackCommands

```java
package io.casehub.connectors.chat.slack;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import java.util.List;
import java.util.logging.Logger;

public class SlackCommands implements Commands {

    private static final Logger LOG =
            Logger.getLogger(SlackCommands.class.getName());

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        // Slack slash commands are configured in the app manifest,
        // not registered via API. Log for diagnostics.
        LOG.info("slack: " + commands.size()
                + " commands available (register via Slack app manifest)");
    }
}
```

Slack doesn't have a runtime command registration API — commands are
defined in the Slack app's manifest. `registerAll()` logs the available
commands so operators can cross-reference with the manifest.

### SlackCommandEndpoint

```java
@Path("/interactions/slack")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public class SlackCommandEndpoint {
    // 1. Validate HMAC-SHA256 signature
    // 2. Parse form-encoded body into CommandInvocation
    // 3. Call commandService.dispatch()
    // 4. Immediate: return JSON { text, response_type }
    // 5. Deferred: return HTTP 200 empty,
    //    then POST to response_url
}
```

Slack sends slash commands as `application/x-www-form-urlencoded` with
fields: `command`, `text`, `user_id`, `channel_id`, `team_id`,
`team_domain`, `response_url`.

The `text` field contains all arguments as a single string. The endpoint
does not parse this into typed parameters — it passes the raw text in
`CommandInvocation.arguments()` as a single entry with key `"text"`.
Individual parameter parsing is the handler's responsibility on Slack.

HMAC-SHA256 verification uses `X-Slack-Signature` and
`X-Slack-Request-Timestamp`:

```java
String basestring = "v0:" + timestamp + ":" + rawBody;
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(
        signingSecret.getBytes(UTF_8), "HmacSHA256"));
String expected = "v0=" + hex(mac.doFinal(basestring.getBytes(UTF_8)));
return MessageDigest.isEqual(
        expected.getBytes(UTF_8), signature.getBytes(UTF_8));
```

### Config properties

| Property | Required | Description |
|----------|----------|-------------|
| `casehub.slack.signing-secret` | For commands | Slack app signing secret for HMAC verification |

## Reference implementation (`chat-ref`)

### RefCommands

```java
package io.casehub.connectors.chat.ref;

import io.casehub.connectors.chat.command.CommandDefinition;
import io.casehub.connectors.chat.spi.Commands;
import java.util.List;

public class RefCommands implements Commands {

    private List<CommandDefinition> registered = List.of();

    @Override
    public void registerAll(List<CommandDefinition> commands) {
        this.registered = List.copyOf(commands);
    }

    public List<CommandDefinition> registered() {
        return registered;
    }
}
```

In-memory. Stores registered definitions for test verification.
`RefChatPlatform` returns `RefCommands` as its commands capability with
`supports(Commands.class) = true`.

## Testing

### Layer 1: CommandService unit tests (`chat-spi`)

- Handler discovery — multiple `CommandHandler` beans found, all
  definitions collected
- Dispatch routes to correct handler by command name
- Unknown command returns error response
- Duplicate command names throw `IllegalStateException` at startup
- Registration called on platforms that support Commands
- Registration not called on platforms that don't support Commands

### Layer 2: DiscordInteractionEndpoint tests (`chat-discord`)

WireMock-backed integration tests.

- Ed25519 signature valid → 200
- Ed25519 signature invalid → 401
- PING (type 1) → responds with `{"type": 1}`
- APPLICATION_COMMAND immediate → `{"type": 4, "data": {"content": ...}}`
- APPLICATION_COMMAND deferred → `{"type": 5}`, then follow-up POST
  to webhook URL
- Ephemeral flag → `flags: 64` in response data
- Missing public-key config → endpoint returns 503
- Malformed JSON body → 400

### Layer 3: DiscordCommands tests (`chat-discord`)

WireMock-backed.

- `registerAll()` sends correct PUT request to
  `/applications/{id}/commands`
- Command definitions serialize correctly (name, description, options
  with types)
- Registration failure → WARNING log, non-fatal
- Blank application-id → no-op

### Layer 4: SlackCommandEndpoint tests (`chat-slack`)

- HMAC signature valid → 200
- HMAC signature invalid → 401
- Timestamp replay (>5 min old) → 401
- Form-encoded body parsed into CommandInvocation correctly
- `text` field passed as `arguments.get("text")`
- Immediate response → JSON `{text, response_type}`
- Deferred response → HTTP 200 empty, then POST to response_url
- Missing signing-secret → endpoint returns 503

### Layer 5: RefCommands tests (`chat-ref`)

- `registerAll()` stores definitions
- `registered()` returns stored definitions
- `supports(Commands.class)` returns true

### Layer 6: CommandService CDI integration test (`chat-spi`)

Quarkus `@QuarkusTest` with a test `CommandHandler` bean:

- Handler discovered and registered
- `dispatch()` invokes handler and returns response
- Deferred response callback executed

## Implementation notes

These are not design decisions but implementation-level details to
address during coding:

- **DefaultChatPlatform record** — gains a `commands` field. Insert
  after `messageHistory`, before `nativeCapabilities` in the positional
  constructor call from `Builder.build()`.

- **DiscordInteractionEndpoint raw body** — Ed25519 verification needs
  the raw request body bytes, but JAX-RS deserializes before the
  method runs. The endpoint should accept `String` (raw body) and
  deserialize manually with Jackson, or use a `ContainerRequestFilter`
  to capture the raw bytes.

- **Deferred callback executor** — use Quarkus's managed executor
  (`@Inject ManagedExecutor`) rather than a custom thread pool, so
  it participates in Quarkus lifecycle and shutdown.

- **CommandService error handling** — `registerAll()` is called inside
  the constructor during CDI initialization. Network failures to
  Discord's API must not break application startup. The spec shows
  try/catch around `registerAll()` — implementation must preserve this.

- **Record validation tests** — `CommandDefinition`, `CommandParameter`,
  and `CommandInvocation` compact constructors throw on invalid input.
  Add a small unit test class for these validation cases alongside the
  CommandService tests.

## Scope boundary — what's NOT in batch 1

- Message components (buttons, select menus, action rows)
- Modal dialogs
- Autocomplete callbacks
- Platform-specific parameter types (USER, CHANNEL, ROLE)
- Subcommands and subcommand groups
- Dynamic command registration/deregistration at runtime
- Command permissions / default member permissions
- Localization of command names and descriptions
- MCP tool exposure for commands

## Deliverables beyond code

### CLAUDE.md update
- Add `Commands` to ChatPlatform capability list
- Update Discord capability count (8 → 9 native)
- Update Slack capability count (9 → 10 native)
- Add new config properties to relevant sections

### ARC42STORIES.MD update
- §5 Module structure: note Commands capability addition
- §9 Update ChatPlatform journey to reference Commands

### Consumer guide update
- `docs/guides/consumer-guide.md`: add Commands section with
  `CommandHandler` example and config properties

### Contributor guide update
- `docs/guides/contributor-guide.md`: document Commands SPI contract,
  endpoint patterns, signature verification

## References

- `chat-spi/ChatPlatform.java` — SPI interface being extended
- `chat-spi/Messaging.java` — capability interface pattern
- `chat-spi/ChatPlatformService.java` — service + CDI discovery pattern
- `chat-spi/ChatBeans.java` — `@All List<ChatPlatform>` injection pattern
- `chat-discord/DiscordChatPlatform.java` — platform implementation pattern
- `chat-discord/ChatDiscordBeans.java` — CDI producer pattern
- `discord/DiscordClient.java` — HTTP client (new methods needed)
- `chat-ref/RefChatPlatform.java` — ref implementation pattern
- Discord API: Application Commands (`PUT /applications/{id}/commands`)
- Discord API: Interactions (`POST /interactions/{id}/{token}/callback`)
- Discord API: Interaction verification (Ed25519)
- Slack API: Slash commands (form-encoded POST + HMAC-SHA256)
- D1–D7 (decisions.md)
