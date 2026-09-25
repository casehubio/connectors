# casehub-connectors -- Consumer Guide

> Outbound and inbound message connector library for the casehubio platform.

**GitHub:** [casehubio/connectors](https://github.com/casehubio/connectors)
**Tier:** Foundation (no casehubio dependencies)

---

## Purpose

Canonical notification and messaging infrastructure for the platform. Provides CDI SPIs for outbound message delivery, inbound message reception, structured chat-system interaction, calendar integration, and notification bridging. Any casehubio repo that needs to send or receive messages must use these SPIs rather than implementing its own connectors.

Pure delivery infrastructure -- no domain logic, no routing decisions, no scheduling. Callers decide when, what, and to whom; observers decide what to do with received messages.

No Camel, no vendor SDKs -- pure `java.net.http.HttpClient` for HTTP-based connectors.

---

## Module Structure

There are 21 active modules in the build (pom.xml `<modules>`):

| Module (artifactId prefix: `casehub-connectors-`) | What consumers need to know |
|----------------------------------------------------|-----------------------------|
| `core` | `Connector` outbound SPI, `InboundConnector` / `WebhookInboundConnector` inbound SPIs, `ConnectorService` routing, `ConnectorDiscovery` SPI, `ConnectorsCloudEventAdapter`, `ConnectorMeshBridge` SPI, built-in outbound impls (Slack, Teams, Twilio SMS, WhatsApp), `InboundMessage` / `Attachment` / `ConnectorMessage` records |
| `email` | Email outbound via `quarkus-mailer` (`EmailConnector`) |
| `email-inbound` | IMAP polling inbound (`EmailInboundConnector`), `EmailInboundAccountProvider` SPI |
| `webhook` | JAX-RS `WebhookRouter` + webhook-based inbound connectors (Slack, Teams, WhatsApp, Twilio SMS), `SigHelper` HMAC utilities |
| `mcp` | MCP tool surface for LLM agents: `send_slack`, `send_teams`, `send_sms`, `send_whatsapp`, `send_email`, `send_chat`, `list_channels`, `list_chat_channels`, plus 6 calendar tools |
| `slack-bot` | `SlackBotClient` -- pure `java.net.http` client for the Slack Web API (16 methods including 2 `postMessage` overloads). Pagination via generic `paginateGet<T>` with fail-soft partial results |
| `discord` | `DiscordClient` (REST API v10), `DiscordGateway` (Gateway v10 WebSocket via Vert.x), `DiscordGatewayPresenceCache`, `DiscordDiscovery` |
| `chat-spi` | `ChatPlatform` SPI with `@SimulationEligible` (capability-based), 9 capability interfaces, `ChatPlatformService` routing, `ChatInboundAdapter`, `InboundTranslator` SPI, `NoOpChatPlatform` `@DefaultBean` fallback, model records (`RichCard`, `Channel`, `ChatContent`, `ReceivedMessage`, `SendResult`, `Member`, `PresenceStatus`) |
| `chat-ref` | In-memory reference `ChatPlatform` for testing (`RefChatPlatform`) |
| `chat-irc` | IRC `ChatPlatform` (3 native capabilities: Messaging, Discovery, Members) |
| `chat-discord` | Discord `ChatPlatform` (8 native capabilities), `DiscordInboundConnector` (Gateway-based), RichCard-to-DiscordEmbed translation |
| `chat-slack` | Slack `ChatPlatform` (9 native capabilities -- most complete), RichCard-to-Block Kit translation |
| `signal-cli` | `SignalClient` HTTP client + `SignalWebSocket` WebSocket client for `signal-cli-rest-api`. Pure `java.net.http` -- no AGPL dependencies |
| `chat-signal` | Signal `ChatPlatform` (6 native capabilities: Messaging, Discovery, Members, Reactions, ChannelManagement, MemberManagement), `SignalInboundConnector` (WebSocket-based), `SignalInboundTranslator` |
| `notification-bridge` | Bridges platform notification delivery system to connector SPI. `NotificationBridgeStartup`, `ConnectorNotificationDeliverer`, `DigestFormatter` SPI, `ConfigDestinationResolver` |
| `calendar-spi` | `CalendarPlatform` SPI with `@SimulationEligible`, `CalendarPlatformService` routing, `NoOpCalendarPlatform` `@DefaultBean` fallback, model records (`CalendarEvent`, `CalendarInfo`, `EventDetails`), sealed `EventTiming` (Timed/AllDay with Jackson `@JsonTypeInfo`) |
| `calendar-ref` | In-memory reference `CalendarPlatform` for testing (`RefCalendarPlatform`) |
| `calendar-google` | Google Calendar API provider with OAuth2 refresh token auth, paginated `listEvents` |
| `bank-spi` | `BankPlatform` SPI with `@SimulationEligible`, model records (`AccountInfo`, `AccountBalance`, `Transaction`), `BankPlatformService` routing, `NoOpBankPlatform` `@DefaultBean` fallback |
| `bank-ref` | In-memory reference `BankPlatform` for testing (`RefBankPlatform`) — 3 UK accounts, 12 transactions, payment lifecycle simulation |
| `email-spi` | `EmailPlatform` SPI with `@SimulationEligible`, model records (`Mailbox`, `EmailSummary`, `EmailMessage`, `EmailAttachment`), `EmailPlatformService` routing. Complements `email` (outbound) and `email-inbound` (push) |
| `graphql` | `ConnectorOperations` `@McpDomain("connectors")` SPI — GraphQL/MCP surface with 4 operations: `injectChat` (constructs `InboundMessage`, fires via `InboundConnectorService`), `sendNotification` (delegates to `ConnectorService.send()`), `connectorStatus` (aggregates outbound + chat + inbound connectors), `sentMessages` (queries `SentMessageCapture`, profile-gated). `ConnectorsModelEnricher` provides domain summary/state for MCP. `SentMessageCapture` (`@UnlessBuildProfile("prod")`) CDI observer for test/dev message capture. |

**CDI events:** `ConnectorService.send()` fires `Event<SentMessage>` on every outbound delivery. `SentMessage` record carries the connector ID, recipient, message content, and timestamp. Observe with `@ObservesAsync SentMessage` for delivery tracking.

---

## Key Consumer APIs and SPIs

### Outbound -- `Connector` SPI

```java
public interface Connector {
    String id();
    boolean send(ConnectorMessage message);
    default String channelType() { return id(); }
}
```

CDI SPI. Implementations are `@ApplicationScoped` beans auto-discovered at startup. `send()` takes a `ConnectorMessage(destination, title, body, attributes)` and returns `boolean` (success/failure).

**Contract:** `send()` must not throw unchecked exceptions, must be thread-safe, and should complete within its configured timeout.

`channelType()` defaults to `id()`. Override to map to a different notification channel type (e.g. `TwilioSmsConnector` returns `"sms"` instead of `"twilio-sms"`). Return `null` to opt out of notification bridging.

**ConnectorService** -- inject this rather than individual `Connector` beans. Routes by `connectorId`, validates registered ids, throws `IllegalArgumentException` for unknown ids.

```java
@Inject ConnectorService connectorService;
connectorService.send("slack", new ConnectorMessage(webhookUrl, "Title", "Body"));
```

**Built-in outbound implementations:**

| Connector ID | Class | Module | Auth | Notes |
|-------------|-------|--------|------|-------|
| `slack` | `SlackConnector` | core | Webhook URL in `destination` | No config needed -- webhook URL is the credential |
| `teams` | `TeamsConnector` | core | Webhook URL in `destination` | Renders as Adaptive Card (v1.4) |
| `twilio-sms` | `TwilioSmsConnector` | core | Account SID + Auth Token + From number in MP Config | `channelType()` returns `"sms"`. E.164 phone number in `destination` |
| `whatsapp` | `WhatsAppConnector` | core | API Token + Phone Number ID in MP Config | Template messages via `attributes("templateName")` + `attributes("templateLanguage")` (default `en_US`) |
| `signal` | `SignalConnector` | core | signal-cli-rest-api URL + sender number in MP Config | Backed by external `signal-cli-rest-api` Docker container |
| `email` | `EmailConnector` | `email` | SMTP via `quarkus-mailer` config | Supports `format=html` attribute for HTML rendering via `Mail.withHtml()` |

### ConnectorMessage Record

```java
public record ConnectorMessage(
    String destination,   // webhook URL, E.164 phone number, or email address
    String title,         // optional subject/card title (null = connector default)
    String body,          // main text content
    Map<String, String> attributes  // optional key-value metadata
) {}
```

Convenience constructors: `ConnectorMessage(destination, title, body)` and `ConnectorMessage(destination, body)`.

The `attributes` map carries connector-specific extras. Known attribute keys:
- `templateName` -- WhatsApp template name for first-contact / outside 24-hour window
- `templateLanguage` -- BCP-47 code for WhatsApp template (default `en_US`)
- `format` -- `"html"` for email HTML rendering
- `category`, `severity`, `actionUrl` -- set by notification bridge during delivery

### Inbound -- `InboundConnector` / `WebhookInboundConnector`

Two inbound SPIs for different transport patterns:

**`InboundConnector`** -- pull-based polling (e.g. IMAP, Discord Gateway). `@ApplicationScoped` CDI beans. `InboundConnectorService` calls `start(InboundMessageSink)` at startup, `stop()` at shutdown.

```java
public interface InboundConnector {
    String id();
    void start(InboundMessageSink sink);
    void stop();
}
```

**`WebhookInboundConnector`** -- push-based webhook reception. Abstract base class (NOT an interface). Does not implement `InboundConnector` -- webhook connectors have no pull lifecycle. Discovered by `WebhookRouter` via `@All List<WebhookInboundConnector>`.

```java
public abstract class WebhookInboundConnector {
    public abstract String id();
    public abstract WebhookResult handle(WebhookRequest request);
}
```

**ID contract for both:** must be lowercase, URL-safe, no slashes or spaces (pattern: `[a-z0-9][a-z0-9\-]*`). Validated at startup -- violations cause startup failure. Webhook connector IDs are also the URL path segment: `POST /connectors/{id}/webhook`.

**Breaking contract:** observers MUST use `@ObservesAsync InboundMessage` -- synchronous `@Observes` will not receive events. At-least-once delivery.

### InboundMessage Record

```java
public record InboundMessage(
    String connectorId,          // e.g. "slack-inbound", "email-inbound"
    String connectorType,        // non-null semantic type: "slack", "email", "sms", "whatsapp", "teams", "discord", "irc"
    String externalSenderId,
    String externalChannelRef,
    String content,
    List<Attachment> attachments, // always non-null, defensively copied
    Instant receivedAt,
    Map<String, String> metadata,
    String tenancyId             // nullable -- null in single-tenant deployments
) {}
```

Constants for connector IDs: `InboundConnectorIds` (e.g. `SLACK_INBOUND = "slack-inbound"`, `EMAIL = "email-inbound"`).
Constants for connector types: `InboundConnectorTypes` (e.g. `SLACK = "slack"`, `EMAIL = "email"`).

**Built-in inbound implementations:**

| Connector ID | Class | Module | Transport | Auth |
|-------------|-------|--------|-----------|------|
| `email-inbound` | `EmailInboundConnector` | `email-inbound` | IMAP polling | IMAP username/password in MP Config |
| `slack-inbound` | `SlackInboundConnector` | `webhook` | Webhook POST | HMAC-SHA256 signing secret |
| `teams-inbound` | `TeamsInboundConnector` | `webhook` | Webhook POST | HMAC-SHA256 with Base64-encoded shared secret |
| `whatsapp-inbound` | `WhatsAppInboundConnector` | `webhook` | Webhook POST/GET | HMAC-SHA256 + hub.mode verify token challenge |
| `twilio-sms-inbound` | `TwilioSmsInboundConnector` | `webhook` | Webhook POST | HMAC-SHA1 (Twilio algorithm), form-encoded |
| `discord-inbound` | `DiscordInboundConnector` | `chat-discord` | Discord Gateway WebSocket | Discord bot token |
| `irc-inbound` | `IrcInboundConnector` | `chat-irc` | IRC connection | IRC server config |
| `signal-inbound` | `SignalInboundConnector` | `chat-signal` | signal-cli-rest-api WebSocket | signal-cli URL + registered number |

### ConnectorDiscovery SPI

Optional interface for connectors whose targets are discoverable at runtime (e.g. Slack channels via `conversations.list`).

```java
public interface ConnectorDiscovery {
    String id();                        // matches Connector.id()
    List<DiscoveredTarget> discover();  // DiscoveredTarget(id, displayName)
}
```

Contract: should not throw (caller catches per-discovery); return empty list on failure. The `list_channels` MCP tool aggregates all registered implementations.

### CloudEvent Adapter

`ConnectorsCloudEventAdapter` observes `@ObservesAsync InboundMessage` and fires `Event<CloudEvent>.fireAsync()`. CloudEvent type: `io.casehub.connectors.inbound.<connectorType>`. Source: `/casehub-connectors/<connectorId>`. Subject: `channel/<externalChannelRef>`. `tenancyId` propagated as CloudEvent extension when non-null.

### ChatPlatform SPI

Structured interface for chat-system interactions beyond simple message delivery. Nine capability interfaces compose the platform:

| Capability | Interface | Methods |
|-----------|-----------|---------|
| Messaging | `Messaging` | `send(ChatChannelRef, ChatContent) -> SendResult` |
| Threading | `Threading` | `reply(ChatMessageRef, ChatContent) -> SendResult` |
| Discovery | `Discovery` | `listChannels() -> List<Channel>` |
| Reactions | `Reactions` | `add(ChatMessageRef, emoji)`, `remove(ChatMessageRef, emoji)`, `list(ChatMessageRef) -> List<String>` |
| Presence | `Presence` | `of(MemberRef) -> PresenceStatus`, `set(MemberRef, PresenceStatus)` |
| Members | `Members` | `list(ChatChannelRef) -> List<Member>` |
| Channel Management | `ChannelManagement` | `create(name, topic, description, isPrivate) -> Channel`, `delete(channelId)`, `find(channelId) -> Optional<Channel>` |
| Member Management | `MemberManagement` | `add(ChatChannelRef, Member)`, `remove(ChatChannelRef, MemberRef)` |
| Message History | `MessageHistory` | `messages(ChatChannelRef, since) -> List<ReceivedMessage>` |

**Builder pattern with auto-degradation.** Only `Messaging` is required. All other capabilities fill with graceful degraded implementations if not provided:

```java
ChatPlatform.builder("my-platform")
    .messaging(myMessaging)        // required
    .threading(myThreading)        // optional -- falls back to ChannelFallbackThreading
    .discovery(myDiscovery)        // optional -- falls back to EmptyDiscovery
    .build();
```

`ChatPlatform.supports(Class<?>)` returns `true` for natively supported capabilities, `false` for degraded.

**ChatPlatformService** -- inject for routing. `platform(id)` throws `IllegalArgumentException` for unknown ids.

**Key model records:**

- `ChatContent(text, markdown, attachments, cards)` -- `text` is required; `cards` carries `List<RichCard>`
- `RichCard(title, description, url, color, fields, thumbnailUrl, imageUrl, footer, author)` -- platform-agnostic rich content. Requires at least `title` or `description`. Has `Builder`. Translated to platform-native formats automatically (Block Kit for Slack, DiscordEmbed for Discord)
- `Channel(ref, name, topic, description, isPrivate, memberCount)` -- `memberCount` is nullable `Integer`
- `SendResult(ok, messageRef, timestamp, error)` -- factory methods `success(ref, ts)` and `failure(error)`
- `ReceivedMessage(platformId, channel, messageRef, parentRef, sender, content, receivedAt)` -- `parentRef` nullable (non-null for threaded replies)

**ChatInboundAdapter** -- CDI adapter that observes `@ObservesAsync InboundMessage`, looks up a matching `InboundTranslator` by `connectorType`, translates to `ReceivedMessage`, and fires `Event<ReceivedMessage>.fireAsync()`. Each chat platform module provides its own `InboundTranslator`.

| Implementation | Platform ID | Native capabilities |
|----------------|------------|-------------------|
| `RefChatPlatform` | `ref` | All 9 (in-memory reference for testing) |
| `IrcChatPlatform` | `irc` | 3 (Messaging, Discovery, Members) |
| `DiscordChatPlatform` | `discord` | 8 (all except MemberManagement, which is degraded) |
| `SlackChatPlatform` | `slack` | 9 (most complete) |
| `SignalChatPlatform` | `signal` | 6 (Messaging, Discovery, Members, Reactions, ChannelManagement, MemberManagement) |

### CalendarPlatform SPI

Calendar integration with full CRUD operations.

```java
public interface CalendarPlatform {
    String id();
    List<CalendarInfo> listCalendars();
    List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to);
    CalendarEvent getEvent(String calendarId, String eventId);
    CalendarEvent createEvent(String calendarId, EventDetails details);
    CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details);
    void deleteEvent(String calendarId, String eventId);
}
```

**CalendarPlatformService** -- inject for routing. Same pattern as `ConnectorService` and `ChatPlatformService`.

**Model records:**
- `CalendarInfo(id, summary, description, primary)` -- `primary` boolean marks the default calendar
- `CalendarEvent(id, calendarId, summary, description, location, timing, attendees, recurringEventId)` -- `recurringEventId` nullable
- `EventDetails(summary, description, location, timing, attendees)` -- input record for create/update
- `EventTiming` -- sealed interface: `Timed(start, end, timeZone)` or `AllDay(start, end)` where `start`/`end` are `Instant`/`LocalDate` respectively

| Implementation | Platform ID | Notes |
|----------------|------------|-------|
| `RefCalendarPlatform` | `ref` | In-memory reference for testing |
| `GoogleCalendarPlatform` | `google` | Google Calendar API with OAuth2 refresh token auth, paginated listEvents (max 20 pages) |

### BankPlatform SPI

Financial integration with capability sub-interfaces for account information (AISP) and payment initiation (PISP).

```java
@SimulationEligible(name = "bank-platform",
    capabilities = {"accountInformation", "paymentInitiation"})
public interface BankPlatform {
    String id();
    AccountInformation accountInformation(String userId);
    PaymentInitiation paymentInitiation(String userId);
    boolean supports(Class<?> capability);
}
```

Capability accessors are user-scoped -- each user has their own consent under PSD2. The `userId` parameter lets the provider resolve per-user state (consent tokens, scope checks).

**AccountInformation** -- `listAccounts()`, `balance(accountId)`, `listTransactions(accountId, from, to, pagination)`, `getTransaction(accountId, transactionId)`.

**PaymentInitiation** -- `initiatePayment(PaymentRequest)` returns `InitiatedPayment` with a hosted payment page URL for SCA; `paymentStatus(paymentId)` polls the result. `PaymentRequest` includes `idempotencyKey` (caller-generated UUID) and `PaymentDestination` sealed hierarchy (`UkAccount`, `IbanAccount`).

**BankPlatformService** -- inject for routing. Same pattern as `CalendarPlatformService`.

**Model records:**
- `AccountInfo(id, name, type, currency)` -- `type` is `AccountType` enum (CURRENT, SAVINGS, CREDIT_CARD, LOAN, MORTGAGE, OTHER)
- `AccountBalance(accountId, available, current, currency, asOf)` -- `BigDecimal` amounts; `available` = spendable, `current` = ledger balance
- `Transaction(id, accountId, amount, direction, currency, description, merchantName, category, date, status)` -- `amount` always positive, `direction` is DEBIT/CREDIT; `merchantName` and `category` nullable
- `PaymentRequest(idempotencyKey, amount, currency, beneficiaryName, destination, reference)`
- `InitiatedPayment(paymentId, hostedPaymentPageLink, status)`
- `PaymentDestination.UkAccount(sortCode, accountNumber)` / `PaymentDestination.IbanAccount(iban, bic)`

**Pagination:** Uses `Page<T>` and `PageRequest` from `connectors-api`. Call `PageRequest.first(25)` for the first page, then use `page.nextCursor()` for subsequent pages.

**Error contract:** `balance()` and `getTransaction()` throw `NoSuchElementException` on not-found. List operations return empty collections.

**Providers:**

| Implementation | `id()` | Capabilities |
|---|---|---|
| `RefBankPlatform` | `ref` | AccountInformation + PaymentInitiation (in-memory, pre-loaded UK test data) |
| `TrueLayerBankPlatform` | `truelayer` | AccountInformation + PaymentInitiation (TrueLayer Open Banking API) |

**bank-ref — Reference Implementation:** Add `casehub-connectors-bank-ref` for an in-memory BankPlatform that works out of the box with zero configuration. Pre-loaded with 3 UK bank accounts (current, savings, credit card) and 12 realistic transactions. Payment initiation simulates the full lifecycle: AUTHORIZATION_REQUIRED → EXECUTED → SETTLED (auto-advances on each `paymentStatus()` poll). When a real provider (e.g., `bank-truelayer`) is also on the classpath, both coexist — `BankPlatformService` routes by platform id.

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-connectors-bank-ref</artifactId>
    <version>${casehub.version}</version>
</dependency>
```

**TrueLayer consent:** Consent is provider-internal (not on the SPI). Inject `TrueLayerConsentService` to check consent status or initiate the consent flow. The consent flow is a browser redirect (OAuth2 + PSD2 SCA). The consent callback endpoint is at `/auth/truelayer/callback`.

**Consent persistence:** Consent tokens are persisted via the `ConsentTokenStore` SPI. Two implementations ship with `bank-truelayer`:

| Implementation | CDI Tier | When to use |
|---|---|---|
| `JpaConsentTokenStore` | Tier 1 (`@ApplicationScoped`) | Production — requires a datasource. Tokens survive JVM restarts. |
| `InMemoryConsentTokenStore` | Tier 3 (`@Alternative @Priority(100)`) | Dev/test — wins when on classpath. Tokens lost on restart. |

**Migration from in-memory to database:** Add `quarkus-hibernate-orm` and a JDBC driver to your app. Configure a datasource. `JpaConsentTokenStore` activates automatically at Tier 1. Remove `InMemoryConsentTokenStore` from the classpath (or leave it — Tier 3 only wins when explicitly selected via `@Alternative`). No code changes required — the `ConsentTokenStore` SPI handles the switch. Expired consents are cleaned up daily by a scheduled job.

**Simulation:** Annotated with `@SimulationEligible` -- the platform simulation framework generates CDI decorators at build time, including recursive wrappers for capability sub-interfaces. Qualified names: `bank-platform.accountInformation.listAccounts`, `bank-platform.accountInformation.balance`, `bank-platform.accountInformation.listTransactions`, `bank-platform.accountInformation.getTransaction`, `bank-platform.paymentInitiation.initiatePayment`, `bank-platform.paymentInitiation.paymentStatus`.

**Shipped corpus data:** The module includes example corpus YAML files on the classpath under `simulation/bank-feed/`:
- `accounts-corpus.yaml` -- 3 accounts (current, savings, credit card) with balances
- `transactions-corpus.yaml` -- 12 transactions with UK merchants, categories, pending/booked status
- `simulation.yaml` -- ready-to-use simulation config with strategies and key extractors

To use in your app: add `bank-spi` as a dependency -- the corpus files are on the classpath automatically. Point your simulation config's `corpus-files` at `classpath:simulation/bank-feed/accounts-corpus.yaml` etc., or copy `simulation/bank-feed/simulation.yaml` as a starting point. A combined config for both SPIs is available at `docs/examples/simulation/household-finance/simulation.yaml`.

**Dependency:**
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-connectors-bank-spi</artifactId>
    <version>${casehub.version}</version>
</dependency>
```

### EmailPlatform SPI

Email query/read integration -- mailbox listing, paginated message listing, message retrieval, attachment content. Complements `EmailConnector` (outbound) and `EmailInboundConnector` (push inbound).

```java
@SimulationEligible(name = "email-platform")
public interface EmailPlatform {
    String id();
    List<Mailbox> listMailboxes();
    Page<EmailSummary> listMessages(String mailboxId, Instant from, Instant to, PageRequest pagination);
    EmailMessage getMessage(String mailboxId, String messageId);
    byte[] getAttachmentContent(String mailboxId, String messageId, String attachmentId);
}
```

**EmailPlatformService** -- inject for routing. Same pattern as `CalendarPlatformService`.

**Model records:**
- `Mailbox(id, name, unreadCount)`
- `EmailSummary(id, mailboxId, messageId, from, subject, receivedAt, read)` -- `messageId` (RFC 2822 Message-ID) nullable
- `EmailMessage(id, mailboxId, messageId, from, to, cc, subject, bodyText, bodyHtml, receivedAt, read, attachments)` -- `bodyText`/`bodyHtml` nullable (at least one non-null); `messageId` nullable
- `EmailAttachment(id, filename, contentType, size)` -- `id` is provider-assigned part identifier (always non-null); `filename` nullable

**Error contract:** `getMessage()` and `getAttachmentContent()` throw `NoSuchElementException` on not-found.

**Correlation with EmailInboundConnector:** RFC 2822 `Message-ID` correlates queries with push events (`InboundMessage.metadata["message-id"]`). Consumers observing both paths must be idempotent. Messages with null `messageId` cannot be deduplicated.

**Simulation:** Same as BankFeedPlatform. Qualified names: `email-platform.listMailboxes`, `email-platform.listMessages`, `email-platform.getMessage`, `email-platform.getAttachmentContent`.

**Shipped corpus data:** Under `simulation/email/` on the classpath:
- `mailbox-corpus.yaml` -- 2 mailboxes, 6 messages with realistic UK senders/subjects
- `messages-corpus.yaml` -- full message bodies, attachments, RFC 2822 Message-IDs
- `simulation.yaml` -- ready-to-use simulation config

**Dependency:**
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-connectors-email-spi</artifactId>
    <version>${casehub.version}</version>
</dependency>
```

### Notification Bridge

The `notification-bridge` module bridges the platform notification delivery system (`NotificationDeliverer`, `DeliveryChannelRegistry` from `casehub-platform-api`) to the connector SPI.

**Auto-registration at startup:** `NotificationBridgeStartup` scans all `Connector` beans. Each with a non-null `channelType()` is registered as a notification delivery channel with `DeliveryChannelRegistry`.

**Destination scoping:** `DeliveryChannelDescriptor` carries `DestinationScope` (PER_USER or PER_TENANT). Slack and Teams are PER_TENANT -- per-tenant channels deliver once per tenant per event, with the dispatcher deduplicating across the per-user loop. All other channels are PER_USER.

**DestinationResolver** -- CDI SPI (in `casehub-platform-api`) resolves `userId` + `tenancyId` to a connector-specific destination string. Config-based fallback reads from `casehub.notification.destinations.<channel>.<userId>` when no CDI resolver is found for a channel type.

**DigestFormatter** -- CDI SPI for channel-type-aware digest delivery. Built-in formatters:
- `EmailDigestFormatter` -- HTML digest grouped by category, uses `format=html` attribute
- `SmsDigestFormatter` -- short text digest
- `WhatsAppDigestFormatter` -- rich text digest

When no `DigestFormatter` is registered for a channel, `DefaultDigestFormat` provides a plain-text fallback.

### MCP Tool Surface

The `mcp` module exposes tools for LLM agents via `quarkus-mcp-server`. All tools are annotated `@Blocking`.

| Tool name | Class | Parameters | What it does |
|-----------|-------|------------|-------------|
| `send_slack` | `SlackMcpTool` | webhookUrl, title, body | Post via Slack webhook |
| `send_teams` | `TeamsMcpTool` | webhookUrl, title, body | Post via Teams webhook |
| `send_sms` | `TwilioSmsMcpTool` | to (E.164), body | Send SMS via Twilio |
| `send_whatsapp` | `WhatsAppMcpTool` | to (E.164), body, templateName?, templateLanguage? | Send WhatsApp via Meta Cloud API |
| `send_email` | `EmailMcpTool` | to, subject, body | Send email via SMTP |
| `send_chat` | `ChatPlatformMcpTool` | platform, channel, text, parentMessageId?, card params or cards JSON | Send to any ChatPlatform with optional RichCard content and threading |
| `list_channels` | `ChannelDiscoveryMcpTool` | (none) | Aggregate all `ConnectorDiscovery` implementations |
| `list_chat_channels` | `ChatPlatformMcpTool` | platform | Rich channel listing from `ChatPlatform.discovery()` |
| `listCalendars` | `CalendarMcpTool` | platform | List available calendars |
| `listCalendarEvents` | `CalendarMcpTool` | platform, calendarId?, from, to | List events in time range |
| `getCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, eventId | Get specific event |
| `createCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, summary, description, location, timing params, attendees | Create event (timed or all-day) |
| `updateCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, eventId, updatable fields | Update event (merge semantics -- omitted fields keep current values) |
| `deleteCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, eventId | Delete event |

All MCP tools call `ConnectorMeshBridge.notifyDelivered()` after successful delivery for mesh integration.

`McpContentSanitizer` strips ASCII control characters and truncates to 500 chars before passing content to `ConnectorMeshBridge`.

---

## Configuration

Slack and Teams webhook connectors require no configuration -- the webhook URL is passed as the destination at call time.

| Property | Module | Purpose |
|----------|--------|---------|
| `casehub.connectors.twilio.account-sid` | core | Twilio Account SID |
| `casehub.connectors.twilio.auth-token` | core | Twilio Auth Token |
| `casehub.connectors.twilio.from` | core | Twilio sender phone number (E.164) |
| `casehub.connectors.whatsapp.api-token` | core | Meta Cloud API token |
| `casehub.connectors.whatsapp.phone-number-id` | core | WhatsApp Phone Number ID |
| `casehub.connectors.slack-bot.token` | slack-bot | Bot OAuth token (`xoxb-...`) |
| `casehub.connectors.slack-bot.api-base-url` | slack-bot | API base URL (default: `https://slack.com`; override for tests) |
| `casehub.connectors.calendar.google.client-id` | calendar-google | Google OAuth2 client ID |
| `casehub.connectors.calendar.google.client-secret` | calendar-google | Google OAuth2 client secret |
| `casehub.connectors.calendar.google.refresh-token` | calendar-google | Google OAuth2 refresh token |
| `casehub.connectors.signal.api-url` | signal-cli | signal-cli-rest-api base URL (e.g. `http://localhost:8080`) |
| `casehub.connectors.signal.sender` | signal-cli | Registered Signal phone number (E.164) |
| `quarkus.mailer.*` | email | SMTP configuration (host, port, from, username, password) |
| IMAP host, port, username, password | email-inbound | Email inbound polling (via `EmailInboundAccountProvider` SPI) |
| `casehub.notification.destinations.<channel>.<userId>` | notification-bridge | Config-based destination resolution fallback |

Connectors with blank credentials are no-ops -- they log a warning and return `false` from `send()`, allowing safe deployment without full configuration.

---

## Dependencies

Nothing in the casehubio ecosystem except `casehub-platform-api` (for `notification-bridge` only). Core module: `java.net.http.HttpClient`, `cloudevents-core` (CNCF CloudEvents SDK), `jackson-databind`. Optional modules: `quarkus-mailer` (email), `jakarta.mail` (email inbound), `quarkus-mcp-server` (MCP tools), Google Calendar API client (calendar-google), Vert.x WebSocket (Discord Gateway). Signal connector uses `java.net.http.HttpClient` and `java.net.http.WebSocket` -- no AGPL signal-cli dependencies in the JVM.

GroupId: `io.casehub` -- published to GitHub Packages at `0.2-SNAPSHOT`.

---

## What This Repo Does NOT Do

- Provide domain logic -- purely delivery infrastructure
- Route or schedule notifications -- callers decide when and what to send
- Depend on casehub-work, casehub-ledger, or casehub-engine
- Include vendor SDKs (Slack SDK, Twilio SDK) -- all HTTP-based connectors use `java.net.http.HttpClient` directly

**Consolidation rule:** Do not implement a new Slack, Teams, SMS, email, WhatsApp, Discord, IRC, Signal, or inbound connector in any other repo. All outbound and inbound messaging routes through these SPIs. If a new channel type is needed, add it here.
