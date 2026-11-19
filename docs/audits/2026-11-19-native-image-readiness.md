# Native Image Readiness Audit

**Date:** 2026-11-19
**Target:** GraalVM 25, Quarkus 3.32.2
**Scope:** All connector modules with production source code

## Summary

| Module | Status | Key concern |
|--------|--------|-------------|
| `connectors-api` | Ready | Pure records, sealed types, `HttpHelper` with `java.net.http` |
| `core` | Ready | CDI beans, `ConnectorsCloudEventAdapter` uses injected `ObjectMapper` (Quarkus handles) |
| `webhook` | Ready | `SigHelper` uses `Mac.getInstance("HmacSHA256")` — standard JCA, no config needed |
| `email` | Ready | Quarkus mailer extension — native-image-ready by design |
| `email-inbound` | Ready | CDI beans, standard patterns |
| `slack-bot` | Ready | `java.net.http.HttpClient`, JSON-P, no reflection |
| `chat-spi` | Ready | CDI beans, sealed types, records. `CommandService` uses CDI discovery (build-time) |
| `chat-ref` | Likely ready | YAML seed loading via `SeedLoader.class.getResourceAsStream("/seed/...")` — needs `resources` config for `/seed/*.yaml` |
| `chat-irc` | Ready | Pure Java sockets, no reflection |
| `chat-slack` | Likely ready | `SlackCommandEndpoint` uses `Mac.getInstance("HmacSHA256")` + `MessageDigest.isEqual()` — standard JCA. Ed25519 NOT used |
| `chat-discord` | Needs work | `DiscordInteractionEndpoint` uses `Signature.getInstance("Ed25519")` + `KeyFactory.getInstance("EdDSA")` — EdDSA provider needs registration |
| `chat-signal` | Ready | Delegates to `signal-cli` module. CDI beans |
| `chat-teams` | Needs work | `BotFrameworkAuth` uses Nimbus JOSE+JWT (`nimbus-jose-jwt`) for JWKS/JWT validation — requires reflection config for Nimbus classes |
| `discord` | Likely ready | Vert.x WebSocket (Quarkus bundles Vert.x native support), Jackson `ObjectMapper`, `java.net.http` for REST |
| `signal-cli` | Ready | `java.net.http.HttpClient` for REST, `java.net.http.WebSocket` for WS, Jackson `ObjectMapper` |
| `microsoft-graph-client` | Ready | Pure `java.net.http.HttpClient`, JSON-P. No reflection |
| `notification-bridge` | Ready | CDI beans, no reflection |
| `graphql` | Ready | Generated resolvers, CDI beans (Quarkus GraphQL extension handles native) |
| `mcp` | Ready | MCP tool definitions, CDI beans |
| `connectors-spring` | N/A | Spring Boot auto-config — not relevant for native image MCP server (Quarkus path) |
| `calendar-spi` | Ready | Records, sealed `EventTiming`, CDI beans |
| `calendar-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `calendar-google` | Needs work | Google API client library (`google-api-services-calendar`) uses reflection heavily for JSON model binding |
| `calendar-microsoft` | Ready | Uses `microsoft-graph-client` (pure `java.net.http`), JSON-P |
| `bank-spi` | Ready | Records, CDI beans |
| `bank-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `bank-truelayer` | Likely ready | `quarkus-oidc-client` (native-ready), JAX-RS endpoints, JSON-P. No JWS verification in production code |
| `email-spi` | Ready | Records, CDI beans |
| `email-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `email-google` | Needs work | Google API client library (`google-api-services-gmail`) — same reflection concerns as `calendar-google` |
| `email-google-inbound` | Needs work | jose4j (`org.bitbucket.b_c:jose4j`) for JWT validation of Pub/Sub push notifications — requires reflection config |
| `email-microsoft` | Ready | Uses `microsoft-graph-client`, JSON-P |
| `document-spi` | Ready | Records, CDI beans |
| `document-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `document-google` | Needs work | Google API client library (`google-api-services-drive`) |
| `document-microsoft` | Ready | Uses `microsoft-graph-client`, JSON-P |
| `contacts-spi` | Ready | Records, CDI beans |
| `contacts-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `contacts-google` | Needs work | Google People API client library |
| `location-spi` | Ready | Records, CDI beans |
| `location-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `location-google` | Needs work | Google Maps/Places client library |
| `commerce-spi` | Ready | Records, CDI beans |
| `commerce-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `commerce-amazon` | Likely ready | Custom AWS Sig v4 signer using `Mac.getInstance("HmacSHA256")` + `MessageDigest.getInstance("SHA-256")`, Jackson `ObjectMapper` for response parsing |
| `commerce-shopify` | Likely ready | `java.net.http.HttpClient`, Jackson `ObjectMapper` for GraphQL responses |
| `travel-spi` | Ready | Records, CDI beans |
| `travel-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `travel-amadeus` | Likely ready | `java.net.http.HttpClient`, Jackson `ObjectMapper`, OAuth2 token management |
| `travel-google` | Likely ready | `java.net.http.HttpClient`, JSON-P |
| `project-spi` | Ready | Records, CDI beans |
| `project-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `project-github` | Likely ready | Uses `github-client` (`java.net.http`), JSON-P |
| `github-client` | Ready | Pure `java.net.http.HttpClient`, JSON-P |
| `health-spi` | Ready | Records, CDI beans |
| `health-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `voice-spi` | Needs work | `RoutingVoicePlatformService.discoverPlatforms()` uses `Class.forName()` + reflection to instantiate `VoicePlatform` implementations; YAML resource loading for voice manifests |
| `voice-ref` | Ready | In-memory, no native concerns |
| `voice-sherpa` | Won't work | Panama FFI (`java.lang.foreign.*`) with native C libraries (sherpa-onnx, espeak, whisper, ONNX Runtime). Uses `MethodHandle` downcalls, `MemorySegment` allocation, `ProcessBuilder` for model provisioning. GraalVM native image does not support Panama FFI |
| `voice-sherpa-native-*` | Won't work | Native library JARs (`.so`, `.dylib`, `.dll`) — complement to `voice-sherpa` |
| `voice-pipeline-steps` | Ready | Plugin steps that delegate to `VoicePlatform` SPI |
| `mobile-spi` | Ready | Records, CDI beans |
| `mobile-ref` | Likely ready | YAML seed loading — needs `/seed/*.yaml` resource config |
| `mobile-dial` | Likely ready | `java.net.http.HttpClient` for REST, `java.net.http.WebSocket` for LLM interaction, Jackson `ObjectMapper` |
| `mobile-twilio` | Likely ready | `java.net.http.HttpClient`, `Mac.getInstance` for HMAC signature verification, Jackson `ObjectMapper` |
| `web-spi` | Ready | Records, CDI beans |
| `web-ref` | Ready | In-memory, no native concerns |
| `web` | Ready | `java.net.http.HttpClient`, standard patterns |

