# Decisions — #32 Discord Slash Commands & Interactions

## D1: Generic ChatPlatform capability, not Discord-specific

**Choice:** New `Commands` capability on the ChatPlatform SPI, benefiting all platform implementations
**Alternatives:**
- Discord-specific interaction handling in `chat-discord` only — limits reuse, duplicates patterns when Slack/Teams need the same
- Separate `InteractionPlatform` SPI — adds a parallel SPI hierarchy when the capability pattern already handles this
**Rationale:** Slash commands are a cross-platform concept (Discord, Slack, Teams). The capability-based ChatPlatform pattern handles platform differences via native/degraded support.
**Trade-offs:** Requires abstracting across Discord's rich interaction model and Slack's simpler text-based model; lowest-common-denominator risk for batch 1
**Sources:** ChatPlatform SPI (chat-spi), issue #29 design (capability pattern precedent)
**Exploration:** quick
**Status:** captured

## D2: CommandHandler SPI with CDI discovery

**Choice:** Consumers implement a `CommandHandler` interface, discovered as CDI beans at startup
**Alternatives:**
- CDI observer pattern (CommandInvocation event) — no return channel for synchronous responses without callback threading
- Annotation-driven (@SlashCommand on methods) — more magic, harder to test, less consistent with existing SPI patterns
**Rationale:** Consistent with `Connector`/`InboundConnector` discovery pattern. Handler is both declaration and implementation in one bean.
**Trade-offs:** Slightly more boilerplate than annotation-driven, but more explicit and testable
**Sources:** InboundConnector SPI, Connector SPI, ConnectorService CDI discovery
**Exploration:** quick
**Status:** captured

## D3: Immediate + deferred response model

**Choice:** `CommandResponse` as a sealed type with immediate and deferred variants
**Alternatives:**
- Immediate only — too constrained; 3-second platform timeout rules out database/API calls
**Rationale:** Both Discord and Slack support deferred responses (acknowledge immediately, send full response later). Without this, the capability would be unusable for real commands.
**Trade-offs:** Deferred path adds complexity (callback mechanism for follow-up response)
**Sources:** Discord Interaction Response types, Slack response_url pattern
**Exploration:** quick
**Status:** captured

## D4: Automatic command registration at startup

**Choice:** Commands capability discovers all CommandHandler beans at startup and registers their definitions with the platform
**Alternatives:**
- Explicit `commands.register()` API call at runtime — requires consumer to know when/how to register, adds lifecycle complexity
**Rationale:** Mirrors InboundConnectorService pattern. Consumer just implements the interface; framework handles registration.
**Trade-offs:** No dynamic command add/remove at runtime (can be added later if needed)
**Sources:** InboundConnectorService startup pattern
**Exploration:** quick
**Status:** captured

## D5: Dedicated JAX-RS endpoints per platform

**Choice:** Platform-specific JAX-RS endpoints for receiving command invocations (DiscordInteractionEndpoint, SlackCommandEndpoint), bypassing WebhookInboundConnector
**Alternatives:**
- Extend WebhookInboundConnector with a `Responded` result variant — stretches the inbound message abstraction to cover request-response operations it wasn't designed for
**Rationale:** Interactions are request-response operations, not inbound messages. Dedicated endpoints keep the HTTP transport clean and don't distort existing abstractions.
**Trade-offs:** Platform-specific HTTP handling code (signature verification, serialization) in each endpoint rather than shared through the webhook router
**Sources:** WebhookInboundConnector, WebhookResult sealed type, WebhookRequest
**Exploration:** quick
**Status:** captured

## D6: Typed parameters with small initial set

**Choice:** `CommandParameter` with type enum (STRING, INTEGER, BOOLEAN, NUMBER) covering cross-platform common subset
**Alternatives:**
- Untyped string only — wastes Discord's native parameter validation and autocomplete
**Rationale:** Discord maps typed parameters to native UI (autocomplete, validation). Slack degrades gracefully (parameters become documentation-only hints). Platform-specific types (USER, CHANNEL) deferred to batch 2.
**Trade-offs:** Slack's text-only model means typed parameters are aspirational on that platform until Slack adds richer command definitions
**Sources:** Discord Application Command option types, Slack slash command text model
**Exploration:** quick
**Status:** captured

## D7: Module structure — all in existing modules

**Choice:** SPI types + CommandService in `chat-spi`, platform implementations in existing `chat-*` modules, JAX-RS endpoints in platform modules
**Alternatives:**
- New `commands` module for CommandService — adds a module for a single service class tightly coupled to chat-spi types
**Rationale:** Follows ConnectorService-in-connectors-api precedent. CommandService is tightly coupled to Commands capability and CommandHandler SPI.
**Trade-offs:** chat-spi grows slightly; acceptable given the capability already lives there
**Sources:** connectors-api module (ConnectorService precedent), chat-spi module structure
**Exploration:** quick
**Status:** captured
