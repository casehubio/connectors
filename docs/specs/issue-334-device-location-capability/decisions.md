## D1: Capability integration pattern

**Choice:** Inner interfaces on LocationPlatform (Option A)
**Alternatives:**
- Separate top-level DevicePlatform SPI — massive overhead for two small interfaces, issue explicitly says "fits on LocationPlatform"
- Extension via PlatformExtensible.as() — breaks established capability pattern, no supports() integration, no SimulationEligible discovery
**Rationale:** Follows the exact established pattern used by PlaceSearch, PlaceDetails, Geocoding, Directions. Same supports() gating, same NoOp pattern, same MCP wiring, same SimulationEligible annotation. Zero architectural deviation.
**Trade-offs:** LocationPlatform interface grows by two accessor methods — acceptable given the domain cohesion.
**Sources:** LocationPlatform.java, RefLocationPlatform.java, NoOpLocationPlatform.java, ConnectorLocationApi.java
**Exploration:** quick
**Status:** captured

## D2: Split DevicePosition and AssetTracking

**Choice:** Two separate capability sub-interfaces (DevicePosition + AssetTracking)
**Alternatives:**
- Single DeviceLocation interface — forces providers to stub out tracker methods when they only do GPS, or vice versa
- Single interface with default-throwing methods — hides capability boundaries behind runtime exceptions
**Rationale:** GPS/geolocation providers don't need tracker methods; future FindMy/AirTag providers can support AssetTracking without host device position. Independent supports() checks.
**Trade-offs:** Two interfaces instead of one — slightly more SPI surface, but each is small (1-2 methods).
**Sources:** Issue #334 discussion, existing pattern where capabilities are independently gateable
**Exploration:** quick
**Status:** captured

## D3: Position model

**Choice:** Position(Coordinates location, double accuracyMeters, Instant timestamp)
**Alternatives:**
- W3C-aligned with nullable altitude/speed/heading — adds 4 fields that would be null for every real tracker provider
- Coordinates only — loses universally-available accuracy and timestamp data
**Rationale:** Research across FindMy, Tile, SmartTag, Google Geolocation, and W3C Geolocation shows lat/lng + accuracy + timestamp are universal. Altitude/speed/heading are browser-only and would always be null for tracker providers.
**Trade-offs:** If a browser-bridge provider is added later, it cannot report altitude/speed/heading through this model. Acceptable — that's a different integration pattern.
**Sources:** W3C Geolocation API spec (2026), Google Geolocation API docs, FindMy.py reverse-engineered data model, pytile Tile API, SmartTag BLE advertisement format
**Exploration:** deep-analysis (internet research)
**Status:** captured

## D4: Battery model

**Choice:** BatteryInfo(BatteryStatus status, Integer levelPercent) with coarse BatteryStatus enum { FULL, GOOD, LOW, CRITICAL, UNKNOWN }
**Alternatives:**
- Percentage only — many providers (Tile) only have coarse 3-level indicators, not percentages
- Freeform string — maximum flexibility, minimum type safety
- BatteryStatus enum only — loses percentage data from providers that have it (FindMy)
**Rationale:** Research shows battery reporting varies wildly: FindMy has percentages + charging status, Tile has 3-level (NONE/LEVEL1/LEVEL2), SmartTag has BLE battery level. Coarse enum normalises across all; optional percentage preserves precision when available.
**Trade-offs:** Enum values are an opinionated mapping — Tile's LEVEL1/LEVEL2/NONE must be interpreted into FULL/GOOD/LOW/CRITICAL/UNKNOWN.
**Sources:** pytile battery_status PR, FindMy.py location data model, SmartTag BLE advertisement format (UUID 0xFD5A)
**Exploration:** deep-analysis (internet research)
**Status:** captured

## D5: Model package location

**Choice:** io.casehub.connectors.location.model (same package as Place, Route, etc.)
**Alternatives:**
- io.casehub.connectors.location.model.device sub-package — separates device/tracker types from place/routing types but adds package depth
**Rationale:** Consistent with existing convention — all location model types in one flat package.
**Trade-offs:** Package grows by ~4 types (Position, TrackedDevice, BatteryInfo, BatteryStatus).
**Sources:** Existing model package structure
**Exploration:** quick
**Status:** captured

## D6: Scope

**Choice:** SPI + ref implementation only (no Google DeviceLocation provider)
**Alternatives:**
- Google Geolocation API — requires client-side radio signal data, not a true "get device position" API
- Placeholder Google impl with supports()=false — adds code with no functionality
**Rationale:** DeviceLocation is inherently client-side. Google's server-side Geolocation API requires WiFi/cell tower data to be passed in. No server-side "get the device's GPS position" API exists on Google.
**Trade-offs:** Google location-google module won't support DevicePosition or AssetTracking until a suitable API bridge exists.
**Sources:** Google Geolocation API docs, issue #334 implementation table analysis
**Exploration:** deep-analysis (internet research)
**Status:** captured