## Detailed Notes

### Needs Work

#### `chat-teams` — Nimbus JOSE+JWT for Bot Framework auth

`BotFrameworkAuth` uses `com.nimbusds:nimbus-jose-jwt` for JWKS fetching and JWT RS256 verification. Nimbus uses reflection for JWK deserialization and crypto provider lookup.

**Fix options:**
1. Add `@RegisterForReflection` on Nimbus classes used at runtime, or provide a `reflect-config.json`
2. Replace Nimbus with `smallrye-jwt` (Quarkus extension, native-ready) or `jose4j` with Quarkus's native support
3. Use `quarkus-oidc` extension for the JWT validation — it handles all native config automatically

Option 3 is recommended — Quarkus OIDC is designed for this exact use case and is fully native-ready.

#### `chat-discord` — EdDSA crypto provider

`DiscordInteractionEndpoint` uses `Signature.getInstance("Ed25519")` and `KeyFactory.getInstance("EdDSA")`. The EdDSA provider is part of the JDK since Java 15, but GraalVM native image may not register it automatically.

**Fix:** Add `--initialize-at-build-time` for the EdDSA security provider, or add it to `native-image.properties`. Quarkus 3.x may handle this automatically — test first.

#### `voice-spi` — Class.forName in RoutingVoicePlatformService

`discoverPlatforms()` uses `Class.forName(className, true, cl)` followed by `getDeclaredConstructor().newInstance()` and `getMethod("withDefaults")` to instantiate `VoicePlatform` implementations discovered from `META-INF/yaml-services.yaml`.

**Fix:** Replace runtime reflection with CDI discovery. `VoicePlatform` implementations should be CDI beans injected via `@All List<VoicePlatform>` — the same pattern every other platform SPI uses. The YAML service file approach is a holdover that predates the CDI wiring.

#### Google API Client Libraries (`calendar-google`, `email-google`, `document-google`, `contacts-google`, `location-google`)

All Google provider modules use `com.google.api` client libraries which rely heavily on reflection for JSON model binding (`GenericJson`, `GenericData`). The `google-http-client-jackson2` transport uses Jackson with reflection for Google's model classes.

**Fix options:**
1. **Quarkus extension approach:** Use `quarkus-google-cloud-common` if it covers these APIs — it provides native image configuration
2. **Reflection config:** Generate `reflect-config.json` for all Google model classes used (tedious but viable)
3. **Replace with raw HTTP:** Rewrite Google provider modules to use `java.net.http.HttpClient` with JSON-P parsing (same pattern as `microsoft-graph-client`) — eliminates the Google client library entirely. Most modules only use 3-5 API endpoints

Option 3 is the most sustainable — it eliminates the dependency entirely and aligns with the project's existing pattern (`microsoft-graph-client` already proves this approach works).

#### `email-google-inbound` — jose4j for JWT validation

Uses `org.bitbucket.b_c:jose4j` for validating Google Pub/Sub push notification JWTs.

