# CLAUDE.md
**Name:** casehub-connectors

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2

## Work Tracking

Issue tracking: enabled
GitHub repo: casehubio/connectors

## What This Project Is

Outbound and inbound message connector library for the casehubio platform. Provides a `Connector` CDI SPI (outbound) and `InboundConnector`/`WebhookInboundConnector` SPIs (inbound) with built-in implementations for Slack, Teams, Twilio SMS, WhatsApp, and email. Also provides a `ChatPlatform` SPI (`chat-spi`) for structured interaction with chat systems (channels, threads, reactions, presence, members, channel management, member management, message history) with graceful degradation across platforms and `@SimulationEligible` annotation for capability-based simulation (platform#375 for recursive wrapper generation). ChatPlatform model includes `RichCard` for platform-agnostic rich content and `Channel` with `memberCount`. ChatPlatform implementations: `chat-ref` (in-memory reference), `chat-irc` (IRC with 3 native capabilities), `chat-discord` (Discord with 8 native capabilities + Gateway inbound + attachment downloading + rich embed support), `chat-slack` (Slack with 9 native capabilities — most capable implementation; batch user fetch for members, full ts-precision message history), `chat-signal` (Signal with 6 native capabilities — Messaging, Discovery, Members, Reactions, ChannelManagement, MemberManagement; backed by external signal-cli-rest-api Docker container over HTTP/WebSocket; groups + contacts as channels; compound message identity sender:timestamp; WebSocket inbound). Shared HTTP clients: `slack-bot` (Slack Web API — 16 methods: messaging, channel listing, reactions, presence, members, users, channel management incl. archive, member management, message history), `discord` (Discord Bot REST API v10 + Gateway WebSocket + CDN attachment download with SSRF defense + rich embed serialization + channel delete), `signal-cli` (signal-cli-rest-api HTTP + WebSocket client — send, groups, contacts, reactions, members, attachments; no AGPL dependencies). MCP tools: `send_slack`, `send_teams`, `send_sms`, `send_whatsapp`, `send_email`, `send_chat`, `list_channels`, `list_chat_channels`, `calendar_list_calendars`, `calendar_list_events`, `calendar_get_event`, `calendar_create_event`, `calendar_update_event`, `calendar_delete_event`. `@McpDomain("connectors")` provides platform-dispatch operations via `casehub_action`: `injectChat` (simulate inbound chat message), `sendNotification` (outbound delivery via named connector), `connectorStatus` (registered connectors and capabilities), `sentMessages` (verification of sent messages in dev/test). `ConnectorService.send()` fires `Event<SentMessage>` on every outbound delivery for CDI observer capture. `ChannelManagement` SPI includes `delete()` — Slack archives via `conversations.archive`, Discord calls `DELETE /channels/{id}`. The runnable chat workbench (formerly `chat-demo`) has been migrated to [casehubio/chat-app](https://github.com/casehubio/chat-app). `graphql` module provides `@McpDomain("connectors")` resolvers for platform MCP dispatch — generated from `ConnectorOperations` SPI interface via `GraphQLResolverProcessor`, auto-discovered by `GraphQLModelScanner`. `SentMessageCapture` (profile-gated, `@UnlessBuildProfile("prod")`) records sent messages for verification queries. `notification-bridge` module bridges the platform notification delivery system (`NotificationDeliverer`, `DeliveryChannelRegistry`) to the connector SPI — each `Connector` with a non-null `channelType()` auto-registers as a notification delivery channel at startup. `Connector.send()` returns `boolean` (success/failure). `Connector.channelType()` defaults to `id()`; override to map to a different channel type (`TwilioSmsConnector` → `"sms"`) or return `null` to opt out of notification bridging. `DeliveryChannelDescriptor` carries `DestinationScope` (PER_USER or PER_TENANT) — per-tenant channels (Slack, Teams) deliver once per tenant per event, with the dispatcher deduplicating across the per-user loop. `DestinationResolver` SPI (in `casehub-platform-api`) resolves `userId` → connector-specific destination per channel. Config-based `DestinationResolver` fallback reads destinations from `casehub.notification.destinations.<channel>.<userId>` config properties — starter implementation for dev/test. `DigestFormatter` CDI SPI provides channel-type-aware digest delivery (email HTML, SMS short text, WhatsApp rich text). `EmailConnector` supports `format=html` attribute for HTML rendering via `Mail.withHtml()`. Also provides a `CalendarPlatform` SPI (`calendar-spi`) for calendar integration (list calendars, list/get/create/update/delete events) with sealed `EventTiming` model (Timed/AllDay) and `@SimulationEligible` annotation for platform simulation framework integration. CalendarPlatform implementations: `calendar-ref` (in-memory reference), `calendar-google` (Google Calendar API with OAuth2 refresh token auth, paginated listEvents). Also provides a `BankFeedPlatform` SPI (`bank-spi`) for financial data integration (list accounts, balance, paginated transaction queries) with `@SimulationEligible` annotation for platform simulation framework integration. Also provides an `EmailPlatform` SPI (`email-spi`) for email query/read (list mailboxes, paginated message listing, message retrieval, attachment content) with `@SimulationEligible` — complements existing EmailConnector (outbound) and EmailInboundConnector (push inbound).

**This is the canonical connector infrastructure for the platform.** Any casehubio repo that needs to send outbound messages or receive inbound webhook messages must use these SPIs, not implement its own connector.

## Key Rule

Do not add business logic, orchestration, or domain knowledge here. This library is pure delivery infrastructure — it sends outbound messages and receives inbound ones, firing a CDI event. Callers decide when, what, and to whom; observers decide what to do with received messages.

## Modules

Multi-module Maven project (`casehub-connectors-parent`):

| Module | Purpose |
|--------|---------|
| `core` | Connector SPI, ConnectorService, InboundConnector SPI |
| `webhook` | WebhookInboundConnector SPI |
| `email` | Email connector (quarkus-mailer) |
| `email-inbound` | Inbound email connector |
| `slack-bot` | Slack Web API HTTP client (16 methods) |
| `chat-spi` | ChatPlatform SPI |
| `chat-ref` | In-memory reference ChatPlatform |
| `chat-irc` | IRC ChatPlatform |
| `discord` | Discord Bot REST API + Gateway client |
| `chat-discord` | Discord ChatPlatform |
| `signal-cli` | signal-cli-rest-api HTTP/WebSocket client |
| `chat-signal` | Signal ChatPlatform |
| `chat-slack` | Slack ChatPlatform |
| `notification-bridge` | Platform notification delivery bridge |
| `calendar-spi` | CalendarPlatform SPI |
| `calendar-ref` | In-memory reference CalendarPlatform |
| `calendar-google` | Google Calendar API CalendarPlatform |
| `bank-spi` | BankFeedPlatform SPI |
| `email-spi` | EmailPlatform SPI |
| `graphql` | GraphQL resolvers for MCP dispatch |
| `mcp` | MCP tool definitions |

## Build and Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

## Java on This Machine

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)    # Java 26, use for dev and tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # GraalVM 25, native only
```

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:** Add to `pom.xml` `<repositories>`:
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Cross-project SNAPSHOT versions:** All casehubio artifacts are `0.2-SNAPSHOT` resolved from GitHub Packages.

## Repo Guide

This repo owns its own documentation, synced to parent via CI:
- `docs/guides/consumer-guide.md` — for app builders: modules, APIs, quick start
- `docs/guides/contributor-guide.md` — for platform builders: architecture, SPIs, internals

Update the relevant guide in the same session when implementation changes modules, SPIs, or public APIs. Do not defer — drift compounds.

Read `docs/guides/consumer-guide.md` for app-level work. Only read `docs/guides/contributor-guide.md` when modifying this repo's internals or extension points.

## Platform Docs

- [Platform Index](https://raw.githubusercontent.com/casehubio/parent/main/docs/INDEX.md) — discovery index (start here)
- [Building Platform](https://raw.githubusercontent.com/casehubio/parent/main/docs/guides/building-platform.md) — platform contributor guide

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
Before committing: `superpowers:requesting-code-review`

Living docs — check for drift after significant changes:
- `ARC42STORIES.MD` — primary design doc; check §9-10 after SPI, module, or connector changes
- `docs/adr/INDEX.md`

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.
