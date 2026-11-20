package io.casehub.connectors.location.spi;

import io.casehub.connectors.UnsupportedCapabilityException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpLocationPlatformTest {

    private final NoOpLocationPlatform platform = new NoOpLocationPlatform();

    @Test
    void doesNotSupportDevicePosition() {
        assertThat(platform.supports(LocationPlatform.DevicePosition.class)).isFalse();
    }

    @Test
    void doesNotSupportAssetTracking() {
        assertThat(platform.supports(LocationPlatform.AssetTracking.class)).isFalse();
    }

    @Test
    void devicePositionThrows() {
        assertThatThrownBy(() -> platform.devicePosition("user1").currentPosition())
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void assetTrackingTrackedDevicesThrows() {
        assertThatThrownBy(() -> platform.assetTracking("user1").trackedDevices())
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void assetTrackingDevicePositionThrows() {
        assertThatThrownBy(() -> platform.assetTracking("user1").devicePosition("tracker-1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }
}