**Fix:** jose4j has known native image issues with JWKS resolution. Replace with `quarkus-oidc` or `smallrye-jwt` for JWT validation — both are native-ready.

### Likely Ready (resource config needed)

All `*-ref` modules use `SeedLoader.class.getResourceAsStream("/seed/<file>.yaml")` to load YAML seed data at startup. In native image, classpath resources must be registered.

**Fix:** Add to `application.properties` or `native-image.properties`:
```
quarkus.native.resources.includes=seed/*.yaml
```

Or add a `resource-config.json`:
```json
{
  "resources": {
    "includes": [{"pattern": "seed/.*\\.yaml$"}]
  }
}
```

Similarly, `voice-spi` loads `META-INF/yaml-services.yaml` and voice manifest YAML files.

All Jackson `ObjectMapper` usage in these modules is for YAML seed deserialization. Quarkus handles Jackson native image configuration for beans it discovers — but the `ObjectMapper` instances created directly (not injected) may need their target types registered for reflection. The seed loader pattern uses `TypeReference` or `Map.class` which should work, but the specific record types being deserialized into may need `@RegisterForReflection`.

### Won't Work

#### `voice-sherpa` and `voice-sherpa-native-*`

These modules use the Java Foreign Function & Memory API (Panama FFI, `java.lang.foreign.*`) to call native C libraries:
- `SherpaLibrary` — 30+ `MethodHandle` downcalls to sherpa-onnx C API
- `WhisperLibrary` — 15+ `MethodHandle` downcalls to whisper.cpp C API
- `EspeakLibrary` — espeak-ng phoneme generation via downcalls
- `OnnxRuntimeLibrary` — ONNX Runtime C API via downcalls
- `Provisioner` — `ProcessBuilder` for model downloading/extraction
- `Qwen3TextToSpeech`, `Qwen3VoicePool` — `ProcessBuilder` for Python subprocess

GraalVM native image does not support the Panama FFI (`java.lang.foreign.Linker`, `MethodHandle` downcalls to native symbols). These modules fundamentally cannot work in native image.

**Impact:** `voice-sherpa` is an optional provider. The `voice-ref` and any future cloud-API-based voice provider (e.g. Google Cloud Speech, Azure Cognitive Services) would work fine in native image. The `VoicePlatform` SPI and `voice-spi` module (after the `Class.forName` fix) are native-ready.

## Status Summary

| Category | Count | Modules |
|----------|-------|---------|
| **Ready** | 30 | connectors-api, core, webhook, email, email-inbound, slack-bot, chat-spi, chat-irc, chat-signal, microsoft-graph-client, notification-bridge, graphql, mcp, calendar-spi, calendar-microsoft, bank-spi, email-spi, email-microsoft, document-spi, document-microsoft, contacts-spi, location-spi, commerce-spi, travel-spi, project-spi, github-client, health-spi, voice-ref, voice-pipeline-steps, mobile-spi, web-spi, web-ref, web |
| **Likely ready** | 17 | All `*-ref` modules (11), chat-slack, discord, commerce-amazon, commerce-shopify, travel-amadeus, travel-google, project-github, mobile-dial, mobile-twilio, bank-truelayer |
| **Needs work** | 8 | chat-teams, chat-discord, voice-spi, calendar-google, email-google, document-google, contacts-google, location-google, email-google-inbound |
| **Won't work** | 6 | voice-sherpa, voice-sherpa-native-linux-arm64, voice-sherpa-native-linux-x64, voice-sherpa-native-osx-arm64, voice-sherpa-native-osx-x64, voice-sherpa-native-win-x64 |

## Recommendations

### For the MCP server packaging (#323)

1. **Start with ref implementations only.** All 13 ref platforms are Ready or Likely ready (resource config). An MCP server with ref backends would compile to native image with minimal effort — just the resource config for YAML seed files.

2. **Microsoft providers are native-ready today.** `calendar-microsoft`, `email-microsoft`, `document-microsoft` all use `microsoft-graph-client` which is pure `java.net.http` + JSON-P. These can be included immediately.

3. **Google providers need the most work.** The Google API client libraries are the biggest native image blocker. Recommend replacing with raw HTTP + JSON-P (following the `microsoft-graph-client` pattern) as a separate effort. This also eliminates a heavy transitive dependency tree.

4. **Teams JWT validation needs migration.** Replace Nimbus JOSE+JWT with `quarkus-oidc` or `smallrye-jwt`. Discord's EdDSA usage likely works with a build-time init flag — test first.

5. **voice-sherpa is excluded by design.** Panama FFI is incompatible with native image. The voice SPI works; sherpa-onnx doesn't. Cloud voice providers would be the native-image path for voice.

6. **Phased rollout:**
   - Phase 1: Ref-only MCP server (native image, all 13 domains)
   - Phase 2: Add Microsoft providers (calendar, email, document)
   - Phase 3: Migrate Google providers to raw HTTP
   - Phase 4: Teams (after JWT migration), Discord (EdDSA flag)
