# MobilePlatform SPI — Design Brief

Issue: connectors#206

## What we're building

A `MobilePlatform` SPI that unifies all phone-number-based communication — SMS, voice calls, voicemail, and WhatsApp — under one platform with capability sub-interfaces. Follows the established connector pattern (ChatPlatform, BankPlatform, etc.).

## Why

- No telephony connector exists. Agents can't make phone calls, check voicemail, or send SMS through the platform SPI.
- Legacy `TwilioSmsConnector` and `WhatsAppInboundConnector` use the old fire-and-forget `Connector` SPI, not the capability-based platform pattern.
- The dinner booking scenario (location → voice call to restaurant → email confirmation → SMS notification) needs a real telephony capability.
- Voicemail transcription lets LLM agents check voicemails the same way they check emails.

## Capability sub-interfaces

| Capability | Operations |
|-----------|-----------|
| `Sms` | send(to, body), receive (inbound webhook), list sent/received |
| `VoiceCall` | initiate(to), bidirectional streaming (TTS out / STT in), end |
| `VoiceCallControl` | hold, resume, transfer, hang up, DTMF tones |
| `Voicemail` | list, get, delete, transcribe (delegates to VoicePlatform STT) |
| `Messaging` | WhatsApp/iMessage — send/receive rich messages, media, templates |

## Provider modules

### mobile-ref — local telephone exchange

In-memory switchboard for agent-to-agent voice calls. NOT canned responses — real TTS/STT audio bytes, simulated wire.

- Agents register numbers with the exchange
- Agent A calls Agent B's number → switchboard routes audio bidirectionally
- If no pickup → voicemail (switchboard records, stored for STT retrieval)
- Multiple concurrent calls, hold, transfer — all in-memory
- SMS log for send/receive simulation
- Runs in CI, no API keys, no cost, no network

### mobile-dial — Dial (getdial.ai)

Primary production provider. $5 free credit, no card needed.

**Base URL:** `https://api.getdial.ai/v1`
**Auth:** `Authorization: Bearer sk_live_xxx` via platform `CredentialResolver`
**Docs:** docs.getdial.ai

**Core endpoints:**

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/messages` | Send SMS/iMessage/WhatsApp |
| `POST` | `/messages/:id/reply` | Reply to a message |
| `GET` | `/messages` | List messages |
| `POST` | `/calls` | Place AI voice call |
| `GET` | `/calls/:id` | Get call details + transcript |
| `POST` | `/calls/:id/stop` | Hang up |
| `GET` | `/numbers` | List phone numbers |
| `POST` | `/numbers` | Provision a new number |
| `DELETE` | `/numbers/:id` | Release a number |
| `POST` | `/webhooks` | Create webhook subscription |
| `GET` | `/events/wait` | Long-poll for events |
| `GET` | `/events/stream` | SSE event stream |
| `GET` | `/lookup/number` | Check number capabilities |

**Voice call model:** Dial handles TTS/STT internally. You provide an `outboundInstruction` (system prompt) and Dial manages the conversation:

```bash
curl -X POST https://api.getdial.ai/v1/calls \
  -H "Authorization: Bearer sk_live_..." \
  -H "Content-Type: application/json" \
  -d '{
    "to": "+447700900000",
    "fromNumberId": "pn_123",
    "outboundInstruction": "You are calling a restaurant to book a table for 2 at 7pm tonight. Be polite and confirm the booking.",
    "language": "en-GB"
  }'
```

Call returns immediately (`status: Queued`). Transcript available via `call.ended` event or `GET /calls/:id` — includes structured `transcriptTurns` with speaker, text, and timing.

**Self-hosted mode:** Two WebSocket protocols for bringing your own voice stack:
- "Self-hosted LLM" — Dial handles audio, you provide conversational logic (text-level)
- "Self-hosted Audio" — raw bidirectional audio, you handle ASR/LLM/TTS

**Events:** `message.received`, `call.ended`, `call.transcribed`, `call.status_changed`, `number.status_changed`

**Inbound SMS:** `POST /events/wait` blocks until `message.received` event. Body at `data.body`, media at `data.media[]`.

**Limits:** Free accounts: 5 min/call max, 2 concurrent calls. $3/mo/number, $0.02/SMS, $0.13-0.22/min voice.

### mobile-twilio — Twilio

Industry standard. $15 free trial credit.

- REST API: well-documented, battle-tested
- Media Streams for bidirectional voice audio (WebSocket)
- Already have `TwilioSmsConnector` in codebase (untested against real account)
- SMS + voice + WhatsApp on one number
- Absorbs existing `TwilioSmsConnector` and `WhatsAppInboundConnector`

## Authentication

Platform `CredentialResolver` — same pattern as LLM providers:
- `credentialResolver.resolve("dial")` → `{"api-key": "sk_live_xxx"}`
- `credentialResolver.resolve("twilio")` → `{"account-sid": "xxx", "auth-token": "xxx"}`

No connector-specific auth code. The resolver reads from Quarkus config, Spring environment, or agent-config manifest.

## Voice call architecture

```
Agent A (TTS) → audio bytes → MobilePlatform.voiceCall().speak()
                                    ↓
                            [mobile-ref: in-memory]  OR  [mobile-dial: WebSocket to Dial]
                                    ↓
                            audio bytes → Agent B (STT) or real phone recipient
