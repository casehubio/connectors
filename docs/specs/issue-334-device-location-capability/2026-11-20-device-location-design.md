# DeviceLocation Capability — Design Spec

**Issue:** casehubio/connectors#334
**Branch:** issue-334-device-location-capability
**Date:** 2026-11-20

## Summary

Add `DevicePosition` and `AssetTracking` capability sub-interfaces to `LocationPlatform` for retrieving the current position from device sensors (GPS, WiFi positioning) and asset trackers (AirTags, Tile, SmartThings). Implement the ref provider with fixed seed data. No Google provider — DeviceLocation is inherently client-side and no server-side Google API exists for it.

## Model Types

All in `io.casehub.connectors.location.model`.

### Position

```java
public record Position(Coordinates location, double accuracyMeters, Instant timestamp) {}
```

Richer than `Coordinates` — adds accuracy radius and when the fix was taken. These two fields are universal across every real device location source (W3C Geolocation, Google Geolocation API, Apple FindMy, Tile, SmartTag BLE). Altitude, speed, and heading are browser-only and omitted — they would be null for every tracker provider.

### BatteryStatus

```java
public enum BatteryStatus { FULL, GOOD, LOW, CRITICAL, UNKNOWN }
```

Normalised coarse battery level. Maps from real provider data:
- FindMy percentage → FULL (>75%), GOOD (25-75%), LOW (10-25%), CRITICAL (<10%)
- Tile 3-level → LEVEL2=GOOD, LEVEL1=LOW, NONE=UNKNOWN
- SmartTag BLE → mapped from battery byte

### BatteryInfo

```java
public record BatteryInfo(BatteryStatus status, Integer levelPercent) {}
```

`levelPercent` is nullable — populated by providers that report percentages (FindMy), null for coarse-only providers (Tile). `status` is always populated.

### TrackedDevice

```java
public record TrackedDevice(String id, String name, String type,
                            Position lastKnownPosition, BatteryInfo battery) {}
```

`type` is freeform string (`"airtag"`, `"tile"`, `"smarttag"`). No enum — real providers have unpredictable device type vocabularies.

## Capability Interfaces

Inner interfaces on `LocationPlatform`, following the established pattern.

### DevicePosition

```java
interface DevicePosition {
    Position currentPosition();
}
```

Returns the host device's current position. User-scoped via `LocationPlatform.devicePosition(String userId)`.

### AssetTracking

```java
interface AssetTracking {
    List<TrackedDevice> trackedDevices();
    Position devicePosition(String deviceId);
}
```

`trackedDevices()` lists all paired trackers for the user. `devicePosition(deviceId)` returns a specific tracker's last known position. User-scoped via `LocationPlatform.assetTracking(String userId)`.

## LocationPlatform Changes

```java
@SimulationEligible(name = "location-platform",
    capabilities = {"placeSearch", "placeDetails", "geocoding", "directions",
                    "devicePosition", "assetTracking"})
public interface LocationPlatform extends PlatformExtensible {

    // ... existing methods ...

    DevicePosition devicePosition(String userId);
    AssetTracking assetTracking(String userId);

    interface DevicePosition { ... }
    interface AssetTracking { ... }
}
```

## NoOpLocationPlatform Changes

Add two NoOp enum singletons following the established pattern:

```java
private enum NoOpDevicePosition implements DevicePosition {
    INSTANCE;
    @Override
    public Position currentPosition() {
        throw new UnsupportedCapabilityException("currentPosition", "DevicePosition", "none", List.of());
    }
}

private enum NoOpAssetTracking implements AssetTracking {
    INSTANCE;
    @Override
    public List<TrackedDevice> trackedDevices() {
        throw new UnsupportedCapabilityException("trackedDevices", "AssetTracking", "none", List.of());
    }
    @Override
    public Position devicePosition(String deviceId) {
        throw new UnsupportedCapabilityException("devicePosition", "AssetTracking", "none", List.of());
    }
}
```

Add accessor methods returning the singletons. `supports()` returns false for both.

## Ref Implementation

### RefLocationPlatform

- Add `DevicePosition.class` and `AssetTracking.class` to the `SUPPORTED` set
- Implement both capability interfaces as inner classes delegating to `LocationBackend`

### LocationBackend / InMemoryLocationBackend

Add methods:
- `Position currentPosition()` — returns fixed position at Balham station (51.4430, -0.1525, accuracy 10m)
- `List<TrackedDevice> trackedDevices()` — returns seed tracked devices
- `Position trackedDevicePosition(String deviceId)` — returns position for a specific device

### Seed Data

Three tracked devices near London:

| ID | Name | Type | Location | Battery |
|----|------|------|----------|---------|
| `tracker-1` | Keys | airtag | 51.4435, -0.1530 (near Balham) | GOOD, 72% |
| `tracker-2` | Backpack | tile | 51.5074, -0.1278 (central London) | LOW, null |
| `tracker-3` | Dog Collar | smarttag | 51.4520, -0.1680 (Wandsworth Common) | FULL, null |

All positions timestamped with a fixed recent instant for deterministic tests.

## MCP Layer (ConnectorLocationApi)

Three new endpoints:

| Method | Path | Capability | Operation |
|--------|------|------------|-----------|
| GET | `/device/position` | DevicePosition | `currentPosition()` |
| GET | `/device/tracked` | AssetTracking | `trackedDevices()` |
| GET | `/device/tracked/{deviceId}` | AssetTracking | `devicePosition(deviceId)` |

Update `requireCapability` helper to include `DevicePosition` and `AssetTracking` in the supported capabilities list for error reporting.

## Google Implementation

No changes. `GoogleLocationPlatform.supports()` already returns false for unknown capability classes. No server-side Google API exists for device position or asset tracking.

## Testing

### Unit Tests

- `LocationPlatformServiceTest` — verify new capabilities discoverable
- `RefLocationPlatformTest` — test both capabilities return expected seed data, test `supports()` returns true for both

### Seed Data Tests

- Verify seed loader populates tracked devices
- Verify `devicePosition("tracker-1")` returns expected coordinates
- Verify `trackedDevices()` returns all three devices

### NoOp Tests

- Verify NoOp throws `UnsupportedCapabilityException` for both capabilities

## Out of Scope

- Real providers (Browser Geolocation, CoreLocation, FindMy, Android) — all require client-side bridges
- Google Geolocation API integration — requires client to supply radio signal data
- Geofencing, location history, or movement tracking
- Real-time position streaming

## References

- `location-spi/src/main/java/.../spi/LocationPlatform.java` — existing SPI structure
- `location-ref/src/main/java/.../ref/RefLocationPlatform.java` — existing ref pattern
- `location-spi/src/main/java/.../spi/NoOpLocationPlatform.java` — NoOp pattern
- `graphql/src/main/java/.../graphql/ConnectorLocationApi.java` — MCP wiring pattern
- `connectors-api/src/main/java/.../Coordinates.java` — reused by Position
- W3C Geolocation API Recommendation (2026-03-24) — Position field research
- Google Geolocation API docs — accuracy field, no altitude
- FindMy.py — Apple tracker data model (lat, lng, accuracy, battery)
- pytile — Tile tracker data model (battery_status 3-level)
- SmartTag BLE advertisement format (UUID 0xFD5A) — battery level in BLE frame