```

For agent-driven conversations (dinner booking):
1. Agent opens call via `MobilePlatform.voiceCall().initiate(to)`
2. Agent speaks via TTS → `voiceCall.speak(audio)`
3. Agent listens via STT ← `voiceCall.listen()` returns audio
4. Agent session decides next utterance based on transcript
5. Loop until conversation complete
6. Agent ends call via `voiceCall.end()`

## Dinner booking playbook (target)

Five domains in one playbook: location + voice + email + SMS + chat.

```yaml
- location-search-nearby:
    latitude: 51.5074
    longitude: -0.1278
    category: restaurant
    keyword: thai

- mobile-call:
    to: ${restaurant.phone}
  capture: call

# Agent-driven voice conversation with restaurant
- agent-session-open:
    session: booking-agent
    system: "You are calling a restaurant to book a table for 2 at 7pm."
    model: classifier

# ... turn-by-turn voice conversation ...

- mobile-sms:
    to: "+447700900000"
    body: "Booking confirmed at ${restaurant.name}. Table for 2, 7pm."

- messaging-send-email:
    to: bookings@restaurant.example.com
    subject: "Booking confirmation — Smith, 2 guests, 7pm"
    body: "This confirms our booking."
```

## What absorbs into MobilePlatform

| Current | Becomes |
|---------|---------|
| `TwilioSmsConnector` (core) | `MobilePlatform.sms()` |
| `WhatsAppInboundConnector` (webhook) | `MobilePlatform.messaging()` inbound |
| `ConnectorMessagingApi.sendSms()` | Generated plugin from MobilePlatform |
| `ConnectorMessagingApi.sendWhatsapp()` | Generated plugin from MobilePlatform |

## Plugin generation

MobilePlatform's `@McpDomain` API class methods will auto-generate playbook plugins via the same GraphQLResolverProcessor pipeline. No hand-written plugins needed — the generator delegates to the API class.

## Testing strategy

- `mobile-ref` for playbook tests — local exchange with real TTS/STT
- Sign up for Dial free account ($5) and Twilio trial ($15) — test real SMS first, then voice
- Integration tests in `integration-tests/` module for real provider tests (gated by API key presence)

## Cross-provider voice call test

Ultimate integration test: **two LLM agents voice-chatting across providers.**

- Agent A on Dial (phone number X) calls Agent B on Twilio (phone number Y)
- Real phone call over the PSTN — Dial → phone network → Twilio
- Agent A speaks with Kokoro TTS, listens with Whisper STT
- Agent B speaks with Kokoro TTS, listens with Whisper STT
- Each agent has its own LLM session deciding what to say
- The conversation is real — two AI agents having a phone call through two different providers

This proves:
1. Both provider implementations work end-to-end
2. The SPI abstraction is real — same `MobilePlatform` interface, different backends
3. TTS/STT integration with telephony works bidirectionally
4. Agent-driven voice conversations work in practice

Test scenario: Agent A (customer) calls Agent B (restaurant) to make a booking. Same dinner booking flow, but over real phone lines with two different providers.

## Open questions for the spec

1. Should `VoiceCall` be synchronous (blocking call) or async (event-driven with callbacks)?
2. How does the agent-driven conversation loop work in a playbook? Is it a `while` loop, or a fixed number of turns?
3. Should `mobile-ref` support conference calls (3+ agents)?
4. How do we handle call recording consent / compliance?
5. Should voicemail transcription happen eagerly (on receive) or lazily (on access)?

## References

- Dial API docs: docs.getdial.ai
- Dial pricing: $5 free, $3/mo/number, $0.02/SMS, $0.13-0.22/min voice
- Numbrrs (UK real SIM SMS): numbrrs.com, £1.50/mo — backup for UK-specific needs
- Platform CredentialResolver: `io.casehub.platform.api.credentials.CredentialResolver`
- Existing voice infrastructure: voice-spi, voice-sherpa (Kokoro TTS, Whisper STT)
- Existing connectors: TwilioSmsConnector, WhatsAppInboundConnector
